"""Génération : Few-Shot par niveau demandé, validation stricte question par question, lots."""

import pytest

from app.modules.quiz.prompts.exemples_bloom import EXEMPLES
from app.modules.quiz.schemas import NiveauBloom
from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, question, repondre

URL = "/api/ai/quiz/generer"


def _poster(client, **modif):
    return client.post(URL, json={**DEMANDE, **modif}, headers=UTILISATEUR)


def test_few_shot_uniquement_pour_les_niveaux_demandes(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    _poster(client)

    prompt = faux.appels[0]["messages"][-1].content
    for niveau in NiveauBloom:
        present = [ex["enonce"] in prompt for ex in EXEMPLES[niveau]]
        attendu = niveau in (NiveauBloom.APPLIQUER, NiveauBloom.ANALYSER)
        assert present == [attendu, attendu], niveau


def test_prompt_de_generation(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    _poster(client)

    appel = faux.appels[0]
    systeme, utilisateur = (m.content for m in appel["messages"])
    assert "exactement 4 choix" in systeme and "erreurAssociee" in systeme
    assert "Jointures SQL" in utilisateur and "- Écrire une jointure interne" in utilisateur
    assert "APPLIQUER, ANALYSER" in utilisateur and "moyenne" in utilisateur
    assert "Écris exactement 2 questions" in utilisateur
    assert appel["temperature"] == pytest.approx(0.4)
    assert appel["schema"].__name__ == "GenerationLlm"


@pytest.mark.parametrize(
    ("q", "raison"),
    [
        (question(1, enonce=" "), "énoncé manquant"),
        (question(1, choix=question(1)["choix"][:3]), "3 choix au lieu de 4"),
        (
            question(1, choix=[{**c, "estCorrecte": True} for c in question(1)["choix"][:2]] + question(1)["choix"][2:]),
            "2 choix corrects au lieu de 1",
        ),
        (
            question(1, choix=[{**c, "estCorrecte": False} for c in question(1)["choix"]]),
            "0 choix corrects au lieu de 1",
        ),
        (
            question(1, choix=question(1)["choix"][:3] + [{"texte": "Sans erreur", "estCorrecte": False}]),
            "erreurAssociee manquante pour « Sans erreur »",
        ),
        (question(1, choix=question(1)["choix"][:3] + [{**question(1)["choix"][1]}]), "choix en double"),
        (question(1, explication=""), "explication manquante"),
        (question(1, niveau="CREER"), "niveau de Bloom non demandé (CREER)"),
        (question(1, niveau="INVENTE"), "niveau de Bloom non demandé (INVENTE)"),
        (question(1, competence="Cuisiner"), "compétence non demandée (Cuisiner)"),
    ],
)
def test_question_mal_formee_rejetee_sans_perdre_le_lot(client, avec_llm, q, raison):
    faux = avec_llm(FauxLLM(generation(q, question(2)), repondre()))

    data = _poster(client).json()["data"]

    rejetee, validee = data["questions"]
    assert rejetee["statut"] == "REJETEE"
    assert rejetee["raisonRejet"] == f"Format invalide : {raison}"
    assert validee["statut"] == "VALIDEE"
    assert data["nbValidees"] == 1 and data["nbRejetees"] == 1
    # seule la question bien formée part à la vérification
    assert len(faux.appels) == 2
    assert "Question 1" not in faux.appels[1]["messages"][-1].content


def test_competence_reconnue_sans_tenir_compte_de_la_casse(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1, competence="écrire une JOINTURE interne"), question(2)), repondre()))

    q = _poster(client).json()["data"]["questions"][0]

    assert q["statut"] == "VALIDEE"
    assert q["competence"] == "Écrire une jointure interne"


def test_questions_en_trop_ignorees_et_manquantes_signalees(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2), question(3)), repondre()))
    assert len(_poster(client).json()["data"]["questions"]) == 2

    avec_llm(FauxLLM(generation(question(1)), repondre()))
    data = _poster(client).json()["data"]
    assert len(data["questions"]) == 1
    assert any("1 question(s) au lieu de 2" in a for a in data["alertes"])


def test_lot_vide_aucune_verification(client, avec_llm):
    faux = avec_llm(FauxLLM(generation()))

    data = _poster(client).json()["data"]

    assert data["questions"] == [] and data["nbValidees"] == 0
    assert len(faux.appels) == 1


