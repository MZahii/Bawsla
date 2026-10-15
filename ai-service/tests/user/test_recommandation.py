"""Filtrage collaboratif hybride : jamais un cours déjà suivi, raisons lisibles, meilleur que les références."""

import pandas as pd
import pytest

from app.modules.user.ml import recommandation as reco
from app.modules.user.ml.donnees_simulees import generer


@pytest.fixture(scope="module")
def donnees():
    return generer()


@pytest.fixture(scope="module")
def modele(donnees):
    return reco.entrainer(*donnees)


def test_matrice_apprenants_x_cours(modele, donnees):
    apprenants, inscriptions, cours = donnees
    assert modele.matrice.shape == (len(apprenants), len(cours))
    assert int(modele.matrice.to_numpy().sum()) == len(inscriptions)
    assert modele.popularite.max() == 1


def test_ne_recommande_jamais_un_cours_deja_suivi(modele, donnees):
    _, inscriptions, _ = donnees
    for a, groupe in list(inscriptions.groupby("apprenant"))[:30]:
        suivis = list(groupe["coursId"])
        recos = modele.recommander(suivis, n=5, exclure=a)
        assert len(recos) == 5
        assert not {r.coursId for r in recos} & set(suivis)
        assert len({r.coursId for r in recos}) == 5


def test_recommandations_triees_et_expliquees(modele):
    recos = modele.recommander([1, 7], ["Data"], n=4)
    assert [r.score for r in recos] == sorted((r.score for r in recos), reverse=True)
    assert all(r.raison and r.titre for r in recos)


def test_le_collaboratif_explique_par_les_voisins(modele):
    recos = modele.recommander([1, 2], [], n=3)
    assert any("au profil proche du tien" in r.raison for r in recos)


def test_demarrage_a_froid_par_domaines_et_niveau_accessible(modele):
    recos = modele.recommander([], ["Web"], n=2)
    assert [r.coursId for r in recos] == [4, 5]  # HTML/CSS (débutant) puis Angular (intermédiaire)
    assert all("Web" in r.raison for r in recos)


def test_notions_ratees_proposent_de_revoir_les_bases(modele):
    """Notions ratées dans « Spring Boot » (8, intermédiaire) : « Bases de données SQL » (7, débutant) en tête."""
    recos = modele.recommander([8], ["Back-end"], cours_en_difficulte=[8], n=3)
    assert recos[0].coursId == 7
    assert recos[0].raison == "Pour revoir les bases en Back-end"


def test_catalogue_limite_les_cours_recommandables(modele):
    recos = modele.recommander([1], ["Data"], n=5, catalogue=[2, 3, 99])
    assert {r.coursId for r in recos} == {2, 3}


def test_tout_suivi_rien_a_recommander(modele, donnees):
    assert modele.recommander(list(donnees[2]["coursId"])) == []


def test_meilleur_que_les_references(modele):
    ev = modele.metriques["evaluation"]
    hybride = ev["hybride"]
    assert hybride["reussiteA3"] > ev["popularite"]["reussiteA3"] + 0.15
    assert hybride["reussiteA3"] > ev["collaboratifSeul"]["reussiteA3"]
    assert hybride["reussiteA5"] >= 0.6
    # Recommandations variées : tout le catalogue est proposé à quelqu'un, contrairement à la popularité.
    assert hybride["couvertureA3"] > ev["popularite"]["couvertureA3"]


def test_apprenant_absent_de_la_matrice(modele):
    """Un nouvel apprenant (pas dans la matrice) avec un historique reçoit quand même du collaboratif."""
    recos = modele.recommander([4, 5], ["Web"], n=3, exclure="INCONNU")
    assert recos and not {4, 5} & {r.coursId for r in recos}


def test_construire_accepte_un_catalogue_indexe(donnees):
    _, inscriptions, cours = donnees
    m = reco.construire(inscriptions, cours.set_index("coursId"))
    assert list(m.cours.index) == list(cours["coursId"])
    assert isinstance(m.matrice, pd.DataFrame)


# ---------- Vrai catalogue (cours-service), inconnu du modèle ----------

VRAI_CATALOGUE = pd.DataFrame([
    {"coursId": 101, "titre": "SQL pour débutants", "categorie": "Données", "niveau": "DEBUTANT"},
    {"coursId": 102, "titre": "Optimiser ses requêtes", "categorie": "Données", "niveau": "AVANCE"},
    {"coursId": 103, "titre": "Introduction à React", "categorie": "Web", "niveau": "DEBUTANT"},
    {"coursId": 104, "titre": "Sans niveau", "categorie": None, "niveau": None},
]).set_index("coursId")


def test_vrai_catalogue_classe_par_domaines_et_niveau(modele):
    recos = modele.recommander([], ["Données"], n=4, cours_disponibles=VRAI_CATALOGUE)
    assert [r.coursId for r in recos][:2] == [101, 102]  # domaine d'abord, niveau accessible en tête
    assert recos[0].raison == "Dans un domaine qui t'intéresse : Données"
    assert {r.coursId for r in recos} <= set(VRAI_CATALOGUE.index)


def test_vrai_catalogue_notions_ratees(modele):
    recos = modele.recommander([102], [], cours_en_difficulte=[102], n=2, cours_disponibles=VRAI_CATALOGUE)
    assert recos[0].coursId == 101
    assert recos[0].raison == "Pour revoir les bases en Données"


def test_vrai_catalogue_cours_nouveau_sans_popularite(modele):
    recos = modele.recommander([101, 102, 103], [], n=1, cours_disponibles=VRAI_CATALOGUE)
    assert recos[0].coursId == 104
    assert recos[0].raison == "Nouveau dans le catalogue"
