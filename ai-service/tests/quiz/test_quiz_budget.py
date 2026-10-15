"""Budget de jetons (secours Groq, ~8 000 par minute) : lots réduits, refus d'avance si rien ne tient."""

import pytest

from app.core.llm_client import estimer_tokens
from app.modules.quiz.config import get_quiz_ai_settings
from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, question, repondre

URL = "/api/ai/quiz/generer"


def _budget(monkeypatch, jetons: int) -> None:
    monkeypatch.setenv("QUIZ_BUDGET_TOKENS", str(jetons))
    get_quiz_ai_settings.cache_clear()


def _poster(client, **modif):
    return client.post(URL, json={**DEMANDE, **modif}, headers=UTILISATEUR)


def test_budget_par_defaut_tous_les_appels_tiennent_dans_8000_jetons(client, avec_llm):
    lot1 = generation(*(question(i) for i in range(1, 6)))
    lot2 = generation(*(question(i) for i in range(6, 11)))
    faux = avec_llm(FauxLLM(lot1, lot2, repondre()))

    _poster(client, nbQuestions=10, niveauxBloom=["MEMORISER", "COMPRENDRE", "APPLIQUER", "ANALYSER", "EVALUER", "CREER"])

    for appel in faux.appels:
        prompt = estimer_tokens("".join(m.content for m in appel["messages"]))
        assert prompt + appel["max_tokens"] + 400 <= 8000


def test_budget_serre_reduit_la_taille_des_lots(client, avec_llm, monkeypatch):
    _budget(monkeypatch, 3500)  # prompt v2 (échelle de difficulté) : lots de 2
    reponses = [generation(question(i), question(i + 1)) for i in (1, 3)] + [generation(question(5))]
    faux = avec_llm(FauxLLM(*reponses, repondre(), repondre()))

    data = _poster(client, nbQuestions=5).json()["data"]

    generations = [a for a in faux.appels if a["schema"].__name__ == "GenerationLlm"]
    assert len(generations) == 3  # lots de 2 au lieu de 5
    assert data["nbValidees"] == 5
    for appel in faux.appels:
        assert estimer_tokens("".join(m.content for m in appel["messages"])) + appel["max_tokens"] + 400 <= 3500


def test_budget_trop_petit_413_sans_appel(client, avec_llm, monkeypatch):
    _budget(monkeypatch, 500)
    faux = avec_llm(FauxLLM())

    r = _poster(client)

    assert r.status_code == 413
    assert r.json()["error"] == "LLM_TOO_LONG_FOR_FALLBACK"
    assert faux.appels == []


@pytest.mark.parametrize(("budget", "nb_generations", "nb_verifications"), [(2900, 2, 2), (8000, 1, 1)])
def test_verification_decoupee_seulement_si_necessaire(client, avec_llm, monkeypatch, budget, nb_generations, nb_verifications):
    _budget(monkeypatch, budget)
    longue = "Pourquoi cette jointure renvoie-t-elle ces lignes ? " + "Contexte détaillé. " * 250
    qs = [question(i, enonce=f"{i}) {longue}") for i in range(1, 3)]
    lots = [generation(*qs)] if nb_generations == 1 else [generation(qs[0]), generation(qs[1])]
    faux = avec_llm(FauxLLM(*lots, repondre(), repondre()))

    data = _poster(client).json()["data"]

    noms = [a["schema"].__name__ for a in faux.appels]
    assert noms.count("GenerationLlm") == nb_generations
    assert noms.count("VerificationLlm") == nb_verifications
    assert data["nbValidees"] == 2
    for appel in faux.appels:
        assert estimer_tokens("".join(m.content for m in appel["messages"])) + appel["max_tokens"] + 400 <= budget
