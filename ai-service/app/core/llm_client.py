"""Client LLM commun : SEUL point d'appel à un LLM dans tout le projet.

Règles :
- Les modules n'importent jamais un SDK LLM directement : ils dépendent de `LLMClient`
  via `Depends(get_llm_client)`.
- Tout texte venant d'un utilisateur passe par `app.core.sanitizer` AVANT d'arriver ici.
- Aucune donnée personnelle (nom, email, identifiant) dans les messages.
- La clé API ne sort jamais de ce fichier : ni dans les logs, ni dans les messages d'erreur.

Implémentations : `StubLLMClient` (aucun appel réseau), `GeminiLLMClient` (LLM_PROVIDER=gemini), `OpenAILLMClient`
(secours, LLM_SECOURS=openai ou groq : Groq est compatible OpenAI, même client avec une autre base_url) et `LLMRoutage`
(Gemini d'abord, le secours sur quota ou indisponibilité).
"""

import asyncio
import base64
import hashlib
import io
import logging
import math
import re
import time
import wave
from abc import ABC, abstractmethod
from contextvars import ContextVar
from typing import Any, Literal, TypeVar

from pydantic import BaseModel, ValidationError

from app.core.config import Settings, get_settings
from app.core.errors import (
    LLMError,
    LLMInvalidResponseError,
    LLMMissingKeyError,
    LLMQuotaError,
    LLMTimeoutError,
    LLMTropLongError,
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
    # Fournisseur qui a réellement répondu : "gemini", "openai" ou "groq" (secours), ou "stub".
    fournisseur: str | None = None


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


TacheEmbedding = Literal["DOCUMENT", "REQUETE"]


class EmbeddingResult(BaseModel):
    """Vecteurs (un par texte, dans l'ordre, normalisés : norme 1) et le modèle qui les a produits.

    On ne compare jamais deux vecteurs de modèles différents : l'appelant garde `modele` avec chaque vecteur.
    """

    vecteurs: list[list[float]]
    modele: str
    dimension: int
    fournisseur: str | None = None
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

    async def embed(self, textes: list[str], *, module: str, tache: TacheEmbedding = "DOCUMENT") -> EmbeddingResult:
        """Vectorise des textes (DOCUMENT pour ce qu'on indexe, REQUETE pour ce qu'on cherche).

        Par défaut indisponible : seuls les fournisseurs qui ont un modèle d'embeddings le redéfinissent.
        """
        raise LLMMissingKeyError("Vectorisation indisponible pour ce fournisseur")


class StubLLMClient(LLMClient):
    nom = "stub"
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
            fournisseur="stub",
        )

    async def complete_with_image(
        self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024
    ) -> LLMResult:
        _verifier_image(image, mime_type)
        return LLMResult(
            text=f"[STUB] Réponse factice du LLM pour le module '{module}' (image de {len(image)} octets).",
            model="stub",
            stub=True,
            fournisseur="stub",
        )

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : complete_json indisponible")

    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : synthèse vocale indisponible")

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue) -> AudioResult:
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : synthèse vocale indisponible")

    async def embed(self, textes: list[str], *, module: str, tache: TacheEmbedding = "DOCUMENT") -> EmbeddingResult:
        """Vecteurs DÉTERMINISTES sans réseau (hachage des mots et des trigrammes) : utiles aux tests et au dev.

        Deux textes qui partagent des mots ont des vecteurs proches ; ce n'est pas une vraie similarité de sens.
        """
        return EmbeddingResult(
            vecteurs=[_vecteur_hache(t, DIMENSION_STUB) for t in textes],
            modele=MODELE_STUB,
            dimension=DIMENSION_STUB,
            fournisseur="stub",
            stub=True,
        )


MODELE_STUB = "stub-hachage-768"
DIMENSION_STUB = 768


