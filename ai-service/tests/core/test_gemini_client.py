"""Tests de GeminiLLMClient avec un SDK simulé : aucun appel réseau, aucune vraie clé."""

import asyncio
import logging
from types import SimpleNamespace

import pytest
from google.genai import errors
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
    GeminiLLMClient,
    LLMMessage,
    RequeteRefuseeError,
    StubLLMClient,
    build_llm_client,
)

CLE_SECRETE = "AIza-cle-factice-pour-les-tests-0123456789"
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 12


class FauxModeles:
    """Simule `client.aio.models` : rejoue des réponses (ou des exceptions) dans l'ordre."""

    def __init__(self, scenario):
        self.scenario = list(scenario)
        self.appels: list[dict] = []

    async def generate_content(self, *, model, contents, config):
        self.appels.append({"model": model, "contents": contents, "config": config})
        element = self.scenario.pop(0)
        if callable(element):
            return await element()
        if isinstance(element, Exception):
            raise element
        return element


def faux_client(*scenario) -> tuple[SimpleNamespace, FauxModeles]:
    modeles = FauxModeles(scenario)
    return SimpleNamespace(aio=SimpleNamespace(models=modeles)), modeles


def reponse(texte, entree=12, sortie=7):
    return SimpleNamespace(text=texte, usage_metadata=SimpleNamespace(prompt_token_count=entree, candidates_token_count=sortie))


def erreur_api(code, statut, message="boom"):
    classe = errors.ClientError if code < 500 else errors.ServerError
    # Le message d'un vrai SDK peut contenir du contexte : il ne doit jamais ressortir tel quel.
    return classe(code, {"error": {"code": code, "message": f"{message} {CLE_SECRETE}", "status": statut}})


def gemini(*scenario, **options) -> tuple[GeminiLLMClient, FauxModeles]:
    client, modeles = faux_client(*scenario)
    options.setdefault("attente_entre_tentatives", 0)
    return GeminiLLMClient(CLE_SECRETE, "gemini-test", client=client, **options), modeles


MESSAGES = [LLMMessage(role="system", content="Tu es utile."), LLMMessage(role="user", content="Bonjour")]


class Fiche(BaseModel):
    titre: str
    points: list[str]


# ---------------------------------------------------------------- complete()


async def test_complete_renvoie_seulement_le_texte():
    llm, modeles = gemini(reponse("  Bonjour !  "))
    resultat = await llm.complete(MESSAGES, module="cours")
    assert resultat.text == "Bonjour !"
    assert resultat.model == "gemini-test"
    assert (resultat.input_tokens, resultat.output_tokens) == (12, 7)
    assert resultat.stub is False
    assert len(modeles.appels) == 1


async def test_consignes_systeme_et_reflexion_reduite():
    llm, modeles = gemini(reponse("ok"), thinking_budget=128)
    await llm.complete(MESSAGES, module="cours", temperature=0.2, max_tokens=500)
    config = modeles.appels[0]["config"]
    assert config.system_instruction == "Tu es utile."
    assert config.temperature == 0.2
    assert config.thinking_config.thinking_budget == 128
    # Les jetons de réflexion comptent dans la sortie : la limite les inclut.
    assert config.max_output_tokens == 628
    # Seul le message utilisateur part dans `contents`.
    contenu = modeles.appels[0]["contents"]
    assert len(contenu) == 1 and contenu[0].role == "user" and contenu[0].parts[0].text == "Bonjour"


async def test_sans_budget_de_reflexion_le_reglage_du_fournisseur_s_applique():
    llm, modeles = gemini(reponse("ok"), thinking_budget=None)
    await llm.complete(MESSAGES, module="cours", max_tokens=100)
    config = modeles.appels[0]["config"]
    assert config.thinking_config is None
    assert config.max_output_tokens == 100


