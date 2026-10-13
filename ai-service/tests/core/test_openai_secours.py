"""Tests de OpenAILLMClient (fournisseur de secours) et de LLMRoutage avec un SDK simulé : aucun appel réseau, aucune vraie clé."""

import asyncio
import io
import logging
import wave
from types import SimpleNamespace

import httpx
import openai
import pytest
from pydantic import BaseModel

from app.core.config import Settings
from app.core.errors import (
    LLMError,
    LLMInvalidResponseError,
    LLMMissingKeyError,
    LLMQuotaError,
    LLMTimeoutError,
)
from app.core.llm_client import (
    AudioResult,
    GeminiLLMClient,
    LLMClient,
    LLMMessage,
    LLMResult,
    LLMRoutage,
    OpenAILLMClient,
    Replique,
    RequeteRefuseeError,
    StubLLMClient,
    build_llm_client,
    fournisseur_courant,
)

CLE_SECRETE = "sk-cle-factice-pour-les-tests-0123456789"
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 12
MESSAGES = [LLMMessage(role="system", content="Tu es utile."), LLMMessage(role="user", content="Bonjour, texte secret du cours")]


class Reponse(BaseModel):
    titre: str


# ---------------------------------------------------------------- SDK simulé


def _http(statut: int) -> httpx.Response:
    return httpx.Response(statut, request=httpx.Request("POST", "https://api.openai.test/v1/chat/completions"))


def erreur_http(statut: int) -> Exception:
    classes = {
        400: openai.BadRequestError, 401: openai.AuthenticationError, 403: openai.PermissionDeniedError,
        404: openai.NotFoundError, 429: openai.RateLimitError, 500: openai.InternalServerError, 503: openai.InternalServerError,
    }
    return classes[statut]("message du SDK avec contexte", response=_http(statut), body=None)


def reponse_chat(texte: str | None, entree: int = 11, sortie: int = 7):
    message = SimpleNamespace(content=texte)
    return SimpleNamespace(choices=[SimpleNamespace(message=message)], usage=SimpleNamespace(prompt_tokens=entree, completion_tokens=sortie))


class FauxChat:
    """Simule `client.chat.completions` et `client.audio.speech` : rejoue des réponses (ou des exceptions) dans l'ordre."""

    def __init__(self, scenario, audio=None):
        self.scenario = list(scenario)
        self.appels: list[dict] = []
        self.appels_audio: list[dict] = []
        self.audio_scenario = list(audio or [])
        self.chat = SimpleNamespace(completions=SimpleNamespace(create=self._create))
        self.audio = SimpleNamespace(speech=SimpleNamespace(create=self._speech))

    async def _create(self, **parametres):
        self.appels.append(parametres)
        element = self.scenario.pop(0)
        if isinstance(element, Exception):
            raise element
        return element

    async def _speech(self, **parametres):
        self.appels_audio.append(parametres)
        element = self.audio_scenario.pop(0)
        if isinstance(element, Exception):
            raise element
        return SimpleNamespace(content=element)


def client_openai(scenario, **options) -> tuple[OpenAILLMClient, FauxChat]:
    faux = FauxChat(scenario, audio=options.pop("audio", None))
    return OpenAILLMClient(CLE_SECRETE, "gpt-test", client=faux, **options), faux