class GeminiLLMClient(LLMClient):
    """Google Gemini via le SDK `google-genai` (appels asynchrones : `client.aio.models.generate_content`).

    - Le modèle vient de `LLM_MODEL`, la réflexion est plafonnée par `LLM_THINKING_BUDGET`.
    - `complete()` renvoie seulement le texte ; `complete_json()` force `application/json`, valide avec pydantic
      et relance UNE fois si le JSON est invalide.
    - Les surcharges passagères du fournisseur (5xx) sont retentées deux fois avec une courte attente.
    - Toutes les erreurs du SDK sont converties en `LLMError` ; ni la clé ni le prompt n'apparaissent dans les logs.
    """

    nom = "gemini"
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
        embedding_model: str | None = "gemini-embedding-001",
        embedding_dimension: int = 768,
    ) -> None:
        self._model = model
        self._embedding_model = embedding_model
        self._embedding_dimension = embedding_dimension
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

    async def embed(self, textes: list[str], *, module: str, tache: TacheEmbedding = "DOCUMENT") -> EmbeddingResult:
        """`embed_content` par lots de 100. Chaque texte est un `Content` séparé (sinon gemini-embedding-2 les
        fusionnerait en un seul vecteur) ; task_type RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY ; vecteurs normalisés
        (gemini-embedding-001 ne l'est pas sous 3072 dimensions)."""
        from google.genai import types

        if not self._embedding_model:
            raise LLMMissingKeyError("LLM_EMBEDDING_MODEL absent : aucun modèle de vectorisation configuré")
        if not textes:
            return EmbeddingResult(vecteurs=[], modele=self._embedding_model, dimension=self._embedding_dimension,
                                   fournisseur=self.nom)
        config = types.EmbedContentConfig(
            task_type="RETRIEVAL_QUERY" if tache == "REQUETE" else "RETRIEVAL_DOCUMENT",
            output_dimensionality=self._embedding_dimension,
        )
        debut = time.monotonic()
        vecteurs: list[list[float]] = []
        for lot in _lots(textes, TAILLE_LOT_EMBEDDING):
            contenus = [types.Content(parts=[types.Part(text=t)]) for t in lot]
            reponse = await self._appeler_embed(contenus, config, module)
            valeurs = [list(e.values or []) for e in (getattr(reponse, "embeddings", None) or [])]
            if len(valeurs) != len(lot) or any(not v for v in valeurs):
                raise LLMInvalidResponseError("Le fournisseur n'a pas renvoyé un vecteur par texte")
            vecteurs.extend(_normaliser(v) for v in valeurs)
        log.info("Gemini embed: module=%s modele=%s textes=%d duree=%.1fs", module, self._embedding_model,
                 len(textes), time.monotonic() - debut)
        return EmbeddingResult(vecteurs=vecteurs, modele=self._embedding_model, dimension=len(vecteurs[0]),
                               fournisseur=self.nom)

    # ------------------------------------------------------------------ interne

    async def _appeler_embed(self, contenus: list, config: Any, module: str) -> Any:
        """Un appel de vectorisation, avec les mêmes délais, relances et conversions d'erreurs que la génération."""
        for tentative in range(1, self.MAX_TENTATIVES + 1):
            try:
                return await asyncio.wait_for(
                    self._client.aio.models.embed_content(model=self._embedding_model, contents=contenus,
                                                          config=config),
                    timeout=self._timeout,
                )
            except LLMError:
                raise
            except Exception as exc:  # noqa: BLE001 - tout est converti en LLMError ci-dessous
                erreur = self._convertir(exc, module)
                passagere = getattr(exc, "code", None) in (500, 502, 503, 504)
                if passagere and tentative < self.MAX_TENTATIVES:
                    await asyncio.sleep(self._attente * tentative)
                    continue
                raise erreur from None
        raise LLMError("Fournisseur LLM indisponible")  # pragma: no cover

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
        return LLMResult(text=texte, model=self._model, input_tokens=entree, output_tokens=sortie, fournisseur=self.nom)

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


