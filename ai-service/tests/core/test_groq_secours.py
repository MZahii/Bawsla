"""Groq comme fournisseur de secours : même client que OpenAI (API compatible), autre base_url. SDK simulé, aucune clé réelle."""

import logging

import openai
import pytest
from pydantic import BaseModel

from app.core.config import Settings
from app.core.errors import LLMQuotaError, LLMTropLongError
from app.core.llm_client import (
    GeminiLLMClient,
    LLMMessage,
    LLMRoutage,
    OpenAILLMClient,
    build_llm_client,
    fournisseur_courant,
)
from tests.core.test_openai_secours import (
    MESSAGES,
    CLE_SECRETE,
    FauxChat,
    Principal,
    _http,
    erreur_http,
    reponse_chat,
)

CLE_GROQ = "gsk-cle-factice-pour-les-tests-0123456789"


class Reponse(BaseModel):
    titre: str


def groq(scenario, **options) -> tuple[OpenAILLMClient, FauxChat]:
    faux = FauxChat(scenario)
    options.setdefault("limite_tokens_minute", 8000)
    return OpenAILLMClient(CLE_GROQ, "openai/gpt-oss-120b", client=faux, nom="groq", max_retries=0, **options), faux


def reglages(**valeurs) -> Settings:
    base = {"llm_provider": "gemini", "llm_api_key": "AIza-factice", "llm_model": "gemini-test", "llm_secours": "groq",
            "groq_api_key": CLE_GROQ, "openai_api_key": CLE_SECRETE, "openai_model": "gpt-test"}
    return Settings(_env_file=None, **{**base, **valeurs})


# ---------------------------------------------------------------- construction


def test_groq_reutilise_le_client_openai_avec_la_base_url_de_groq():
    client = build_llm_client(reglages())
    assert isinstance(client, LLMRoutage) and isinstance(client.principal, GeminiLLMClient)
    secours = client.secours
    assert type(secours) is OpenAILLMClient and secours.nom == "groq"  # aucun nouveau client
    assert str(secours._client.base_url) == "https://api.groq.com/openai/v1/"
    assert secours._model == "openai/gpt-oss-120b"
    assert secours._client.max_retries == 0  # pas de relances automatiques
    assert secours._limite_tpm == 8000


def test_base_url_et_modele_configurables():
    client = build_llm_client(reglages(groq_base_url="https://groq.exemple.test/v1", groq_model="llama-test", groq_tokens_par_minute=12000))
    assert str(client.secours._client.base_url) == "https://groq.exemple.test/v1/"
    assert (client.secours._model, client.secours._limite_tpm) == ("llama-test", 12000)


def test_llm_secours_openai_continue_de_fonctionner():
    client = build_llm_client(reglages(llm_secours="openai"))
    assert isinstance(client, LLMRoutage) and client.secours.nom == "openai"
    assert str(client.secours._client.base_url) == "https://api.openai.com/v1/"
    assert client.secours._limite_tpm is None  # la limite ne concerne que Groq


def test_groq_sans_cle_ou_sans_modele_est_ignore(caplog):
    assert isinstance(build_llm_client(reglages(groq_api_key=None)), GeminiLLMClient)
    assert isinstance(build_llm_client(reglages(groq_model=None)), GeminiLLMClient)
    assert "GROQ_API_KEY ou GROQ_MODEL est absent" in caplog.text


# ---------------------------------------------------------------- bascule et fournisseur réel


async def test_bascule_sur_groq_le_fournisseur_reel_est_dans_la_reponse_et_les_logs(caplog):
    caplog.set_level(logging.INFO)
    secours, faux = groq([reponse_chat("réponse de groq")])
    llm = LLMRoutage(Principal(LLMQuotaError("Quota Gemini")), secours)

    resultat = await llm.complete(MESSAGES, module="cours")

    assert (resultat.text, resultat.fournisseur, fournisseur_courant()) == ("réponse de groq", "groq", "groq")
    assert faux.appels[0]["model"] == "openai/gpt-oss-120b"
    assert "bascule sur le fournisseur de secours groq" in caplog.text
    assert "groq: module=cours modele=openai/gpt-oss-120b" in caplog.text  # le journal d'usage nomme le vrai fournisseur
    assert CLE_GROQ not in caplog.text and "texte secret du cours" not in caplog.text