def test_dix_questions_en_deux_lots_puis_une_verification(client, avec_llm):
    lot1 = generation(*(question(i) for i in range(1, 6)))
    lot2 = generation(*(question(i) for i in range(6, 11)))
    faux = avec_llm(FauxLLM(lot1, lot2, repondre()))

    data = _poster(client, nbQuestions=10).json()["data"]

    assert data["nbValidees"] == 10
    assert [a["schema"].__name__ for a in faux.appels] == ["GenerationLlm", "GenerationLlm", "VerificationLlm"]
    second = faux.appels[1]["messages"][-1].content
    assert "Écris exactement 5 questions" in second
    assert "- Question 1 sur les jointures ?" in second  # rappel des énoncés déjà posés
    assert "déjà posés" not in faux.appels[0]["messages"][-1].content


def test_lot_max_configurable(client, avec_llm, monkeypatch):
    from app.modules.quiz.config import get_quiz_ai_settings

    monkeypatch.setenv("QUIZ_LOT_MAX", "1")
    get_quiz_ai_settings.cache_clear()
    faux = avec_llm(FauxLLM(generation(question(1)), generation(question(2)), repondre()))

    data = _poster(client).json()["data"]

    assert data["nbValidees"] == 2
    assert len(faux.appels) == 3


def test_doublon_rejete(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2, enonce="question 1  SUR les jointures ?")), repondre()))

    rejetee = _poster(client).json()["data"]["questions"][1]

    assert rejetee["statut"] == "REJETEE"
    assert rejetee["raisonRejet"] == "Doublon d'une question déjà générée"


REFORMULEE = (
    "Vous devez choisir entre un tri rapide non stable et un tri fusion stable pour trier des enregistrements "
    "dont la date de création doit rester ordonnée. Quel algorithme choisissez-vous et quel piège devez-vous éviter ?"
)


def test_quasi_doublon_rejete(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1, enonce=REFORMULEE), question(2, enonce=REFORMULEE.replace("tri rapide", "quick sort"))), repondre()))

    rejetee = _poster(client).json()["data"]["questions"][1]

    assert rejetee["statut"] == "REJETEE"
    assert rejetee["raisonRejet"] == "Doublon d'une question déjà générée"


def test_questions_voisines_mais_distinctes_gardees(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1, enonce="Quel tri est stable ?"), question(2, enonce="Quel tri est en place ?")), repondre()))

    questions = _poster(client).json()["data"]["questions"]

    assert [q["statut"] for q in questions] == ["VALIDEE", "VALIDEE"]


def test_version_de_prompt_inconnue_repli_sur_la_version_par_defaut(client, avec_llm, monkeypatch):
    from app.modules.quiz.config import get_quiz_ai_settings
    from app.modules.quiz.prompts import VERSION_PAR_DEFAUT

    monkeypatch.setenv("PROMPT_QUIZ_VERSION", "v9")
    get_quiz_ai_settings.cache_clear()
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    assert _poster(client).json()["data"]["versionPrompt"] == VERSION_PAR_DEFAUT == "v3"


def test_champs_null_rejettent_la_question_sans_perdre_le_lot(client, avec_llm):
    """JSON brut du LLM avec des null : seule la question concernée est rejetée (pas de 502)."""
    nulle = question(2)
    nulle["competence"] = None
    nulle["choix"][1]["estCorrecte"] = None
    nulle["choix"][2]["erreurAssociee"] = None
    avec_llm(FauxLLM({"questions": [question(1), nulle]}, repondre()))

    r = _poster(client)

    assert r.status_code == 200
    data = r.json()["data"]
    assert data["nbValidees"] == 1
    assert data["questions"][1]["raisonRejet"].startswith("Format invalide : erreurAssociee manquante")


def test_choix_null_et_niveau_absent(client, avec_llm):
    vide = question(2)
    vide["choix"] = None
    vide["niveauBloom"] = None
    avec_llm(FauxLLM({"questions": [question(1), vide]}, repondre()))

    rejetee = _poster(client).json()["data"]["questions"][1]

    assert rejetee["raisonRejet"] == "Format invalide : 0 choix au lieu de 4"


def test_niveau_normalise(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1, niveau=" appliquer "), question(2, niveau="Analyser")), repondre()))

    data = _poster(client).json()["data"]

    assert data["nbValidees"] == 2
    assert [q["niveauBloom"] for q in data["questions"]] == ["APPLIQUER", "ANALYSER"]


def test_question_rejetee_ne_bloque_pas_sa_reprise(client, avec_llm):
    """Une question mal formée ne compte pas pour les doublons : sa version correcte est retenue."""
    mal_formee = question(1, explication="")
    avec_llm(FauxLLM(generation(mal_formee, question(1)), repondre()))

    data = _poster(client).json()["data"]

    assert [q["statut"] for q in data["questions"]] == ["REJETEE", "VALIDEE"]
