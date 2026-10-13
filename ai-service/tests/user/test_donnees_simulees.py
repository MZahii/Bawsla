"""Jeu de données simulé du module User : reproductible, cohérent, sans donnée personnelle."""

import pandas as pd

from app.modules.user.ml import donnees_simulees as ds


def test_meme_graine_memes_donnees():
    a1, i1, _ = ds.generer()
    a2, i2, _ = ds.generer()
    pd.testing.assert_frame_equal(a1, a2)
    pd.testing.assert_frame_equal(i1, i2)


def test_autre_graine_autres_donnees():
    a1, _, _ = ds.generer(graine=1)
    a2, _, _ = ds.generer(graine=2)
    assert not a1.equals(a2)


def test_taille_et_colonnes():
    apprenants, inscriptions, cours = ds.generer()
    assert len(apprenants) == ds.NB_APPRENANTS
    assert apprenants["apprenant"].is_unique
    assert len(cours) == len(ds.CATALOGUE)
    attendues = {
        "nbCoursSuivis", "nbCoursTermines", "progressionMoyenne", "tempsPasseMinutes", "nbQuiz", "noteMoyenne",
        "tauxReussite", "nbNotionsRatees", "nbErreurs", "joursActifs30j", "joursDepuisDerniereConnexion", "echec",
    }
    assert attendues <= set(apprenants.columns)
    assert {"apprenant", "coursId", "progression", "tempsPasseMinutes", "termine", "note"} <= set(inscriptions.columns)


def test_aucune_donnee_personnelle():
    apprenants, inscriptions, _ = ds.generer()
    colonnes = {c.lower() for c in [*apprenants.columns, *inscriptions.columns]}
    assert not colonnes & {"nom", "prenom", "email", "etudiantid", "userid"}
    assert apprenants["apprenant"].str.fullmatch(r"A\d{3}").all()


def test_valeurs_dans_les_bornes():
    apprenants, inscriptions, _ = ds.generer()
    for col in ["progressionMoyenne", "noteMoyenne", "tauxReussite"]:
        assert apprenants[col].between(0, 1).all(), col
    assert inscriptions["progression"].between(0, 1).all()
    assert (apprenants["joursActifs30j"].between(0, 30)).all()
    assert (apprenants[["tempsPasseMinutes", "nbQuiz", "nbNotionsRatees", "nbErreurs"]] >= 0).all().all()
    # Chaque apprenant suit au moins un cours, et ses inscriptions correspondent au compteur.
    par_apprenant = inscriptions.groupby("apprenant").size()
    assert (par_apprenant.reindex(apprenants["apprenant"]).values == apprenants["nbCoursSuivis"].values).all()
    assert inscriptions["coursId"].isin([c[0] for c in ds.CATALOGUE]).all()


def test_les_profils_caches_sont_distincts_et_realistes():
    apprenants, _, _ = ds.generer()
    moy = apprenants.groupby("profilCache")[["noteMoyenne", "progressionMoyenne", "echec"]].mean()
    # Les avancés réussissent mieux que les débutants en difficulté ; les décrocheurs progressent peu.
    assert moy.loc["avance", "noteMoyenne"] > moy.loc["regulier", "noteMoyenne"] > moy.loc["debutant_en_difficulte", "noteMoyenne"]
    assert moy.loc["decrocheur", "progressionMoyenne"] < 0.4
    assert moy.loc["avance", "echec"] < 0.25 < moy.loc["debutant_en_difficulte", "echec"]
    # Assez d'échecs ET de réussites pour entraîner un classifieur.
    assert 0.25 <= apprenants["echec"].mean() <= 0.55


def test_enregistrer_ecrit_les_trois_csv(tmp_path):
    chemins = ds.enregistrer(tmp_path)
    assert set(chemins) == {"apprenants", "inscriptions", "cours"}
    assert len(pd.read_csv(chemins["apprenants"])) == ds.NB_APPRENANTS