async def test_reponse_vide_ou_bloquee():
    llm, _ = gemini(reponse(""))
    with pytest.raises(LLMInvalidResponseError):
        await llm.complete(MESSAGES, module="cours")


async def test_pas_de_message_utilisateur():
    llm, modeles = gemini()
    with pytest.raises(LLMInvalidResponseError):
        await llm.complete([LLMMessage(role="system", content="seulement une consigne")], module="cours")
    assert modeles.appels == []


# ---------------------------------------------------------------- complete_json()


async def test_complete_json_valide_et_force_le_mime_json():
    llm, modeles = gemini(reponse('{"titre": "SQL", "points": ["a", "b"]}'))
    fiche = await llm.complete_json(MESSAGES, Fiche, module="cours")
    assert fiche == Fiche(titre="SQL", points=["a", "b"])
    assert modeles.appels[0]["config"].response_mime_type == "application/json"


async def test_complete_json_retire_les_balises_de_code():
    llm, _ = gemini(reponse('```json\n{"titre": "SQL", "points": []}\n```'))
    assert (await llm.complete_json(MESSAGES, Fiche, module="cours")).titre == "SQL"


async def test_complete_json_relance_une_fois_si_le_json_est_invalide():
    llm, modeles = gemini(reponse("ceci n'est pas du JSON"), reponse('{"titre": "ok", "points": []}'))
    fiche = await llm.complete_json(MESSAGES, Fiche, module="cours")
    assert fiche.titre == "ok"
    assert len(modeles.appels) == 2
    relance = modeles.appels[1]["contents"]
    assert "JSON" in relance[-1].parts[0].text  # la consigne de correction est envoyée
    assert relance[-2].role == "model"  # avec la réponse invalide précédente


async def test_complete_json_deux_echecs_donnent_une_erreur():
    llm, modeles = gemini(reponse("pas json"), reponse('{"titre": 3}'))
    with pytest.raises(LLMInvalidResponseError):
        await llm.complete_json(MESSAGES, Fiche, module="cours")
    assert len(modeles.appels) == 2  # une seule relance


# ---------------------------------------------------------------- erreurs


async def test_quota_429():
    llm, modeles = gemini(erreur_api(429, "RESOURCE_EXHAUSTED"))
    with pytest.raises(LLMQuotaError):
        await llm.complete(MESSAGES, module="cours")
    assert len(modeles.appels) == 1  # un quota n'est pas retenté


@pytest.mark.parametrize("code,statut", [(401, "UNAUTHENTICATED"), (403, "PERMISSION_DENIED")])
async def test_cle_refusee(code, statut):
    llm, _ = gemini(erreur_api(code, statut))
    with pytest.raises(LLMMissingKeyError):
        await llm.complete(MESSAGES, module="cours")