def wav(frames: bytes) -> bytes:
    sortie = io.BytesIO()
    with wave.open(sortie, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(24000)
        w.writeframes(frames)
    return sortie.getvalue()


# ---------------------------------------------------------------- OpenAILLMClient


async def test_complete_envoie_le_modele_les_messages_et_marque_le_fournisseur():
    client, faux = client_openai([reponse_chat("  Réponse  ")], reasoning_effort="low")
    resultat = await client.complete(MESSAGES, module="cours", temperature=0.3, max_tokens=500)

    assert resultat.text == "Réponse"
    assert (resultat.model, resultat.fournisseur, resultat.input_tokens, resultat.output_tokens) == ("gpt-test", "openai", 11, 7)
    appel = faux.appels[0]
    assert appel["model"] == "gpt-test"
    assert appel["messages"] == [{"role": "system", "content": "Tu es utile."}, {"role": "user", "content": "Bonjour, texte secret du cours"}]
    assert appel["temperature"] == 0.3
    assert appel["reasoning_effort"] == "low"
    assert appel["max_completion_tokens"] == 500 + OpenAILLMClient.MARGE_REFLEXION  # la réflexion compte dans la limite
    assert "response_format" not in appel


async def test_sans_effort_de_reflexion_la_limite_de_sortie_reste_celle_demandee():
    client, faux = client_openai([reponse_chat("ok")], reasoning_effort=None)
    await client.complete(MESSAGES, module="cours", max_tokens=500)
    assert faux.appels[0]["max_completion_tokens"] == 500
    assert "reasoning_effort" not in faux.appels[0]


async def test_complete_json_active_le_mode_json_et_valide_le_schema():
    client, faux = client_openai([reponse_chat('```json\n{"titre": "Jointures"}\n```')])
    resultat = await client.complete_json(MESSAGES, Reponse, module="cours")
    assert resultat == Reponse(titre="Jointures")
    assert faux.appels[0]["response_format"] == {"type": "json_object"}


async def test_complete_json_relance_une_fois_si_le_json_est_invalide():
    client, faux = client_openai([reponse_chat("pas du json"), reponse_chat('{"titre": "ok"}')])
    assert (await client.complete_json(MESSAGES, Reponse, module="cours")).titre == "ok"
    assert len(faux.appels) == 2
    assert faux.appels[1]["messages"][-1]["role"] == "user" and "JSON" in faux.appels[1]["messages"][-1]["content"]


async def test_complete_json_deux_reponses_invalides_leve_une_erreur_de_reponse():
    client, faux = client_openai([reponse_chat("non"), reponse_chat("toujours non")])
    with pytest.raises(LLMInvalidResponseError):
        await client.complete_json(MESSAGES, Reponse, module="cours")
    assert len(faux.appels) == 2


async def test_complete_with_image_joint_une_data_url_au_dernier_message_utilisateur():
    client, faux = client_openai([reponse_chat("Une image.")])
    await client.complete_with_image(MESSAGES, PNG, "image/png", module="cours")
    contenu = faux.appels[0]["messages"][-1]["content"]
    assert contenu[0] == {"type": "text", "text": "Bonjour, texte secret du cours"}
    assert contenu[1]["type"] == "image_url" and contenu[1]["image_url"]["url"].startswith("data:image/png;base64,")
    assert faux.appels[0]["messages"][0]["content"] == "Tu es utile."  # le message système reste du texte


async def test_image_non_prise_en_charge_ou_vide():
    client, faux = client_openai([])
    with pytest.raises(LLMInvalidResponseError):
        await client.complete_with_image(MESSAGES, PNG, "image/gif", module="cours")
    with pytest.raises(LLMInvalidResponseError):
        await client.complete_with_image(MESSAGES, b"", "image/png", module="cours")
    assert faux.appels == []


async def test_reponse_vide_ou_sans_message_utilisateur():
    client, _ = client_openai([reponse_chat(None)])
    with pytest.raises(LLMInvalidResponseError):
        await client.complete(MESSAGES, module="cours")
    client, faux = client_openai([])
    with pytest.raises(LLMInvalidResponseError):
        await client.complete([LLMMessage(role="system", content="seul")], module="cours")
    assert faux.appels == []


@pytest.mark.parametrize(
    ("statut", "attendu"),
    [
        (429, LLMQuotaError),
        (401, LLMMissingKeyError),
        (403, LLMMissingKeyError),
        (404, LLMMissingKeyError),
        (400, RequeteRefuseeError),
        (500, LLMError),
        (503, LLMError),
    ],
)
async def test_erreurs_du_sdk_converties_en_llm_error(statut, attendu):
    client, _ = client_openai([erreur_http(statut)], reasoning_effort=None)
    with pytest.raises(attendu) as info:
        await client.complete(MESSAGES, module="cours")
    assert type(info.value) is attendu
    assert "contexte" not in str(info.value)  # le message du SDK n'est jamais recopié


async def test_erreurs_reseau_et_delai():
    connexion = openai.APIConnectionError(request=httpx.Request("POST", "https://api.openai.test"))
    client, _ = client_openai([connexion])
    with pytest.raises(LLMError) as info:
        await client.complete(MESSAGES, module="cours")
    assert type(info.value) is LLMError

    delai = openai.APITimeoutError(request=httpx.Request("POST", "https://api.openai.test"))
    client, _ = client_openai([delai])
    with pytest.raises(LLMTimeoutError):
        await client.complete(MESSAGES, module="cours")

    class Lent(FauxChat):
        async def _create(self, **parametres):
            await asyncio.sleep(5)

    lent = Lent([])
    with pytest.raises(LLMTimeoutError):
        await OpenAILLMClient(CLE_SECRETE, "gpt-test", timeout_seconds=0.05, client=lent).complete(MESSAGES, module="cours")


async def test_parametre_refuse_retire_une_fois_puis_memorise():
    client, faux = client_openai([erreur_http(400), reponse_chat("ok"), reponse_chat("encore ok")], reasoning_effort="low")
    assert (await client.complete(MESSAGES, module="cours", max_tokens=300)).text == "ok"
    assert "temperature" in faux.appels[0] and "reasoning_effort" in faux.appels[0]
    assert "temperature" not in faux.appels[1] and "reasoning_effort" not in faux.appels[1]
    assert faux.appels[1]["max_completion_tokens"] == 300
    await client.complete(MESSAGES, module="cours")
    assert "temperature" not in faux.appels[2]  # on s'en souvient


async def test_un_400_sans_parametre_optionnel_n_est_pas_retente():
    client, faux = client_openai([erreur_http(400), erreur_http(400)], reasoning_effort=None)
    client._temperature_acceptee = False
    with pytest.raises(RequeteRefuseeError):
        await client.complete(MESSAGES, module="cours")
    assert len(faux.appels) == 1


async def test_ni_la_cle_ni_le_prompt_dans_les_logs(caplog):
    caplog.set_level(logging.DEBUG)
    client, _ = client_openai([reponse_chat("ok"), erreur_http(429)])
    await client.complete(MESSAGES, module="cours")
    with pytest.raises(LLMQuotaError):
        await client.complete(MESSAGES, module="cours")
    journal = caplog.text
    assert CLE_SECRETE not in journal
    assert "texte secret du cours" not in journal


# ---------------------------------------------------------------- synthèse vocale OpenAI


async def test_synthese_vocale_mp3():
    client, faux = client_openai([], audio=[b"ID3-mp3"], tts_model="gpt-4o-mini-tts")
    son = await client.synthetiser("Bonjour.", "marin", "fr")
    assert (son.data, son.mime_type, son.model) == (b"ID3-mp3", "audio/mpeg", "gpt-4o-mini-tts")
    assert faux.appels_audio[0] == {"model": "gpt-4o-mini-tts", "voice": "marin", "input": "Bonjour.", "response_format": "mp3"}


async def test_synthese_vocale_erreurs():
    client, _ = client_openai([], audio=[erreur_http(429), b""])
    with pytest.raises(LLMQuotaError):
        await client.synthetiser("Bonjour.", "marin", "fr")
    with pytest.raises(LLMInvalidResponseError):
        await client.synthetiser("Bonjour.", "marin", "fr")
    with pytest.raises(LLMInvalidResponseError):
        await client.synthetiser("   ", "marin", "fr")


async def test_dialogue_une_requete_par_replique_et_wav_assembles():
    client, faux = client_openai([], audio=[wav(b"\x01\x00" * 100), wav(b"\x02\x00" * 50)])
    son = await client.synthetiser_dialogue(
        [Replique(locuteur="PROF", texte="Salut."), Replique(locuteur="ETU", texte="Question ?")], {"PROF": "marin", "ETU": "cedar"}, "fr"
    )
    assert [a["voice"] for a in faux.appels_audio] == ["marin", "cedar"]
    assert son.mime_type == "audio/wav"
    with wave.open(io.BytesIO(son.data), "rb") as w:
        assert w.getnframes() == 150 and w.getframerate() == 24000
    with pytest.raises(LLMInvalidResponseError):
        await client.synthetiser_dialogue([Replique(locuteur="X", texte="a")], {}, "fr")


# ---------------------------------------------------------------- routage Gemini -> OpenAI


class Principal(LLMClient):
    """Faux Gemini : réussit ou lève l'erreur programmée."""

    nom = "gemini"

    def __init__(self, erreur: Exception | None = None):
        self.erreur = erreur
        self.appels = 0

    async def _repondre(self):
        self.appels += 1
        if self.erreur:
            raise self.erreur

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024):
        await self._repondre()
        return LLMResult(text="gemini", model="g", fournisseur="gemini")

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024):
        await self._repondre()
        return LLMResult(text="gemini-image", model="g", fournisseur="gemini")

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        await self._repondre()
        return schema(titre="de gemini")

    async def synthetiser(self, texte, voix, langue):
        await self._repondre()
        return AudioResult(data=b"gemini", mime_type="audio/wav", model="g")

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        await self._repondre()
        return AudioResult(data=b"gemini", mime_type="audio/wav", model="g")


