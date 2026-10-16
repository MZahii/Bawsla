"""Difficulté à 5 niveaux (prompt v2) : chaque niveau change réellement ce qui est demandé au LLM."""

import itertools

import pytest

from app.modules.quiz.config import get_quiz_ai_settings
from app.modules.quiz.generation import Objectifs, max_tokens_generation, taille_lot
from app.modules.quiz.prompts import prompt_generation
from app.modules.quiz.prompts.difficultes import GRILLE
from app.modules.quiz.schemas import Difficulte, NiveauBloom
from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, question, repondre

URL = "/api/ai/quiz/generer"
NIVEAUX = [d.value for d in Difficulte]


def _generer(client, avec_llm, difficulte: str) -> tuple[dict, dict]:
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))
    reponse = client.post(URL, json={**DEMANDE, "difficulte": difficulte}, headers=UTILISATEUR)
    return reponse.json(), faux.appels[0]


def test_les_cinq_niveaux_dans_l_ordre():
    assert NIVEAUX == ["FACILE", "INTERMEDIAIRE", "AVANCE", "DIFFICILE", "MASTER"]


@pytest.mark.parametrize("difficulte", NIVEAUX)
def test_chaque_niveau_est_accepte_et_renvoye(client, avec_llm, difficulte):
    corps, _ = _generer(client, avec_llm, difficulte)

    assert corps["success"] is True
    assert corps["data"]["difficulte"] == difficulte


def test_moyen_reste_accepte_comme_intermediaire(client, avec_llm):
    """Compatibilité avec l'échelle à 3 niveaux (contrat initial)."""
    corps, _ = _generer(client, avec_llm, "MOYEN")

    assert corps["data"]["difficulte"] == "INTERMEDIAIRE"


def test_sans_difficulte_le_niveau_par_defaut_est_intermediaire(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))
    demande = {k: v for k, v in DEMANDE.items() if k != "difficulte"}

    assert client.post(URL, json=demande, headers=UTILISATEUR).json()["data"]["difficulte"] == "INTERMEDIAIRE"


@pytest.mark.parametrize("difficulte", NIVEAUX)
def test_le_prompt_porte_la_consigne_du_seul_niveau_demande(client, avec_llm, difficulte):
    _, appel = _generer(client, avec_llm, difficulte)
    systeme, utilisateur = (m.content for m in appel["messages"])

    demande = GRILLE[Difficulte(difficulte)]
    assert f"Difficulté : {demande.nom}" in utilisateur
    assert demande.consigne in utilisateur
    for autre in GRILLE.values():
        assert autre.consigne in systeme  # l'échelle complète sert de repère
        if autre is not demande:
            assert autre.consigne not in utilisateur


def test_les_cinq_prompts_sont_deux_a_deux_differents(client, avec_llm):
    prompts = {d: _generer(client, avec_llm, d)[1]["messages"][-1].content for d in NIVEAUX}

    for a, b in itertools.combinations(NIVEAUX, 2):
        assert prompts[a] != prompts[b], (a, b)


def test_la_sortie_autorisee_croit_avec_le_niveau(client, avec_llm):
    """Un niveau plus élevé demande des énoncés plus riches : plus de jetons de sortie par question."""
    sorties = [_generer(client, avec_llm, d)[1]["max_tokens"] for d in NIVEAUX]

    assert sorties == sorted(sorties) and len(set(sorties)) == 5
    assert sorties == [max_tokens_generation(2, d) for d in NIVEAUX]


def test_budget_serre_lots_plus_petits_en_master():
    """Sous le budget du secours Groq, MASTER tient moins de questions par appel que FACILE."""
    prompt = prompt_generation("v2")

    def lot(difficulte: str) -> int:
        obj = Objectifs("Jointures SQL", ["Écrire une jointure interne"], [NiveauBloom.APPLIQUER], difficulte)
        return taille_lot(prompt, obj, 10, 6000, 400)

    lots = [lot(d) for d in NIVEAUX]
    assert lots == sorted(lots, reverse=True)
    assert lots[0] > lots[-1]


def test_prompt_v1_reste_utilisable_avec_les_nouveaux_niveaux(client, avec_llm, monkeypatch):
    """v1 ignore la consigne détaillée mais accepte les 5 niveaux (comparaison v1 / v2 dans le journal)."""
    monkeypatch.setenv("PROMPT_QUIZ_VERSION", "v1")
    get_quiz_ai_settings.cache_clear()

    corps, appel = _generer(client, avec_llm, "MASTER")

    assert corps["data"]["versionPrompt"] == "v1"
    utilisateur = appel["messages"][-1].content
    assert "Difficulté : master (5 sur 5)" in utilisateur
    assert GRILLE[Difficulte.MASTER].consigne not in utilisateur
