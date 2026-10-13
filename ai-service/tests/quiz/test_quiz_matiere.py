"""Matière du cours (Mathématiques, Histoire, « Autre » précisée…) : facultative, nettoyée, injectée dans le prompt v2."""

import pytest

from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, question, repondre

URL = "/api/ai/quiz/generer"


def _generer(client, avec_llm, **champs):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))
    reponse = client.post(URL, json={**DEMANDE, **champs}, headers=UTILISATEUR)
    return reponse, faux


def _prompt(faux) -> str:
    return faux.appels[0]["messages"][-1].content


@pytest.mark.parametrize("matiere", ["Mathématiques", "Histoire", "Psychologie", "Œnologie"])
def test_la_matiere_est_dans_le_prompt(client, avec_llm, matiere):
    reponse, faux = _generer(client, avec_llm, matiere=matiere)

    assert reponse.status_code == 200
    assert f"Matière : {matiere}\n" in _prompt(faux)


@pytest.mark.parametrize("champs", [{}, {"matiere": None}, {"matiere": "   "}])
def test_sans_matiere_le_prompt_dit_non_precisee(client, avec_llm, champs):
    reponse, faux = _generer(client, avec_llm, **champs)

    assert reponse.status_code == 200
    assert "Matière : non précisée\n" in _prompt(faux)


def test_matiere_trop_longue_400(client, avec_llm):
    reponse, _ = _generer(client, avec_llm, matiere="x" * 81)

    assert reponse.status_code == 400
    assert "matiere" in reponse.json()["details"]


def test_email_dans_la_matiere_masque_et_signale(client, avec_llm):
    reponse, faux = _generer(client, avec_llm, matiere="Histoire jean.dupont@example.com")

    assert reponse.status_code == 200
    assert "jean.dupont@example.com" not in _prompt(faux)
    assert any("masquées" in a for a in reponse.json()["data"]["alertes"])