def routage(erreur_principal=None, scenario_secours=None):
    secours, faux = client_openai(scenario_secours if scenario_secours is not None else [reponse_chat("openai")])
    principal = Principal(erreur_principal)
    return LLMRoutage(principal, secours), principal, faux


async def test_gemini_repond_le_secours_n_est_pas_appele():
    llm, principal, faux = routage()
    resultat = await llm.complete(MESSAGES, module="cours")
    assert (resultat.text, resultat.fournisseur, fournisseur_courant()) == ("gemini", "gemini", "gemini")
    assert faux.appels == []


@pytest.mark.parametrize("erreur", [LLMQuotaError("Quota"), LLMTimeoutError("Délai"), LLMError("Surchargé")])
async def test_bascule_sur_openai_quand_gemini_est_en_quota_ou_indisponible(erreur, caplog):
    caplog.set_level(logging.INFO)
    llm, principal, faux = routage(erreur)
    resultat = await llm.complete(MESSAGES, module="cours")
    assert (resultat.text, resultat.fournisseur) == ("openai", "openai")
    assert fournisseur_courant() == "openai"
    assert principal.appels == 1 and len(faux.appels) == 1
    assert "bascule sur le fournisseur de secours openai" in caplog.text
    assert "texte secret du cours" not in caplog.text