class OpenAILLMClient(LLMClient):
    """OpenAI via le SDK officiel `openai` (Chat Completions asynchrone). Fournisseur de SECOURS de Gemini.

    Groq expose une API compatible OpenAI : le MÊME client sert pour Groq, avec `base_url`, `nom="groq"` et,
    si le fournisseur limite les jetons par minute, `limite_tokens_minute` (une requête plus grosse n'est pas envoyée).

    - Le modèle vient de `OPENAI_MODEL` ; `OPENAI_REASONING_EFFORT` plafonne la réflexion (comme LLM_THINKING_BUDGET).
    - `complete_json()` active le mode JSON, valide avec pydantic et relance UNE fois si le JSON est invalide.
    - Mêmes erreurs que Gemini : tout est converti en `LLMError` (jamais la clé ni le prompt dans les logs).
    - Un paramètre refusé par le modèle (temperature, reasoning_effort) est retiré une fois, puis on s'en souvient.
    """

    nom = "openai"
    MARGE_REFLEXION = 1500  # les jetons de réflexion comptent dans la limite de sortie

    def __init__(
        self,
        api_key: str,
        model: str,
        *,
        timeout_seconds: float = 30.0,
        reasoning_effort: str | None = "low",
        tts_model: str = "gpt-4o-mini-tts",
        client: Any | None = None,
        nom: str = "openai",
        base_url: str | None = None,
        max_retries: int = 1,
        limite_tokens_minute: int | None = None,
        marge_reflexion: int | None = None,
        embedding_model: str | None = None,
        embedding_dimension: int = 768,
    ) -> None:
        self.nom = nom
        self._embedding_model = embedding_model
        self._embedding_dimension = embedding_dimension
        self._marge = self.MARGE_REFLEXION if marge_reflexion is None else marge_reflexion
        self._limite_tpm = limite_tokens_minute
        self._model = model
        self._tts_model = tts_model
        self._timeout = timeout_seconds
        self._effort = reasoning_effort or None
        self._temperature_acceptee = True
        if client is not None:  # injecté par les tests (SDK simulé)
            self._client = client
        else:
            from openai import AsyncOpenAI

            self._client = AsyncOpenAI(api_key=api_key, timeout=timeout_seconds, max_retries=max_retries, base_url=base_url)

    # ------------------------------------------------------------------ API publique

    async def complete(
        self, messages: list[LLMMessage], *, module: str, temperature: float = 0.2, max_tokens: int = 1024
    ) -> LLMResult:
        return await self._generer(messages, module, temperature, max_tokens, json=False)

    async def complete_with_image(
        self, messages, image: bytes, mime_type: str, *, module: str, temperature: float = 0.2, max_tokens: int = 1024
    ) -> LLMResult:
        _verifier_image(image, mime_type)
        return await self._generer(messages, module, temperature, max_tokens, json=False, image=(image, mime_type))

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        conversation = list(messages)
        for tentative in (1, 2):
            result = await self._generer(conversation, module, temperature, max_tokens, json=True)
            try:
                return schema.model_validate_json(_strip_code_fence(result.text))
            except ValidationError as exc:
                log.warning("%s: JSON invalide (module=%s, tentative=%d/2)", self.nom, module, tentative)
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

    async def synthetiser(self, texte: str, voix: str, langue: str, *, format_audio: str = "mp3") -> AudioResult:
        """Synthèse vocale (`gpt-4o-mini-tts`). MP3 par défaut ; `langue` n'est pas nécessaire (le modèle la détecte)."""
        if not texte.strip():
            raise LLMInvalidResponseError("Aucun texte à mettre en voix")
        debut = time.monotonic()
        try:
            reponse = await asyncio.wait_for(
                self._client.audio.speech.create(model=self._tts_model, voice=voix, input=texte, response_format=format_audio),
                timeout=self._timeout,
            )
            donnees = await _octets(reponse)
        except LLMError:
            raise
        except Exception as exc:  # noqa: BLE001
            raise _convertir_openai(exc, "tts", self.nom) from None
        if not donnees:
            raise LLMInvalidResponseError("Réponse vide du fournisseur de synthèse vocale")
        log.info("%s TTS: modele=%s duree=%.1fs octets=%d", self.nom, self._tts_model, time.monotonic() - debut, len(donnees))
        mime = "audio/wav" if format_audio == "wav" else "audio/mpeg"
        return AudioResult(data=donnees, mime_type=mime, model=self._tts_model)

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue) -> AudioResult:
        """OpenAI n'a pas de synthèse multi-locuteurs : une requête par réplique (une voix chacune), WAV assemblés."""
        repliques = [r for r in repliques if r.texte.strip()]
        locuteurs = list(dict.fromkeys(r.locuteur for r in repliques))
        if not locuteurs:
            raise LLMInvalidResponseError("Aucun texte à mettre en voix")
        if len(locuteurs) > 2:
            raise LLMInvalidResponseError("La synthèse d'un dialogue accepte 2 locuteurs au plus")
        manquantes = [nom for nom in locuteurs if nom not in voix_par_locuteur]
        if manquantes:
            raise LLMInvalidResponseError(f"Voix manquante pour le locuteur : {', '.join(manquantes)}")
        morceaux = [
            (await self.synthetiser(r.texte, voix_par_locuteur[r.locuteur], langue, format_audio="wav")).data for r in repliques
        ]
        return AudioResult(data=_assembler_wav(morceaux), mime_type="audio/wav", model=self._tts_model)

    async def embed(self, textes: list[str], *, module: str, tache: TacheEmbedding = "DOCUMENT") -> EmbeddingResult:
        """`embeddings.create` (text-embedding-3-small, `dimensions`), par lots. Indisponible pour Groq."""
        if not self._embedding_model:
            raise LLMMissingKeyError(f"Vectorisation indisponible chez {self.nom}")
        if not textes:
            return EmbeddingResult(vecteurs=[], modele=self._embedding_model, dimension=self._embedding_dimension,
                                   fournisseur=self.nom)
        vecteurs: list[list[float]] = []
        for lot in _lots(textes, TAILLE_LOT_EMBEDDING):
            try:
                reponse = await asyncio.wait_for(
                    self._client.embeddings.create(model=self._embedding_model, input=lot,
                                                   dimensions=self._embedding_dimension),
                    timeout=self._timeout,
                )
            except LLMError:
                raise
            except Exception as exc:  # noqa: BLE001
                raise _convertir_openai(exc, module, self.nom) from None
            donnees = sorted(getattr(reponse, "data", None) or [], key=lambda d: getattr(d, "index", 0))
            if len(donnees) != len(lot):
                raise LLMInvalidResponseError("Le fournisseur n'a pas renvoyé un vecteur par texte")
            vecteurs.extend(_normaliser(list(d.embedding)) for d in donnees)
        return EmbeddingResult(vecteurs=vecteurs, modele=self._embedding_model, dimension=len(vecteurs[0]),
                               fournisseur=self.nom)

    # ------------------------------------------------------------------ interne

    async def _generer(
        self, messages: list[LLMMessage], module: str, temperature: float, max_tokens: int, *, json: bool,
        image: tuple[bytes, str] | None = None,
    ) -> LLMResult:
        envoyes = _messages_openai(messages, image)
        if not any(m["role"] == "user" for m in envoyes):
            raise LLMInvalidResponseError("Aucun message utilisateur à envoyer au LLM")
        parametres: dict[str, Any] = {
            "model": self._model,
            "messages": envoyes,
            "max_completion_tokens": self.reserve_sortie(max_tokens),
        }
        if json:
            parametres["response_format"] = {"type": "json_object"}
            _exiger_mot_json(envoyes)  # le mode JSON de Groq/OpenAI exige le mot « JSON » dans les messages
        self._verifier_taille(envoyes, parametres["max_completion_tokens"])
        if self._effort:
            parametres["reasoning_effort"] = self._effort
        if self._temperature_acceptee:
            parametres["temperature"] = temperature

        debut = time.monotonic()
        try:
            reponse = await self._appeler(parametres, module)
        except RequeteRefuseeError:
            # Le modèle refuse peut-être temperature ou reasoning_effort : on réessaie sans, une seule fois.
            if "temperature" not in parametres and "reasoning_effort" not in parametres:
                raise
            log.warning("%s: requête refusée, nouvel essai sans temperature ni reasoning_effort (module=%s)", self.nom, module)
            self._temperature_acceptee = False
            self._effort = None
            parametres.pop("temperature", None)
            parametres.pop("reasoning_effort", None)
            parametres["max_completion_tokens"] = max_tokens
            reponse = await self._appeler(parametres, module)
        texte = _texte_openai(reponse)
        if not texte:
            raise LLMInvalidResponseError("Réponse vide ou bloquée par le fournisseur")
        usage = getattr(reponse, "usage", None)
        entree, sortie = getattr(usage, "prompt_tokens", None), getattr(usage, "completion_tokens", None)
        log.info(
            "%s: module=%s modele=%s duree=%.1fs jetons_entree=%s jetons_sortie=%s",
            self.nom, module, self._model, time.monotonic() - debut, entree, sortie,
        )
        return LLMResult(text=texte, model=self._model, input_tokens=entree, output_tokens=sortie, fournisseur=self.nom)

    @property
    def limite_tokens_minute(self) -> int | None:
        """Jetons par minute que le fournisseur accepte (None : pas de limite connue)."""
        return self._limite_tpm

    def reserve_sortie(self, max_tokens: int) -> int:
        """Jetons de sortie réellement demandés au fournisseur : la sortie voulue plus la marge de réflexion."""
        return max_tokens + (self._marge if self._effort else 0)

    def _verifier_taille(self, envoyes: list[dict[str, Any]], tokens_sortie: int) -> None:
        """Refuse d'envoyer une requête plus grosse que la limite de jetons par minute du fournisseur (aucun appel, aucune relance)."""
        if not self._limite_tpm:
            return
        prompt = _estimer_tokens(envoyes)
        estimes = prompt + tokens_sortie
        if estimes > self._limite_tpm:
            log.warning(
                "%s: requête non envoyée (jetons du prompt=%d, max_tokens=%d, total=%d > limite de %d par minute)",
                self.nom, prompt, tokens_sortie, estimes, self._limite_tpm,
            )
            raise LLMTropLongError(MESSAGE_TROP_LONG)

    async def _appeler(self, parametres: dict[str, Any], module: str) -> Any:
        try:
            return await asyncio.wait_for(self._client.chat.completions.create(**parametres), timeout=self._timeout)
        except LLMError:
            raise
        except Exception as exc:  # noqa: BLE001 - tout est converti en LLMError
            raise _convertir_openai(exc, module, self.nom, avec_limite=bool(self._limite_tpm)) from None