async def test_gemini_repond_groq_n_est_pas_appele():
    secours, faux = groq([])
    llm = LLMRoutage(Principal(), secours)
    assert (await llm.complete(MESSAGES, module="cours")).fournisseur == "gemini"
    assert faux.appels == []


async def test_json_via_groq_exige_le_mot_json_dans_les_messages():
    secours, faux = groq([reponse_chat('{"titre": "ok"}'), reponse_chat('{"titre": "ok"}')])
    sans_mot = [LLMMessage(role="user", content="Résume ce cours.")]
    assert (await secours.complete_json(sans_mot, Reponse, module="cours")).titre == "ok"
    assert faux.appels[0]["response_format"] == {"type": "json_object"}
    assert "JSON" in faux.appels[0]["messages"][-1]["content"]
    assert sans_mot[0].content == "Résume ce cours."  # le message d'origine n'est pas modifié
    # Déjà présent : rien n'est ajouté.
    await secours.complete_json([LLMMessage(role="user", content="Réponds en JSON.")], Reponse, module="cours")
    assert faux.appels[1]["messages"][-1]["content"] == "Réponds en JSON."


# ---------------------------------------------------------------- cours trop long pour la limite de jetons par minute


async def test_texte_trop_long_erreur_claire_sans_appel_ni_relance():
    secours, faux = groq([])
    gros = [LLMMessage(role="user", content="mot " * 20_000)]  # ~80 000 caractères, bien au-dessus de 8 000 jetons
    with pytest.raises(LLMTropLongError) as info:
        await secours.complete(gros, module="cours")
    assert info.value.message == "Cours trop long pour le fournisseur de secours"
    assert info.value.status_code == 413
    assert faux.appels == []


async def test_la_limite_compte_aussi_les_jetons_de_sortie_demandes():
    secours, faux = groq([reponse_chat("ok")], reasoning_effort=None)
    texte = [LLMMessage(role="user", content="x" * 14_000)]  # ~4 000 jetons
    await secours.complete(texte, module="cours", max_tokens=1000)  # 4 000 + 1 000 : passe
    with pytest.raises(LLMTropLongError):
        await secours.complete(texte, module="cours", max_tokens=6000)  # 4 000 + 6 000 > 8 000
    assert len(faux.appels) == 1


async def test_sans_limite_configuree_rien_n_est_refuse_d_avance():
    secours, faux = groq([reponse_chat("ok")], limite_tokens_minute=None)
    await secours.complete([LLMMessage(role="user", content="mot " * 20_000)], module="cours")
    assert len(faux.appels) == 1


async def test_via_le_routage_le_message_du_secours_remplace_le_quota_gemini():
    secours, faux = groq([])
    llm = LLMRoutage(Principal(LLMQuotaError("Quota Gemini")), secours)
    with pytest.raises(LLMTropLongError, match="Cours trop long pour le fournisseur de secours"):
        await llm.complete([LLMMessage(role="user", content="mot " * 20_000)], module="cours")
    assert faux.appels == []


async def test_un_cours_court_passe_et_un_cours_long_n_atteint_jamais_groq_si_gemini_repond():
    secours, faux = groq([])
    llm = LLMRoutage(Principal(), secours)  # Gemini répond : la limite de Groq ne concerne pas ce cas
    assert (await llm.complete([LLMMessage(role="user", content="mot " * 20_000)], module="cours")).fournisseur == "gemini"
    assert faux.appels == []


@pytest.mark.parametrize("statut", [413])
async def test_413_du_fournisseur_devient_cours_trop_long(statut):
    secours, faux = groq([openai.APIStatusError("Request Entity Too Large", response=_http(statut), body=None)])
    with pytest.raises(LLMTropLongError):
        await secours.complete(MESSAGES, module="cours")
    assert len(faux.appels) == 1  # pas de nouvel essai


async def test_429_request_too_large_devient_cours_trop_long_mais_un_vrai_429_reste_un_quota():
    trop_gros = openai.RateLimitError("Request too large for model on tokens per minute (TPM): Limit 8000, Requested 9500",
                                      response=_http(429), body=None)
    secours, faux = groq([trop_gros, erreur_http(429)])
    with pytest.raises(LLMTropLongError):
        await secours.complete(MESSAGES, module="cours")
    with pytest.raises(LLMQuotaError):
        await secours.complete(MESSAGES, module="cours")
    assert len(faux.appels) == 2  # une requête par appel, aucune relance