async def test_bascule_aussi_pour_complete_json_et_les_images():
    llm, _, faux = routage(LLMQuotaError("Quota"), [reponse_chat('{"titre": "de openai"}'), reponse_chat("vue par openai")])
    assert (await llm.complete_json(MESSAGES, Reponse, module="cours")).titre == "de openai"
    assert fournisseur_courant() == "openai"
    assert (await llm.complete_with_image(MESSAGES, PNG, "image/png", module="cours")).fournisseur == "openai"
    assert faux.appels[0]["response_format"] == {"type": "json_object"}


@pytest.mark.parametrize(
    "erreur", [LLMMissingKeyError("Clé"), LLMInvalidResponseError("JSON"), RequeteRefuseeError("Refusé")]
)
async def test_pas_de_bascule_pour_une_erreur_de_configuration_ou_de_requete(erreur):
    llm, principal, faux = routage(erreur)
    with pytest.raises(type(erreur)):
        await llm.complete(MESSAGES, module="cours")
    assert faux.appels == []


async def test_si_le_secours_echoue_aussi_son_erreur_est_levee():
    llm, _, _ = routage(LLMQuotaError("Quota Gemini"), [erreur_http(429)])
    with pytest.raises(LLMQuotaError):
        await llm.complete(MESSAGES, module="cours")


async def test_la_synthese_vocale_n_est_pas_routee():
    llm, principal, faux = routage(LLMQuotaError("Quota"))
    with pytest.raises(LLMQuotaError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")  # la chaîne vocale du module Cours gère ses propres secours
    assert faux.appels_audio == []


# ---------------------------------------------------------------- construction depuis la configuration


def reglages(**valeurs) -> Settings:
    base = {"llm_provider": "gemini", "llm_api_key": "AIza-factice", "llm_model": "gemini-test", "llm_secours": "",
            "openai_api_key": CLE_SECRETE, "openai_model": "gpt-test"}
    return Settings(_env_file=None, **{**base, **valeurs})


def test_llm_secours_vide_pas_de_secours():
    assert isinstance(build_llm_client(reglages()), GeminiLLMClient)
    assert isinstance(build_llm_client(reglages(llm_secours="  ")), GeminiLLMClient)


def test_llm_secours_openai_construit_le_routage():
    client = build_llm_client(reglages(llm_secours="OpenAI"))
    assert isinstance(client, LLMRoutage)
    assert isinstance(client.principal, GeminiLLMClient) and isinstance(client.secours, OpenAILLMClient)


def test_secours_inutilisable_est_ignore_sans_casser_gemini(caplog):
    assert isinstance(build_llm_client(reglages(llm_secours="openai", openai_api_key=None)), GeminiLLMClient)
    assert isinstance(build_llm_client(reglages(llm_secours="openai", openai_model=None)), GeminiLLMClient)
    assert isinstance(build_llm_client(reglages(llm_secours="mistral")), GeminiLLMClient)
    assert "aucun secours" in caplog.text


def test_fournisseur_openai_direct_et_stub_sans_secours():
    assert isinstance(build_llm_client(reglages(llm_provider="openai")), OpenAILLMClient)
    with pytest.raises(LLMMissingKeyError):
        build_llm_client(reglages(llm_provider="openai", openai_api_key=None))
    assert isinstance(build_llm_client(reglages(llm_provider="stub", llm_secours="openai")), StubLLMClient)