MESSAGE_TROP_LONG = "Cours trop long pour le fournisseur de secours"


CARACTERES_PAR_JETON = 3.5


def estimer_tokens(texte: str) -> int:
    """Estimation prudente du nombre de jetons d'un texte (1 jeton pour 3,5 caractères), sans appel réseau."""
    return int(len(texte) / CARACTERES_PAR_JETON) + 1


def _estimer_tokens(messages: list[dict[str, Any]]) -> int:
    """Estimation prudente (1 jeton pour 3,5 caractères) du texte envoyé ; les images ne sont pas comptées."""
    caracteres = 0
    for m in messages:
        contenu = m["content"]
        caracteres += len(contenu) if isinstance(contenu, str) else sum(len(p.get("text", "")) for p in contenu)
    return int(caracteres / CARACTERES_PAR_JETON) + 1


def _exiger_mot_json(messages: list[dict[str, Any]]) -> None:
    """Le mode JSON refuse une requête où le mot « JSON » n'apparaît pas : on l'ajoute au dernier message utilisateur au besoin."""
    for m in messages:
        texte = m["content"] if isinstance(m["content"], str) else " ".join(p.get("text", "") for p in m["content"])
        if "json" in texte.lower():
            return
    for m in reversed(messages):
        if m["role"] == "user" and isinstance(m["content"], str):
            m["content"] += "\n\nRéponds uniquement avec un objet JSON valide."
            return


