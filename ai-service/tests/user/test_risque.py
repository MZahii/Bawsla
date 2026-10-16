"""Random Forest du risque d'échec : évaluation honnête, meilleur que les références, explications lisibles."""

import pandas as pd
import pytest

from app.modules.user.ml import risque
from app.modules.user.ml.donnees_simulees import generer


@pytest.fixture(scope="module")
def apprenants() -> pd.DataFrame:
    return generer()[0]


@pytest.fixture(scope="module")
def modele(apprenants):
    return risque.entrainer(apprenants)


def test_decoupage_entrainement_test(modele, apprenants):
    m = modele.metriques
    assert m["nbEntrainement"] + m["nbTest"] == len(apprenants)
    assert m["nbTest"] == round(len(apprenants) * risque.PART_TEST)


def test_aucune_fuite_de_la_verite_terrain():
    """Ni la cible, ni le profil caché, ni l'identifiant ne sont des entrées du modèle."""
    assert not {"echec", "profilCache", "apprenant"} & set(risque.VARIABLES)


def test_meilleur_que_la_classe_majoritaire(modele):
    comp = modele.metriques["comparaison"]
    assert comp["randomForest"]["validationCroisee"]["f1"] > comp["classeMajoritaire"]["validationCroisee"]["f1"] + 0.5
    assert comp["randomForest"]["validationCroisee"]["auc"] > 0.75


def test_performances_sur_le_test(modele):
    t = modele.metriques["test"]
    assert t["accuracy"] >= 0.75
    assert t["auc"] >= 0.75
    # Outil d'alerte : il doit repérer la majorité des apprenants qui échouent.
    assert t["rappel"] >= 0.6
    assert sum(map(sum, t["matriceConfusion"])) == modele.metriques["nbTest"]


def test_seuil_choisi_pour_le_rappel(modele):
    assert modele.seuil in list(risque.SEUILS_TESTES)
    # Le seuil retenu repère au moins autant d'échecs que le seuil standard 0,5.
    assert modele.metriques["test"]["rappel"] >= modele.metriques["testSeuil05"]["rappel"]


def test_importances_completes(modele):
    assert set(modele.importances) == set(risque.VARIABLES)
    assert abs(sum(modele.importances.values()) - 1) < 0.01
    # Les résultats aux quiz comptent parmi les variables les plus importantes (sujet : risque « à partir des quiz »).
    assert {"noteMoyenne", "tauxReussite"} & set(list(modele.importances)[:3])


def _apprenant(**valeurs) -> pd.DataFrame:
    base = {"noteMoyenne": 0.85, "tauxReussite": 0.86, "nbQuiz": 9, "nbNotionsRatees": 1, "nbErreurs": 1,
            "progressionMoyenne": 0.8, "tempsPasseMinutes": 1300, "nbCoursSuivis": 4, "nbCoursTermines": 1,
            "joursActifs30j": 18, "joursDepuisDerniereConnexion": 1}
    return pd.DataFrame([{**base, **valeurs}])


def test_apprenant_a_l_aise_risque_faible_sans_raison(modele):
    r = modele.evaluer(_apprenant())[0]
    assert r.niveau == "faible"
    assert r.facteurs == []


def test_apprenant_en_difficulte_risque_eleve_avec_raisons(modele):
    r = modele.evaluer(_apprenant(noteMoyenne=0.35, tauxReussite=0.33, nbNotionsRatees=7, nbErreurs=15,
                                  tempsPasseMinutes=90, nbCoursSuivis=1, joursActifs30j=3,
                                  joursDepuisDerniereConnexion=30, progressionMoyenne=0.2))[0]
    assert r.niveau == "eleve"
    assert r.probabilite >= risque.SEUIL_ELEVE
    assert 1 <= len(r.facteurs) <= 3
    # Pas deux raisons redondantes (note et bonnes réponses disent la même chose).
    assert not (any("Note moyenne" in f for f in r.facteurs) and any("bonnes réponses" in f for f in r.facteurs))


@pytest.mark.parametrize(("p", "attendu"), [(0.1, "faible"), (0.39, "faible"), (0.4, "moyen"), (0.59, "moyen"), (0.6, "eleve")])
def test_niveaux(p, attendu):
    assert risque.niveau(p, seuil=0.4) == attendu


def test_entrainement_reproductible(apprenants, modele):
    autre = risque.entrainer(apprenants)
    assert autre.metriques["test"] == modele.metriques["test"]
    assert autre.seuil == modele.seuil
