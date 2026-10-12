"""Client LLM commun : SEUL point d'appel à un LLM dans tout le projet.

Règles :
- Les modules n'importent jamais un SDK LLM directement : ils dépendent de `LLMClient`
  via `Depends(get_llm_client)`.
- Tout texte venant d'un utilisateur passe par `app.core.sanitizer` AVANT d'arriver ici.
- Aucune donnée personnelle (nom, email, identifiant) dans les messages.
- La clé API ne sort jamais de ce fichier : ni dans les logs, ni dans les messages d'erreur.

Implémentations : `StubLLMClient` (aucun appel réseau) et `GeminiLLMClient` (LLM_PROVIDER=gemini).
"""

import asyncio
import io
import logging
import re
import time
import wave
from abc import ABC, abstractmethod
from typing import Any, Literal, TypeVar

from pydantic import BaseModel, ValidationError

from app.core.config import Settings, get_settings
from app.core.errors import (
    LLMError,
    LLMInvalidResponseError,
    LLMMissingKeyError,
    LLMQuotaError,
    LLMTimeoutError,
)

T = TypeVar("T", bound=BaseModel)

log = logging.getLogger("bawsla.llm")

IMAGE_MIME_TYPES = ("image/png", "image/jpeg", "image/webp")


class RequeteRefuseeError(LLMError):
    """Le fournisseur a refusé la requête (400) : paramètre non pris en charge, par exemple. Même code HTTP que LLMError."""