def _convertir_openai(exc: Exception, module: str, nom: str = "openai", *, avec_limite: bool = False) -> LLMError:
    """Traduit une exception du SDK openai en LLMError (même mapping que Gemini). Le message du SDK n'est jamais recopié."""
    import openai

    if isinstance(exc, (asyncio.TimeoutError, TimeoutError, openai.APITimeoutError)):
        log.warning("%s: délai dépassé (module=%s)", nom, module)
        return LLMTimeoutError("Le fournisseur LLM n'a pas répondu à temps")
    code = getattr(exc, "status_code", None)
    log.warning("%s: erreur du fournisseur (module=%s, type=%s, code=%s)", nom, module, type(exc).__name__, code)
    # Groq : requête plus grosse que la limite de jetons par minute (413, ou 429 « request too large ») : aucune relance utile.
    if avec_limite and (code == 413 or (code == 429 and _requete_trop_grosse(exc))):
        return LLMTropLongError(MESSAGE_TROP_LONG)
    if isinstance(exc, openai.RateLimitError) or code == 429:  # inclut insufficient_quota
        return LLMQuotaError("Quota du fournisseur LLM atteint, réessaie plus tard")
    if isinstance(exc, (openai.AuthenticationError, openai.PermissionDeniedError)) or code in (401, 403):
        return LLMMissingKeyError("Clé API refusée par le fournisseur LLM")
    if isinstance(exc, openai.NotFoundError) or code == 404:
        return LLMMissingKeyError("Modèle LLM introuvable : vérifie OPENAI_MODEL")
    if isinstance(exc, openai.APIConnectionError) or (code is not None and code >= 500):
        return LLMError("Le fournisseur LLM est surchargé ou indisponible, réessaie dans un instant")
    return RequeteRefuseeError("Requête refusée par le fournisseur LLM")


