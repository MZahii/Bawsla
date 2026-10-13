"""Entraînement complet, enregistrement et rechargement des modèles du module User."""

import json

import pandas as pd
import pytest

from app.modules.user.ml import entrainement


@pytest.fixture(scope="module")
def dossier(tmp_path_factory):
    return tmp_path_factory.mktemp("modeles")


@pytest.fixture(scope="module")
def modeles(dossier):
    return entrainement.entrainer_tout(dossier)


@pytest.fixture(autouse=True)
def cache_vide():
    entrainement.vider_cache()
    yield
    entrainement.vider_cache()


def test_fichiers_ecrits(modeles, dossier):
    for fichier in [*entrainement.FICHIERS.values(), entrainement.METRIQUES]:
        assert (dossier / fichier).stat().st_size > 0


def test_metriques_json_completes(modeles, dossier):
    met = json.loads((dossier / entrainement.METRIQUES).read_text(encoding="utf-8"))
    assert met["donneesSimulees"] is True and "simulées" in met["source"]
    assert met["versions"]["scikit-learn"] == entrainement.sklearn.__version__
    assert {"profils", "risque", "recommandation"} <= set(met)
    assert met["profils"]["groupes"] and met["profils"]["courbes"]
    assert {"accuracy", "rappel", "f1", "auc", "matriceConfusion"} <= set(met["risque"]["test"])
    assert "hybride" in met["recommandation"]["evaluation"]


def test_rechargement_identique(modeles, dossier):
    recharges = entrainement.charger(dossier)
    assert recharges is not None
    ligne = pd.DataFrame([{v: 0.5 for v in entrainement.risque.VARIABLES} | {"nbQuiz": 3, "tempsPasseMinutes": 200}])
    assert recharges.risque.evaluer(ligne)[0] == modeles.risque.evaluer(ligne)[0]
    assert recharges.recommandation.recommander([1, 7], ["Data"]) == modeles.recommandation.recommander([1, 7], ["Data"])
    assert [g.nom for g in recharges.profils.groupes] == [g.nom for g in modeles.profils.groupes]


def test_charger_sans_fichiers_renvoie_none(tmp_path):
    assert entrainement.charger(tmp_path) is None


def test_charger_refuse_une_autre_version_de_scikit_learn(modeles, dossier, tmp_path):
    for f in [*entrainement.FICHIERS.values(), entrainement.METRIQUES]:
        (tmp_path / f).write_bytes((dossier / f).read_bytes())
    met = json.loads((tmp_path / entrainement.METRIQUES).read_text(encoding="utf-8"))
    met["versions"]["scikit-learn"] = "0.0.1"
    (tmp_path / entrainement.METRIQUES).write_text(json.dumps(met), encoding="utf-8")
    assert entrainement.charger(tmp_path) is None


def test_charger_refuse_une_autre_version_de_python(modeles, dossier, tmp_path):
    for f in [*entrainement.FICHIERS.values(), entrainement.METRIQUES]:
        (tmp_path / f).write_bytes((dossier / f).read_bytes())
    met = json.loads((tmp_path / entrainement.METRIQUES).read_text(encoding="utf-8"))
    met["versions"]["python"] = "2.7.18"
    (tmp_path / entrainement.METRIQUES).write_text(json.dumps(met), encoding="utf-8")
    assert entrainement.charger(tmp_path) is None


def test_obtenir_charge_puis_garde_en_memoire(modeles, dossier, monkeypatch):
    appels = []
    monkeypatch.setattr(entrainement, "entrainer_tout", lambda d: appels.append(d))
    premier = entrainement.obtenir(dossier)
    assert premier.metriques["dateEntrainement"] == modeles.metriques["dateEntrainement"]
    assert entrainement.obtenir(dossier) is premier
    assert appels == []  # chargé depuis le disque, sans réentraîner


def test_obtenir_entraine_si_rien_sur_le_disque(tmp_path, monkeypatch, modeles):
    monkeypatch.setattr(entrainement, "entrainer_tout", lambda d: modeles)
    assert entrainement.obtenir(tmp_path) is modeles


def test_reentrainer_remplace_le_cache(modeles, dossier, monkeypatch):
    entrainement.obtenir(dossier)
    nouveaux = entrainement.Modeles(modeles.profils, modeles.risque, modeles.recommandation, {"nouveau": True})
    monkeypatch.setattr(entrainement, "entrainer_tout", lambda d: nouveaux)
    assert entrainement.reentrainer(dossier) is nouveaux
    assert entrainement.obtenir(dossier) is nouveaux