async def test_cle_invalide_signalee_en_400():
    llm, _ = gemini(erreur_api(400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key."))
    with pytest.raises(LLMMissingKeyError):
        await llm.complete(MESSAGES, module="cours")


async def test_modele_introuvable_404():
    llm, _ = gemini(erreur_api(404, "NOT_FOUND"))
    with pytest.raises(LLMMissingKeyError) as info:
        await llm.complete(MESSAGES, module="cours")
    assert "LLM_MODEL" in info.value.message


async def test_requete_refusee_400():
    llm, _ = gemini(erreur_api(400, "INVALID_ARGUMENT", "Thinking level not supported"))
    with pytest.raises(LLMError) as info:
        await llm.complete(MESSAGES, module="cours")
    assert isinstance(info.value, RequeteRefuseeError)


async def test_delai_depasse():
    async def lent():
        await asyncio.sleep(5)

    llm, _ = gemini(lent, timeout_seconds=0.05)
    with pytest.raises(LLMTimeoutError):
        await llm.complete(MESSAGES, module="cours")


async def test_surcharge_503_retentee_puis_reussie():
    llm, modeles = gemini(erreur_api(503, "UNAVAILABLE"), erreur_api(503, "UNAVAILABLE"), reponse("enfin"))
    assert (await llm.complete(MESSAGES, module="cours")).text == "enfin"
    assert len(modeles.appels) == 3


async def test_surcharge_503_persistante():
    llm, modeles = gemini(*[erreur_api(503, "UNAVAILABLE")] * 3)
    with pytest.raises(LLMError) as info:
        await llm.complete(MESSAGES, module="cours")
    assert type(info.value) is LLMError
    assert len(modeles.appels) == 3


# ---------------------------------------------------------------- secrets


async def test_la_cle_n_apparait_ni_dans_les_logs_ni_dans_les_erreurs(caplog):
    caplog.set_level(logging.DEBUG)
    llm, _ = gemini(erreur_api(429, "RESOURCE_EXHAUSTED"), reponse("Bonjour"), erreur_api(403, "PERMISSION_DENIED"))
    with pytest.raises(LLMQuotaError) as quota:
        await llm.complete(MESSAGES, module="cours")
    await llm.complete(MESSAGES, module="cours")
    with pytest.raises(LLMMissingKeyError) as refus:
        await llm.complete(MESSAGES, module="cours")
    assert CLE_SECRETE not in caplog.text
    assert CLE_SECRETE not in quota.value.message and CLE_SECRETE not in refus.value.message
    assert "Bonjour" not in caplog.text  # ni le contenu des échanges


# ---------------------------------------------------------------- complete_with_image()


async def test_complete_with_image_joint_l_image_au_dernier_message_utilisateur():
    llm, modeles = gemini(reponse("Un schéma."))
    resultat = await llm.complete_with_image(MESSAGES, PNG, "image/png", module="cours")
    assert resultat.text == "Un schéma."
    parts = modeles.appels[0]["contents"][-1].parts
    assert parts[0].text == "Bonjour"
    assert parts[1].inline_data.mime_type == "image/png" and parts[1].inline_data.data == PNG


@pytest.mark.parametrize("image,mime", [(PNG, "image/gif"), (b"", "image/png"), (PNG, "application/pdf")])
async def test_complete_with_image_refuse_les_entrees_invalides(image, mime):
    llm, modeles = gemini()
    with pytest.raises(LLMInvalidResponseError):
        await llm.complete_with_image(MESSAGES, image, mime, module="cours")
    assert modeles.appels == []


async def test_le_client_stub_implemente_aussi_l_interface():
    resultat = await StubLLMClient().complete_with_image(MESSAGES, PNG, "image/png", module="cours")
    assert resultat.stub is True and "[STUB]" in resultat.text


# ---------------------------------------------------------------- construction


def test_build_gemini_exige_une_cle_et_un_modele():
    with pytest.raises(LLMMissingKeyError):
        build_llm_client(Settings(_env_file=None, llm_provider="gemini", llm_model="m"))
    with pytest.raises(LLMMissingKeyError) as info:
        build_llm_client(Settings(_env_file=None, llm_provider="gemini", llm_api_key=CLE_SECRETE))
    assert "LLM_MODEL" in info.value.message
    client = build_llm_client(Settings(_env_file=None, llm_provider="gemini", llm_api_key=CLE_SECRETE, llm_model="m"))
    assert isinstance(client, GeminiLLMClient)


def test_build_fournisseur_inconnu_ou_stub():
    assert isinstance(build_llm_client(Settings(_env_file=None, llm_provider="stub")), StubLLMClient)
    with pytest.raises(LLMMissingKeyError):
        build_llm_client(Settings(_env_file=None, llm_provider="inconnu", llm_api_key=CLE_SECRETE))


def test_la_cle_n_est_pas_affichee_par_les_settings():
    settings = Settings(_env_file=None, llm_provider="gemini", llm_api_key=CLE_SECRETE, llm_model="m")
    assert CLE_SECRETE not in repr(settings) and CLE_SECRETE not in str(settings)
