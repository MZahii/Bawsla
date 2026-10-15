"""Délais de la fiche : 504 et délai dépassé sans relance (bascule directe sur le secours), une relance sur les autres 5xx, budget global,
messages et codes distincts. Faux SDK et faux LLM : aucun appel réel."""

import asyncio

import pytest

from app.core.config import Settings
from app.core.errors import LLMError, LLMTimeoutError
from app.core.llm_client import GeminiLLMClient, LLMMessage, LLMRoutage
from app.main import app
from app.modules.cours.clients_llm import MESSAGE_TROP_LENT, GeminiSans504, client_dedie, construire_client, principal_seul
from app.modules.cours.router import get_llm_fiche
from tests.core.test_gemini_client import CLE_SECRETE, erreur_api, faux_client, reponse
from tests.core.test_openai_secours import FauxChat, client_openai, reponse_chat
from tests.cours.conftest import COURS, UTILISATEUR, FauxLLM

MESSAGES = [LLMMessage(role="system", content="Tu es utile."), LLMMessage(role="user", content="Bonjour")]
FICHE_JSON = (
    '{"resume": "Une jointure relie deux tables.", "motsCles": ["jointure"], "notions": ['
    '{"titre": "INNER JOIN", "definition": "Garde les lignes en correspondance.", '
    '"extraitSource": "ne garde que les lignes qui ont une correspondance dans les deux tables"}]}'
)


def gemini_sans_504(*scenario, **options) -> tuple[GeminiSans504, object]:
    client, modeles = faux_client(*scenario)
    options.setdefault("attente_entre_tentatives", 0)
    return GeminiSans504(CLE_SECRETE, "gemini-test", client=client, **options), modeles


# ---------------------------------------------------------------- relances


async def test_504_deadline_exceeded_sans_aucune_relance():
    llm, modeles = gemini_sans_504(erreur_api(504, "DEADLINE_EXCEEDED"), reponse("jamais appelé"))
    with pytest.raises(LLMTimeoutError):
        await llm.complete(MESSAGES, module="cours")
    assert len(modeles.appels) == 1  # un seul appel, aucune relance


async def test_delai_depasse_sans_relance():
    async def lent():
        await asyncio.sleep(5)

    llm, modeles = gemini_sans_504(lent, reponse("jamais appelé"), timeout_seconds=0.05)
    with pytest.raises(LLMTimeoutError):
        await llm.complete(MESSAGES, module="cours")
    assert len(modeles.appels) == 1


async def test_autre_5xx_une_seule_relance():
    llm, modeles = gemini_sans_504(erreur_api(503, "UNAVAILABLE"), reponse("ok après une relance"))
    assert (await llm.complete(MESSAGES, module="cours")).text == "ok après une relance"
    assert len(modeles.appels) == 2

    llm, modeles = gemini_sans_504(erreur_api(503, "UNAVAILABLE"), erreur_api(503, "UNAVAILABLE"), reponse("jamais"))
    with pytest.raises(LLMError) as info:
        await llm.complete(MESSAGES, module="cours")
    assert type(info.value) is LLMError and len(modeles.appels) == 2  # une relance seulement, pas deux


# ---------------------------------------------------------------- bascule directe sur le secours


async def test_504_bascule_directement_sur_le_secours():
    principal, modeles = gemini_sans_504(erreur_api(504, "DEADLINE_EXCEEDED"))
    secours, faux = client_openai([reponse_chat('{"titre": "de secours"}')])
    llm = LLMRoutage(principal, secours)
    resultat = await llm.complete(MESSAGES, module="cours")
    assert (resultat.text, resultat.fournisseur) == ('{"titre": "de secours"}', "openai")
    assert len(modeles.appels) == 1 and len(faux.appels) == 1  # Gemini une fois, puis le secours


