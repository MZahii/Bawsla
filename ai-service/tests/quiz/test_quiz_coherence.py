"""Self-Consistency : passes de vérification sans les réponses, choix mélangés avec une graine fixe."""

from app.modules.quiz.coherence import melanger
from app.modules.quiz.schemas import ChoixQcm, QuestionQcm, StatutQuestion
from tests.quiz.conftest import DEMANDE, UTILISATEUR, FauxLLM, generation, lire_qcm, question, repondre

URL = "/api/ai/quiz/generer"


def _poster(client, **modif):
    return client.post(URL, json={**DEMANDE, **modif}, headers=UTILISATEUR)


def test_appel_de_verification_a_la_temperature_reglee(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    _poster(client)

    verif = faux.appels[1]
    assert verif["schema"].__name__ == "VerificationLlm"
    assert verif["temperature"] == 0.7
    assert sorted(lire_qcm(verif["messages"])) == [1, 2]


def test_la_verification_ne_recoit_ni_reponse_ni_erreur_ni_explication(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    _poster(client)

    envoye = "\n".join(m.content for m in faux.appels[1]["messages"])
    for interdit in ("estCorrecte", "Confond INNER et LEFT", "Oublie la clause ON", "jointure interne ne garde", "true"):
        assert interdit not in envoye
    assert "Bonne réponse 1" in envoye  # le choix est là, mais rien ne le distingue


def test_desaccord_rejette_avec_les_deux_choix_dans_la_raison(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre(fausses={"Question 2 sur les jointures ?"})))

    data = _poster(client).json()["data"]

    ok, rejetee = data["questions"]
    assert ok["statut"] == "VALIDEE"
    assert rejetee["statut"] == "REJETEE"
    assert rejetee["raisonRejet"].startswith("Désaccord à la vérification : « Distracteur 2-")
    assert "choisi au lieu de « Bonne réponse 2 »" in rejetee["raisonRejet"]
    assert "Raisonnement court." in rejetee["raisonRejet"]
    assert rejetee["raisonRejet"].endswith("(vérification 1/1)")
    assert data["nbValidees"] == 1 and data["nbRejetees"] == 1


def test_reponse_absente_ou_illisible_rejetee(client, avec_llm):
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre(sans_reponse={"Question 1 sur les jointures ?"})))
    q1 = _poster(client).json()["data"]["questions"][0]
    assert q1["statut"] == "REJETEE"
    assert q1["raisonRejet"] == "La vérification n'a pas répondu à cette question (vérification 1/1)"

    illisible = {"reponses": [{"numero": 1, "choix": "Z"}, {"numero": 2, "choix": "Z"}]}
    from app.modules.quiz.schemas import VerificationLlm

    avec_llm(FauxLLM(generation(question(1), question(2)), VerificationLlm.model_validate(illisible)))
    q1 = _poster(client).json()["data"]["questions"][0]
    assert q1["raisonRejet"].startswith("Réponse de vérification illisible")


def test_choix_renvoyes_dans_l_ordre_melange_de_la_verification(client, avec_llm):
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre()))

    data = _poster(client).json()["data"]

    _, choix_verifies = lire_qcm(faux.appels[1]["messages"])[1]
    assert [c["texte"] for c in data["questions"][0]["choix"]] == list(choix_verifies.values())


def _q(enonce: str) -> QuestionQcm:
    choix = [ChoixQcm(texte=f"C{i}", est_correcte=i == 0, erreur_associee=None if i == 0 else "e") for i in range(4)]
    return QuestionQcm(enonce=enonce, choix=choix, explication="x", statut=StatutQuestion.VALIDEE)


def test_melange_reproductible_et_varie():
    a = melanger(_q("Énoncé A"))
    assert [c.texte for c in melanger(_q("Énoncé A")).choix] == [c.texte for c in a.choix]
    ordres = {tuple(c.texte for c in melanger(_q(f"Énoncé {i}")).choix) for i in range(20)}
    assert len(ordres) > 1
    positions = {next(i for i, c in enumerate(melanger(_q(f"Énoncé {i}")).choix) if c.est_correcte) for i in range(20)}
    assert len(positions) > 1  # la bonne réponse n'est pas toujours au même endroit


def _deux_passes(monkeypatch):
    from app.modules.quiz.config import get_quiz_ai_settings

    monkeypatch.setenv("QUIZ_NB_VERIFICATIONS", "2")
    get_quiz_ai_settings.cache_clear()


def test_reglages_par_defaut_deux_passes_a_0_7(monkeypatch):
    from app.modules.quiz.config import QuizAiSettings

    monkeypatch.delenv("QUIZ_NB_VERIFICATIONS")
    monkeypatch.delenv("QUIZ_TEMPERATURE_VERIFICATION")
    reglages = QuizAiSettings(_env_file=None)
    assert reglages.quiz_nb_verifications == 2
    assert reglages.quiz_temperature_verification == 0.7


def test_deux_passes_toutes_d_accord(client, avec_llm, monkeypatch):
    _deux_passes(monkeypatch)
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre(), repondre()))

    data = _poster(client).json()["data"]

    assert data["nbValidees"] == 2
    assert [a["schema"].__name__ for a in faux.appels] == ["GenerationLlm", "VerificationLlm", "VerificationLlm"]
    assert all(a["temperature"] == 0.7 for a in faux.appels[1:])


def test_une_seule_passe_divergente_suffit_a_rejeter(client, avec_llm, monkeypatch):
    _deux_passes(monkeypatch)
    fausses = {"Question 2 sur les jointures ?"}
    avec_llm(FauxLLM(generation(question(1), question(2)), repondre(), repondre(fausses=fausses)))

    data = _poster(client).json()["data"]

    ok, rejetee = data["questions"]
    assert ok["statut"] == "VALIDEE"
    assert rejetee["statut"] == "REJETEE"
    assert rejetee["raisonRejet"].startswith("Désaccord à la vérification")
    assert rejetee["raisonRejet"].endswith("(vérification 2/2)")


def test_chaque_passe_a_ses_choix_et_l_ordre_renvoye_est_celui_de_la_premiere(client, avec_llm, monkeypatch):
    _deux_passes(monkeypatch)
    faux = avec_llm(FauxLLM(generation(question(1), question(2)), repondre(), repondre()))

    data = _poster(client).json()["data"]

    passe1, passe2 = (lire_qcm(faux.appels[k]["messages"]) for k in (1, 2))
    for n in (1, 2):
        assert passe1[n][0] == passe2[n][0]
        assert sorted(passe1[n][1].values()) == sorted(passe2[n][1].values())
        assert [c["texte"] for c in data["questions"][n - 1]["choix"]] == list(passe1[n][1].values())


def test_graine_differente_selon_la_passe():
    passe1 = [tuple(c.texte for c in melanger(_q(f"Énoncé {i}")).choix) for i in range(20)]
    passe2 = [tuple(c.texte for c in melanger(_q(f"Énoncé {i}"), 2).choix) for i in range(20)]
    assert passe1 != passe2
    assert passe2 == [tuple(c.texte for c in melanger(_q(f"Énoncé {i}"), 2).choix) for i in range(20)]