def _requete_trop_grosse(exc: Exception) -> bool:
    """Vrai si le fournisseur dit que la requête dépasse la limite de jetons par minute (lecture du message, jamais recopié)."""
    message = str(getattr(exc, "message", "") or exc).lower()
    return "request too large" in message or "too large" in message


def _messages_openai(messages: list[LLMMessage], image: tuple[bytes, str] | None) -> list[dict[str, Any]]:
    """Messages au format Chat Completions ; l'image (data URL) est jointe au dernier message utilisateur."""
    dernier_user = max((i for i, m in enumerate(messages) if m.role == "user"), default=None)
    envoyes: list[dict[str, Any]] = []
    for i, m in enumerate(messages):
        if image is not None and i == dernier_user:
            url = f"data:{image[1]};base64,{base64.b64encode(image[0]).decode('ascii')}"
            contenu: Any = [{"type": "text", "text": m.content}, {"type": "image_url", "image_url": {"url": url}}]
        else:
            contenu = m.content
        envoyes.append({"role": m.role, "content": contenu})
    return envoyes


def _texte_openai(reponse: Any) -> str:
    try:
        return (reponse.choices[0].message.content or "").strip()
    except Exception:  # noqa: BLE001 - pas de choix, refus, contenu absent
        return ""


async def _octets(reponse: Any) -> bytes:
    """Octets d'une réponse audio du SDK (`content`, `aread()` ou `read()` selon la version)."""
    contenu = getattr(reponse, "content", None)
    if isinstance(contenu, (bytes, bytearray)):
        return bytes(contenu)
    if hasattr(reponse, "aread"):
        return bytes(await reponse.aread())
    if hasattr(reponse, "read"):
        lu = reponse.read()
        return bytes(await lu if asyncio.iscoroutine(lu) else lu)
    return b""


def _assembler_wav(morceaux: list[bytes]) -> bytes:
    """Concatène des WAV de même format (celui de OpenAI) en un seul."""
    sortie = io.BytesIO()
    with wave.open(sortie, "wb") as final:
        for i, donnees in enumerate(morceaux):
            with wave.open(io.BytesIO(donnees), "rb") as w:
                if i == 0:
                    final.setparams(w.getparams())
                final.writeframes(w.readframes(w.getnframes()))
    return sortie.getvalue()


# Erreurs qui déclenchent le secours : quota (429), délai dépassé et fournisseur indisponible (5xx, réseau).
# Une clé refusée, une requête refusée ou une réponse invalide ne sont PAS retentées ailleurs : c'est une erreur de
# configuration ou de prompt, que le secours ne réglerait pas.
def _declenche_secours(exc: LLMError) -> bool:
    return isinstance(exc, (LLMQuotaError, LLMTimeoutError)) or type(exc) is LLMError


_fournisseur_utilise: ContextVar[str | None] = ContextVar("bawsla_llm_fournisseur", default=None)


def fournisseur_courant() -> str | None:
    """Fournisseur qui a réellement répondu au DERNIER appel LLM de la requête en cours ("gemini", "openai", "stub"…).

    À lire après `complete*`, pour l'enregistrer (suivi des traitements, journal des prompts).
    """
    return _fournisseur_utilise.get()