async def test_delai_depasse_bascule_directement_sur_le_secours():
    async def lent():
        await asyncio.sleep(5)

    principal, modeles = gemini_sans_504(lent, timeout_seconds=0.05)
    secours, faux = client_openai([reponse_chat("réponse du secours")])
    resultat = await LLMRoutage(principal, secours).complete(MESSAGES, module="cours")
    assert resultat.fournisseur == "openai" and len(modeles.appels) == 1


# ---------------------------------------------------------------- client dédié (120 s) sans toucher au client commun


def reglages(**valeurs) -> Settings:
    base = {"llm_provider": "gemini", "llm_api_key": CLE_SECRETE, "llm_model": "gemini-test", "llm_timeout_seconds": 30, "llm_secours": ""}
    return Settings(_env_file=None, **{**base, **valeurs})


def test_client_de_la_fiche_a_son_propre_delai():
    reglage = reglages()
    commun = GeminiLLMClient(CLE_SECRETE, "gemini-test", timeout_seconds=30, client=object())

    dedie = client_dedie(commun, reglage, 120.0)

    assert isinstance(dedie, GeminiSans504) and dedie is not commun
    assert dedie._timeout == 120.0 and commun._timeout == 30 and reglage.llm_timeout_seconds == 30  # LLM_TIMEOUT_SECONDS inchangé
    assert dedie.MAX_TENTATIVES == 2
    assert client_dedie(commun, reglage, 120.0) is dedie  # un seul exemplaire réutilisé


def test_client_de_la_fiche_garde_le_secours_avec_le_meme_delai():
    reglage = reglages(llm_secours="openai", openai_api_key="sk-factice", openai_model="gpt-test")
    client = construire_client(reglage, 120.0)
    assert isinstance(client, LLMRoutage) and isinstance(client.principal, GeminiSans504)
    assert client.principal._timeout == 120.0 and client.secours._timeout == 120.0
    assert principal_seul(client) is client.principal


def test_client_non_gemini_renvoye_tel_quel():
    faux = FauxLLM()
    assert client_dedie(faux, reglages(), 120.0) is faux


def test_valeurs_par_defaut_des_delais():
    from app.modules.cours.config import CoursAiSettings

    r = CoursAiSettings(_env_file=None)
    assert (r.fiche_timeout_seconds, r.fiche_budget_seconds) == (120.0, 300.0)


# ---------------------------------------------------------------- route : budget global, message et code distincts


class LLMLent(FauxLLM):
    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        await asyncio.sleep(5)


def test_budget_global_depasse_504_avec_message_et_code_distincts(client, monkeypatch):
    monkeypatch.setenv("FICHE_BUDGET_SECONDS", "0.05")
    from app.modules.cours.config import get_cours_ai_settings

    get_cours_ai_settings.cache_clear()
    app.dependency_overrides[get_llm_fiche] = lambda: LLMLent()
    r = client.post("/api/ai/cours/fiche", json={"coursId": 1, "texte": COURS}, headers=UTILISATEUR)
    assert r.status_code == 504
    corps = r.json()
    assert corps["message"] == MESSAGE_TROP_LENT == "L'IA met trop de temps à répondre, réessaie dans un instant"
    assert corps["error"] == "LLM_TIMEOUT"


def test_delai_d_un_appel_depasse_meme_message(client):
    class TropLent(FauxLLM):
        async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
            raise LLMTimeoutError("Le fournisseur LLM n'a pas répondu à temps")

    app.dependency_overrides[get_llm_fiche] = lambda: TropLent()
    r = client.post("/api/ai/cours/fiche", json={"coursId": 1, "texte": COURS}, headers=UTILISATEUR)
    assert (r.status_code, r.json()["message"], r.json()["error"]) == (504, MESSAGE_TROP_LENT, "LLM_TIMEOUT")


def test_fiche_normale_dans_le_budget(client, avec_llm):
    from tests.cours.test_routes_cours import fiche_fidele

    avec_llm(FauxLLM(fiche_fidele()))
    r = client.post("/api/ai/cours/fiche", json={"coursId": 1, "texte": COURS}, headers=UTILISATEUR)
    assert r.status_code == 200 and r.json()["data"]["resume"]
