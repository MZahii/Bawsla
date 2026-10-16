"""K-Means du module User : choix de k, nommage des groupes, prédiction, validation sur les profils cachés."""

import pandas as pd
import pytest

from app.modules.user.ml import clustering
from app.modules.user.ml.donnees_simulees import generer


@pytest.fixture(scope="module")
def apprenants() -> pd.DataFrame:
    return generer()[0]


@pytest.fixture(scope="module")
def modele(apprenants):
    return clustering.entrainer(apprenants)


def test_k_choisi_par_la_silhouette_entre_3_et_6(modele):
    points = {p["k"]: p for p in modele.metriques["courbes"]}
    assert set(points) == set(clustering.K_TESTES)
    assert clustering.K_MIN <= modele.k <= clustering.K_MAX
    meilleure = max(p["silhouette"] for k, p in points.items() if clustering.K_MIN <= k <= clustering.K_MAX)
    assert modele.metriques["silhouette"] == meilleure


def test_methode_du_coude_inertie_decroissante(modele):
    inerties = [p["inertie"] for p in modele.metriques["courbes"]]
    assert inerties == sorted(inerties, reverse=True)


def test_groupes_nommes_et_complets(modele, apprenants):
    assert len(modele.groupes) == modele.k
    assert sum(g.taille for g in modele.groupes) == len(apprenants)
    assert len({g.nom for g in modele.groupes}) == modele.k
    for g in modele.groupes:
        assert g.description
        assert set(g.centre) == set(clustering.VARIABLES)


def test_les_groupes_ont_du_sens(modele):
    """Sur les données simulées : un groupe de décrocheurs, et il échoue plus que le groupe le plus à l'aise."""
    noms = {g.nom for g in modele.groupes}
    assert "Décrocheur" in noms
    taux = modele.metriques["tauxEchecParGroupe"]
    meilleur = max(modele.groupes, key=lambda g: g.centre["noteMoyenne"]).nom
    assert taux["Décrocheur"] > taux[meilleur]


def test_accord_avec_les_profils_caches(modele):
    """ARI : 0 = hasard, 1 = parfait. On attend un accord net sans être parfait (les profils se chevauchent)."""
    assert 0.3 <= modele.metriques["accordProfilsCaches"] < 1.0
    # Aucun apprenant « avancé » n'est classé décrocheur.
    assert modele.metriques["repartitionProfilsCaches"]["Décrocheur"].get("avance", 0) == 0


def test_predire_un_nouvel_apprenant(modele):
    nouveaux = pd.DataFrame([
        {"noteMoyenne": 0.85, "progressionMoyenne": 0.8, "tempsPasseMinutes": 1200, "nbCoursSuivis": 4,
         "nbNotionsRatees": 1, "joursActifs30j": 18, "joursDepuisDerniereConnexion": 1},
        {"noteMoyenne": 0.5, "progressionMoyenne": 0.1, "tempsPasseMinutes": 60, "nbCoursSuivis": 1,
         "nbNotionsRatees": 5, "joursActifs30j": 1, "joursDepuisDerniereConnexion": 45},
    ])
    assidu, absent = modele.predire(nouveaux)
    assert absent.nom == "Décrocheur"
    assert assidu.nom in {"Avancé", "Régulier"}


def test_entrainement_reproductible(apprenants):
    a = clustering.entrainer(apprenants)
    b = clustering.entrainer(apprenants)
    assert [g.centre for g in a.groupes] == [g.centre for g in b.groupes]


@pytest.mark.parametrize(("centre", "nom"), [
    ({"joursActifs30j": 3, "joursDepuisDerniereConnexion": 2, "noteMoyenne": 0.9}, "Décrocheur"),
    ({"joursActifs30j": 15, "joursDepuisDerniereConnexion": 30, "noteMoyenne": 0.9}, "Décrocheur"),
    ({"joursActifs30j": 15, "joursDepuisDerniereConnexion": 2, "noteMoyenne": 0.85}, "Avancé"),
    ({"joursActifs30j": 15, "joursDepuisDerniereConnexion": 2, "noteMoyenne": 0.70}, "Régulier"),
    ({"joursActifs30j": 15, "joursDepuisDerniereConnexion": 2, "noteMoyenne": 0.40}, "Débutant en difficulté"),
])
def test_regles_de_nommage(centre, nom):
    assert clustering.nommer(centre) == nom