class LLMMessage(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str


class LLMResult(BaseModel):
    text: str
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None
    stub: bool = False


class Replique(BaseModel):
    """Une réplique d'un dialogue à mettre en voix."""

    locuteur: str
    texte: str


class AudioResult(BaseModel):
    """Son produit par un modèle de synthèse vocale (WAV pour Gemini)."""

    data: bytes
    mime_type: str
    model: str
    stub: bool = False


class LLMClient(ABC):
    """Interface commune. Toutes les implémentations lèvent UNIQUEMENT des `LLMError`
    (LLMTimeoutError, LLMQuotaError, LLMMissingKeyError, LLMInvalidResponseError, ou LLMError)."""

    @abstractmethod
    async def complete(
        self,
        messages: list[LLMMessage],
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        """Renvoie la réponse texte du modèle.

        `module` ("user" | "cours" | "quiz" | "forum") sert au suivi d'usage et aux logs.
        """

    @abstractmethod
    async def complete_with_image(
        self,
        messages: list[LLMMessage],
        image: bytes,
        mime_type: str,
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        """Comme `complete`, avec une image jointe (PNG, JPEG ou WebP) au dernier message utilisateur."""

    @abstractmethod
    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        """Met `texte` en voix (`voix` : nom de la voix du fournisseur, `langue` : "fr" ou "en")."""

    @abstractmethod
    async def synthetiser_dialogue(
        self, repliques: list[Replique], voix_par_locuteur: dict[str, str], langue: str
    ) -> AudioResult:
        """Met en voix un dialogue de deux locuteurs au plus, en UN seul appel (une voix par locuteur)."""

    async def complete_json(
        self,
        messages: list[LLMMessage],
        schema: type[T],
        *,
        module: str,
        temperature: float = 0.0,
        max_tokens: int = 2048,
    ) -> T:
        """Demande une réponse JSON et la valide avec `schema` (pydantic).

        Lève `LLMInvalidResponseError` si la réponse n'est pas conforme.
        """
        result = await self.complete(messages, module=module, temperature=temperature, max_tokens=max_tokens)
        try:
            return schema.model_validate_json(_strip_code_fence(result.text))
        except ValidationError as exc:
            raise LLMInvalidResponseError("Réponse du LLM non conforme au schéma attendu") from exc


class StubLLMClient(LLMClient):
    """Implémentation factice : aucun appel réseau, réponse clairement marquée [STUB]."""

    async def complete(
        self,
        messages: list[LLMMessage],
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        return LLMResult(
            text=f"[STUB] Réponse factice du LLM pour le module '{module}' ({len(messages)} message(s) reçu(s)).",
            model="stub",
            stub=True,
        )

    async def complete_with_image(
        self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024
    ) -> LLMResult:
        _verifier_image(image, mime_type)
        return LLMResult(
            text=f"[STUB] Réponse factice du LLM pour le module '{module}' (image de {len(image)} octets).",
            model="stub",
            stub=True,
        )

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : complete_json indisponible")

    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : synthèse vocale indisponible")

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue) -> AudioResult:
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : synthèse vocale indisponible")


class GeminiLLMClient(LLMClient):
    """Google Gemini via le SDK `google-genai` (appels asynchrones : `client.aio.models.generate_content`).

    - Le modèle vient de `LLM_MODEL`, la réflexion est plafonnée par `LLM_THINKING_BUDGET`.
    - `complete()` renvoie seulement le texte ; `complete_json()` force `application/json`, valide avec pydantic
      et relance UNE fois si le JSON est invalide.
    - Les surcharges passagères du fournisseur (5xx) sont retentées deux fois avec une courte attente.
    - Toutes les erreurs du SDK sont converties en `LLMError` ; ni la clé ni le prompt n'apparaissent dans les logs.
    """

    MAX_TENTATIVES = 3

    def __init__(
        self,
        api_key: str,
        model: str,
        *,
        timeout_seconds: float = 30.0,
        thinking_budget: int | None = 128,
        tts_model: str | None = None,
        client: Any | None = None,
        attente_entre_tentatives: float = 1.0,
    ) -> None:
        self._model = model
        self._tts_model = tts_model
        self._langue_acceptee = True  # passe à False si le modèle de synthèse refuse language_code
        self._timeout = timeout_seconds
        self._thinking_budget = thinking_budget
        self._attente = attente_entre_tentatives
        if client is not None:  # injecté par les tests (SDK simulé)
            self._client = client
        else:
            from google import genai
            from google.genai import types

            self._client = genai.Client(api_key=api_key, http_options=types.HttpOptions(timeout=int(timeout_seconds * 1000)))

    # ------------------------------------------------------------------ API publique

    async def complete(
        self, messages: list[LLMMessage], *, module: str, temperature: float = 0.2, max_tokens: int = 1024
    ) -> LLMResult:
        return await self._generer(messages, module, temperature, max_tokens, json=False)

    async def complete_with_image(
        self,
        messages: list[LLMMessage],
        image: bytes,
        mime_type: str,
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        _verifier_image(image, mime_type)
        return await self._generer(messages, module, temperature, max_tokens, json=False, image=(image, mime_type))

    async def complete_json(
        self,
        messages: list[LLMMessage],
        schema: type[T],
        *,
        module: str,
        temperature: float = 0.0,
        max_tokens: int = 2048,
    ) -> T:
        conversation = list(messages)
        for tentative in (1, 2):
            result = await self._generer(conversation, module, temperature, max_tokens, json=True)
            try:
                return schema.model_validate_json(_strip_code_fence(result.text))
            except ValidationError as exc:
                log.warning("Gemini: JSON invalide (module=%s, tentative=%d/2)", module, tentative)
                if tentative == 2:
                    raise LLMInvalidResponseError("Réponse du LLM non conforme au schéma attendu") from exc
                conversation = [
                    *messages,
                    LLMMessage(role="assistant", content=result.text),
                    LLMMessage(
                        role="user",
                        content="Ta réponse précédente n'est pas un JSON valide pour le format demandé. "
                        "Renvoie UNIQUEMENT le JSON corrigé, sans texte autour.",
                    ),
                ]
        raise LLMInvalidResponseError("Réponse du LLM non conforme au schéma attendu")  # pragma: no cover

    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        from google.genai import types

        if not texte.strip():
            raise LLMInvalidResponseError("Aucun texte à mettre en voix")
        voice = types.VoiceConfig(prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=voix))
        return await self._synthese(texte, lambda code: types.SpeechConfig(voice_config=voice, language_code=code), langue)

    async def synthetiser_dialogue(
        self, repliques: list[Replique], voix_par_locuteur: dict[str, str], langue: str
    ) -> AudioResult:
        from google.genai import types

        repliques = [r for r in repliques if r.texte.strip()]
        locuteurs = list(dict.fromkeys(r.locuteur for r in repliques))
        if not locuteurs:
            raise LLMInvalidResponseError("Aucun texte à mettre en voix")
        if len(locuteurs) > 2:
            raise LLMInvalidResponseError("La synthèse d'un dialogue accepte 2 locuteurs au plus")
        manquantes = [nom for nom in locuteurs if nom not in voix_par_locuteur]
        if manquantes:
            raise LLMInvalidResponseError(f"Voix manquante pour le locuteur : {', '.join(manquantes)}")
        if len(locuteurs) == 1:  # un seul locuteur : synthèse simple
            return await self.synthetiser(" ".join(r.texte for r in repliques), voix_par_locuteur[locuteurs[0]], langue)

        voix = [
            types.SpeakerVoiceConfig(
                speaker=nom,
                voice_config=types.VoiceConfig(prebuilt_voice_config=types.PrebuiltVoiceConfig(voice_name=voix_par_locuteur[nom])),
            )
            for nom in locuteurs
        ]
        texte = "\n".join(f"{r.locuteur}: {r.texte.strip()}" for r in repliques)
        multi = types.MultiSpeakerVoiceConfig(speaker_voice_configs=voix)
        return await self._synthese(texte, lambda code: types.SpeechConfig(multi_speaker_voice_config=multi, language_code=code), langue)

    # ------------------------------------------------------------------ interne

    async def _synthese(self, texte: str, speech, langue: str) -> AudioResult:
        """Un appel au modèle de synthèse. `speech(code)` construit la configuration de voix pour un code de langue."""
        from google.genai import types

        if not self._tts_model:
            raise LLMMissingKeyError("LLM_TTS_MODEL absent : aucun modèle de synthèse vocale configuré")
        debut = time.monotonic()
        code = _CODES_LANGUE.get(langue.lower()) if self._langue_acceptee else None
        contenu = [types.Content(role="user", parts=[types.Part(text=texte)])]
        try:
            reponse = await self._appeler(contenu, _config_audio(types, speech(code)), "tts", modele=self._tts_model)
        except LLMError as exc:
            if code is None or not isinstance(exc, RequeteRefuseeError):
                raise
            # Requête refusée : le modèle n'accepte peut-être pas language_code. On réessaie sans, et on s'en souvient.
            log.warning("Gemini TTS: language_code refusé, nouvel essai sans ce paramètre")
            self._langue_acceptee = False
            reponse = await self._appeler(contenu, _config_audio(types, speech(None)), "tts", modele=self._tts_model)
        donnees, mime = _audio_de(reponse)
        wav = _vers_wav(donnees, mime)
        log.info("Gemini TTS: modele=%s duree=%.1fs octets=%d", self._tts_model, time.monotonic() - debut, len(wav))
        return AudioResult(data=wav, mime_type="audio/wav", model=self._tts_model)

    async def _generer(
        self,
        messages: list[LLMMessage],
        module: str,
        temperature: float,
        max_tokens: int,
        *,
        json: bool,
        image: tuple[bytes, str] | None = None,
    ) -> LLMResult:
        from google.genai import types

        systeme = "\n\n".join(m.content for m in messages if m.role == "system") or None
        contents = _contenus(messages, image)
        if not contents:
            raise LLMInvalidResponseError("Aucun message utilisateur à envoyer au LLM")
        thinking = types.ThinkingConfig(thinking_budget=self._thinking_budget) if self._thinking_budget is not None else None
        # Les jetons de réflexion comptent dans la limite de sortie : on les ajoute pour ne pas tronquer la réponse.
        limite = max_tokens + (self._thinking_budget or 0)
        config = types.GenerateContentConfig(
            system_instruction=systeme,
            temperature=temperature,
            max_output_tokens=limite,
            response_mime_type="application/json" if json else None,
            thinking_config=thinking,
        )

        debut = time.monotonic()
        reponse = await self._appeler(contents, config, module)
        texte = _texte(reponse)
        if not texte:
            raise LLMInvalidResponseError("Réponse vide ou bloquée par le fournisseur")
        usage = getattr(reponse, "usage_metadata", None)
        entree = getattr(usage, "prompt_token_count", None)
        sortie = getattr(usage, "candidates_token_count", None)
        log.info(
            "Gemini: module=%s modele=%s duree=%.1fs jetons_entree=%s jetons_sortie=%s",
            module, self._model, time.monotonic() - debut, entree, sortie,
        )
        return LLMResult(text=texte, model=self._model, input_tokens=entree, output_tokens=sortie)

    async def _appeler(self, contents: list, config: Any, module: str, modele: str | None = None) -> Any:
        """Un appel au SDK, avec délai maximal et nouvelles tentatives sur les erreurs passagères (5xx)."""
        for tentative in range(1, self.MAX_TENTATIVES + 1):
            try:
                return await asyncio.wait_for(
                    self._client.aio.models.generate_content(model=modele or self._model, contents=contents, config=config),
                    timeout=self._timeout,
                )
            except LLMError:
                raise
            except Exception as exc:  # noqa: BLE001 - tout est converti en LLMError ci-dessous
                erreur = self._convertir(exc, module)
                passagere = getattr(exc, "code", None) in (500, 502, 503, 504) and not isinstance(erreur, LLMTimeoutError)
                if passagere and tentative < self.MAX_TENTATIVES:
                    log.warning("Gemini: erreur passagère %s (module=%s), nouvelle tentative %d", getattr(exc, "code", "?"), module, tentative)
                    await asyncio.sleep(self._attente * tentative)
                    continue
                raise erreur from None
        raise LLMError("Fournisseur LLM indisponible")  # pragma: no cover

    def _convertir(self, exc: Exception, module: str) -> LLMError:
        """Traduit une exception du SDK en LLMError. On ne recopie jamais son message (il pourrait contenir du contexte)."""
        if isinstance(exc, (asyncio.TimeoutError, TimeoutError)) or type(exc).__name__ in ("ReadTimeout", "ConnectTimeout", "TimeoutException"):
            log.warning("Gemini: délai dépassé (module=%s)", module)
            return LLMTimeoutError("Le fournisseur LLM n'a pas répondu à temps")
        code = getattr(exc, "code", None)
        statut = getattr(exc, "status", None)
        log.warning("Gemini: erreur du fournisseur (module=%s, code=%s, statut=%s)", module, code, statut)
        if code == 429:
            return LLMQuotaError("Quota du fournisseur LLM atteint, réessaie plus tard")
        if code in (401, 403):
            return LLMMissingKeyError("Clé API refusée par le fournisseur LLM")
        if code == 404:
            return LLMMissingKeyError("Modèle LLM introuvable : vérifie LLM_MODEL")
        if code == 400 and statut == "INVALID_ARGUMENT" and "API key" in str(getattr(exc, "message", "")):
            return LLMMissingKeyError("Clé API invalide")
        if code in (500, 502, 503, 504):
            return LLMError("Le fournisseur LLM est surchargé ou indisponible, réessaie dans un instant")
        return RequeteRefuseeError("Requête refusée par le fournisseur LLM")


_CODES_LANGUE = {"fr": "fr-FR", "en": "en-US"}
_FREQUENCE = re.compile(r"rate=(\d+)", re.IGNORECASE)


def _config_audio(types, speech):
    return types.GenerateContentConfig(response_modalities=["AUDIO"], speech_config=speech)


def _audio_de(reponse: Any) -> tuple[bytes, str]:
    """Octets et type MIME du premier élément audio de la réponse."""
    try:
        for candidat in reponse.candidates or []:
            for partie in candidat.content.parts or []:
                donnees = partie.inline_data
                if donnees is not None and donnees.data:
                    return donnees.data, donnees.mime_type or ""
    except AttributeError:
        pass
    raise LLMInvalidResponseError("Le fournisseur n'a renvoyé aucun son")


def _vers_wav(donnees: bytes, mime: str) -> bytes:
    """Gemini renvoie du PCM 16 bits mono brut (audio/L16;rate=24000) : on l'emballe dans un en-tête WAV."""
    if mime.lower().startswith(("audio/wav", "audio/x-wav", "audio/wave")):
        return donnees
    trouve = _FREQUENCE.search(mime)
    tampon = io.BytesIO()
    with wave.open(tampon, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(int(trouve.group(1)) if trouve else 24000)
        wav.writeframes(donnees)
    return tampon.getvalue()


def _contenus(messages: list[LLMMessage], image: tuple[bytes, str] | None) -> list:
    """Messages (hors consignes système) au format du SDK ; l'image est jointe au dernier message utilisateur."""
    from google.genai import types

    dialogue = [m for m in messages if m.role != "system"]
    dernier_user = max((i for i, m in enumerate(dialogue) if m.role == "user"), default=None)
    contenus = []
    for i, m in enumerate(dialogue):
        parts = [types.Part(text=m.content)]
        if image is not None and i == dernier_user:
            parts.append(types.Part.from_bytes(data=image[0], mime_type=image[1]))
        contenus.append(types.Content(role="user" if m.role == "user" else "model", parts=parts))
    return contenus


def _texte(reponse: Any) -> str:
    try:
        return (reponse.text or "").strip()
    except Exception:  # noqa: BLE001 - le SDK lève si la réponse n'a pas de texte (bloquée, vide)
        return ""


def _verifier_image(image: bytes, mime_type: str) -> None:
    if mime_type not in IMAGE_MIME_TYPES:
        raise LLMInvalidResponseError(f"Type d'image non pris en charge : {', '.join(IMAGE_MIME_TYPES)}")
    if not image:
        raise LLMInvalidResponseError("Image vide")


def _strip_code_fence(text: str) -> str:
    """Retire un éventuel bloc ```json ... ``` autour de la réponse."""
    t = text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else ""
        t = t.rsplit("```", 1)[0]
    return t.strip()


def build_llm_client(settings: Settings) -> LLMClient:
    if settings.llm_provider == "stub":
        return StubLLMClient()
    if settings.llm_api_key is None or not settings.llm_api_key.get_secret_value():
        raise LLMMissingKeyError(f"LLM_API_KEY absente pour le fournisseur '{settings.llm_provider}'")
    if settings.llm_provider == "gemini":
        if not settings.llm_model:
            raise LLMMissingKeyError("LLM_MODEL absent pour le fournisseur 'gemini'")
        return GeminiLLMClient(
            settings.llm_api_key.get_secret_value(),
            settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            thinking_budget=settings.llm_thinking_budget,
            tts_model=settings.llm_tts_model,
        )
    raise LLMMissingKeyError(f"Fournisseur LLM '{settings.llm_provider}' non implémenté")


_clients: dict[tuple[str, str | None], LLMClient] = {}


def get_llm_client() -> LLMClient:
    """Dépendance FastAPI : `llm: LLMClient = Depends(get_llm_client)`. Un client par fournisseur et modèle."""
    settings = get_settings()
    cle = (settings.llm_provider, settings.llm_model)
    if cle not in _clients:
        _clients[cle] = build_llm_client(settings)
    return _clients[cle]
