"""Route POST /api/ai/quiz/generer : enveloppe, validation, erreurs du socle, fournisseur."""

import pytest

from app.core.errors import (
    LLMInvalidResponseError,
    LLMMissingKeyError,
    LLMQuotaError,
    LLMTimeoutError,
    LLMTropLongError,
)
from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, question, repondre

URL = "/api/ai/quiz/generer"


def test_generer_renvoie_l_enveloppe_et_le_fournisseur(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2, niveau="ANALYSER")), repondre(), fournisseur="gemini"))

    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.status_code == 200
    corps = r.json()
    assert corps["success"] is True
    data = corps["data"]
    assert data["coursId"] == 3
    assert data["nbValidees"] == 2 and data["nbRejetees"] == 0
    assert data["versionPrompt"] == "v2"
    assert data["difficulte"] == "INTERMEDIAIRE"
    assert data["fournisseur"] == "gemini"
    q = data["questions"][0]
    assert q["statut"] == "VALIDEE"
    assert "raisonRejet" not in q  # champs null omis
    assert len(q["choix"]) == 4 and sum(c["estCorrecte"] for c in q["choix"]) == 1
    bonne = next(c for c in q["choix"] if c["estCorrecte"])
    assert "erreurAssociee" not in bonne
    assert all(c["erreurAssociee"] for c in q["choix"] if not c["estCorrecte"])
    assert [a["module"] for a in faux.appels] == ["quiz", "quiz"]


def test_fournisseur_de_secours_signale(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre(), fournisseur="groq"))

    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.json()["data"]["fournisseur"] == "groq"


def test_sans_en_tetes_de_la_gateway_401(client, avec_llm):
    faux = avec_llm(FauxLLM())

    r = client.post(URL, json=DEMANDE)

    assert r.status_code == 401
    assert faux.appels == []


@pytest.mark.parametrize(
    "modification",
    [
        {"nbQuestions": 0},
        {"nbQuestions": 11},
        {"competences": []},
        {"competences": [f"Compétence {i}" for i in range(9)]},
        {"competences": ["x" * 151]},
        {"niveauxBloom": []},
        {"niveauxBloom": ["RETENIR"]},
        {"difficulte": "EXTREME"},
        {"theme": ""},
        {"theme": "x" * 201},
    ],
)
def test_corps_invalide_400(client, avec_llm, modification):
    faux = avec_llm(FauxLLM())

    r = client.post(URL, json={**DEMANDE, **modification}, headers=UTILISATEUR)

    assert r.status_code == 400
    assert r.json()["success"] is False
    assert faux.appels == []


@pytest.mark.parametrize(
    ("erreur", "statut", "code"),
    [
        (LLMQuotaError("quota"), 429, "LLM_QUOTA_EXCEEDED"),
        (LLMInvalidResponseError("json"), 502, "LLM_INVALID_RESPONSE"),
        (LLMMissingKeyError("clé"), 503, "LLM_MISSING_KEY"),
        (LLMTimeoutError("délai"), 504, "LLM_TIMEOUT"),
        (LLMTropLongError("trop long"), 413, "LLM_TOO_LONG_FOR_FALLBACK"),
    ],
)
def test_erreurs_du_llm_traduites_par_le_socle(client, avec_llm, erreur, statut, code):
    avec_llm(FauxLLM(erreur))

    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.status_code == statut
    assert r.json()["error"] == code


def test_erreur_pendant_la_verification_remonte_aussi(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), LLMQuotaError("quota")))

    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.status_code == 429


def test_fournisseur_stub_sans_cle_503(client):
    """Sans faux LLM : le fournisseur `stub` refuse les appels JSON (aucun appel réseau)."""
    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.status_code == 503
    assert r.json()["error"] == "LLM_MISSING_KEY"


def test_apprenant_refuse_403(client, avec_llm):
    faux = avec_llm(FauxLLM())

    r = client.post(URL, json=DEMANDE, headers={"X-User-Id": "7", "X-User-Role": "ETUDIANT"})

    assert r.status_code == 403
    assert r.json()["success"] is False
    assert faux.appels == []  # aucun quota consommé


def test_admin_autorise(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    r = client.post(URL, json=DEMANDE, headers={"X-User-Id": "1", "X-User-Role": "ADMIN"})

    assert r.status_code == 200


def test_fournisseur_par_defaut_sans_secours(client, avec_llm):
    """Sans LLMRoutage, fournisseur_courant() est vide : on reprend LLM_PROVIDER."""
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    r = client.post(URL, json=DEMANDE, headers=UTILISATEUR)

    assert r.json()["data"]["fournisseur"] == "stub"