class LLMRoutage(LLMClient):
    """Appelle le fournisseur principal (Gemini) d'abord ; sur quota (429) ou indisponibilité, bascule sur le secours.

    Le fournisseur réellement utilisé est dans `LLMResult.fournisseur` et dans `fournisseur_courant()`.
    La synthèse vocale n'est PAS routée : la chaîne vocale du module Cours gère elle-même ses secours (TTS_FOURNISSEURS).
    """

    def __init__(self, principal: LLMClient, secours: LLMClient) -> None:
        self.principal = principal
        self.secours = secours

    async def _router(self, appel: str, *args: Any, **kwargs: Any) -> Any:
        nom_principal = getattr(self.principal, "nom", None)
        nom_secours = getattr(self.secours, "nom", None)
        try:
            resultat = await getattr(self.principal, appel)(*args, **kwargs)
            _fournisseur_utilise.set(nom_principal)
            return _marquer(resultat, nom_principal)
        except LLMError as exc:
            if not _declenche_secours(exc):
                raise
            log.warning("LLM: %s en échec (%s), bascule sur le fournisseur de secours %s", nom_principal, exc.code, nom_secours)
        resultat = await getattr(self.secours, appel)(*args, **kwargs)
        _fournisseur_utilise.set(nom_secours)
        return _marquer(resultat, nom_secours)

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024) -> LLMResult:
        return await self._router("complete", messages, module=module, temperature=temperature, max_tokens=max_tokens)

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024) -> LLMResult:
        return await self._router(
            "complete_with_image", messages, image, mime_type, module=module, temperature=temperature, max_tokens=max_tokens
        )

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        return await self._router("complete_json", messages, schema, module=module, temperature=temperature, max_tokens=max_tokens)

    async def embed(self, textes, *, module, tache="DOCUMENT") -> EmbeddingResult:
        """Routée comme le reste ; le modèle du secours diffère : `EmbeddingResult.modele` le dit à l'appelant."""
        resultat = await self._router("embed", textes, module=module, tache=tache)
        if resultat.fournisseur is None:
            resultat.fournisseur = fournisseur_courant()
        return resultat

    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        return await self.principal.synthetiser(texte, voix, langue)

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue) -> AudioResult:
        return await self.principal.synthetiser_dialogue(repliques, voix_par_locuteur, langue)


def _marquer(resultat: Any, nom: str | None) -> Any:
    if isinstance(resultat, LLMResult) and resultat.fournisseur is None:
        resultat.fournisseur = nom
    return resultat


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


TAILLE_LOT_EMBEDDING = 100
_MOTS = re.compile(r"\w+", re.UNICODE)


def _lots(elements: list[str], taille: int) -> list[list[str]]:
    return [elements[i:i + taille] for i in range(0, len(elements), taille)]


def _normaliser(vecteur: list[float]) -> list[float]:
    """Ramène un vecteur à la norme 1 : le cosinus devient un simple produit scalaire."""
    norme = math.sqrt(sum(x * x for x in vecteur))
    return [x / norme for x in vecteur] if norme > 0 else list(vecteur)


def _vecteur_hache(texte: str, dimension: int) -> list[float]:
    """Vecteur déterministe d'un texte : mots et trigrammes de caractères hachés (blake2b) dans `dimension` cases."""
    vecteur = [0.0] * dimension
    mots = [m for m in _MOTS.findall((texte or "").lower()) if len(m) > 1]
    elements = mots + [m[i:i + 3] for m in mots for i in range(max(1, len(m) - 2))]
    for element in elements:
        empreinte = hashlib.blake2b(element.encode("utf-8"), digest_size=8).digest()
        case = int.from_bytes(empreinte[:4], "little") % dimension
        vecteur[case] += 1.0 if empreinte[4] % 2 == 0 else -1.0
    return _normaliser(vecteur)


def _strip_code_fence(text: str) -> str:
    """Retire un éventuel bloc ```json ... ``` autour de la réponse."""
    t = text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else ""
        t = t.rsplit("```", 1)[0]
    return t.strip()


def build_openai_client(settings: Settings) -> OpenAILLMClient | None:
    """Client OpenAI, ou None si OPENAI_API_KEY ou OPENAI_MODEL manque."""
    if settings.openai_api_key is None or not settings.openai_api_key.get_secret_value() or not settings.openai_model:
        return None
    return OpenAILLMClient(
        settings.openai_api_key.get_secret_value(),
        settings.openai_model,
        timeout_seconds=settings.llm_timeout_seconds,
        reasoning_effort=settings.openai_reasoning_effort,
        tts_model=settings.openai_tts_model,
        embedding_model=settings.openai_embedding_model,
        embedding_dimension=settings.llm_embedding_dimension,
    )


def build_groq_client(settings: Settings) -> OpenAILLMClient | None:
    """Client Groq (même classe que OpenAI, autre base_url), ou None si GROQ_API_KEY ou GROQ_MODEL manque."""
    if settings.groq_api_key is None or not settings.groq_api_key.get_secret_value() or not settings.groq_model:
        return None
    return OpenAILLMClient(
        settings.groq_api_key.get_secret_value(),
        settings.groq_model,
        timeout_seconds=settings.llm_timeout_seconds,
        reasoning_effort=settings.groq_reasoning_effort,
        nom="groq",
        base_url=settings.groq_base_url,
        max_retries=0,  # pas de relances automatiques : un quota atteint ne se règle pas en réessayant
        limite_tokens_minute=settings.groq_tokens_par_minute,
        marge_reflexion=settings.groq_marge_reflexion,
    )


def build_llm_client(settings: Settings) -> LLMClient:
    if settings.llm_provider == "stub":
        return StubLLMClient()
    if settings.llm_provider == "openai":
        client = build_openai_client(settings)
        if client is None:
            raise LLMMissingKeyError("OPENAI_API_KEY ou OPENAI_MODEL absent pour le fournisseur 'openai'")
        return client
    if settings.llm_api_key is None or not settings.llm_api_key.get_secret_value():
        raise LLMMissingKeyError(f"LLM_API_KEY absente pour le fournisseur '{settings.llm_provider}'")
    if settings.llm_provider == "gemini":
        if not settings.llm_model:
            raise LLMMissingKeyError("LLM_MODEL absent pour le fournisseur 'gemini'")
        gemini = GeminiLLMClient(
            settings.llm_api_key.get_secret_value(),
            settings.llm_model,
            timeout_seconds=settings.llm_timeout_seconds,
            thinking_budget=settings.llm_thinking_budget,
            tts_model=settings.llm_tts_model,
            embedding_model=settings.llm_embedding_model,
            embedding_dimension=settings.llm_embedding_dimension,
        )
        return _avec_secours(gemini, settings)
    raise LLMMissingKeyError(f"Fournisseur LLM '{settings.llm_provider}' non implémenté")


def _avec_secours(principal: LLMClient, settings: Settings) -> LLMClient:
    """Enveloppe le client principal dans un routeur si LLM_SECOURS est renseigné et utilisable (vide = pas de secours)."""
    secours = settings.llm_secours.strip().lower()
    if not secours:
        return principal
    if secours not in ("openai", "groq"):
        log.warning("LLM_SECOURS=%r inconnu : aucun secours (valeurs acceptées : openai, groq)", secours)
        return principal
    client = build_openai_client(settings) if secours == "openai" else build_groq_client(settings)
    if client is None:
        cle = "OPENAI_API_KEY ou OPENAI_MODEL" if secours == "openai" else "GROQ_API_KEY ou GROQ_MODEL"
        log.warning("LLM_SECOURS=%s mais %s est absent : aucun secours", secours, cle)
        return principal
    return LLMRoutage(principal, client)


_clients: dict[tuple[str, str | None, str], LLMClient] = {}


def get_llm_client() -> LLMClient:
    """Dépendance FastAPI : `llm: LLMClient = Depends(get_llm_client)`. Un client par fournisseur et modèle."""
    settings = get_settings()
    cle = (settings.llm_provider, settings.llm_model, settings.llm_secours)
    if cle not in _clients:
        _clients[cle] = build_llm_client(settings)
    return _clients[cle]
