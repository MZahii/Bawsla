"""Script d'évaluation des prompts de la fiche : uniquement en --dry-run (faux LLM), jamais de vrai appel."""

import csv

import pytest

from app.modules.cours.evaluation import evaluer_fiche


def _cours(dossier):
    dossier.mkdir()
    (dossier / "a.txt").write_text(
        "Les jointures relient des tables. Une jointure interne garde seulement les correspondances entre deux tables. "
        "Une jointure externe gauche garde toutes les lignes de la table de gauche. "
        "La clé étrangère contient la clé primaire d'une autre table du modèle relationnel. "
        "Le produit cartésien associe chaque ligne de la première table à chaque ligne de la seconde.",
        encoding="utf-8",
    )
    return dossier


def test_dry_run_ecrit_csv_et_markdown_sans_confirmation(tmp_path, monkeypatch):
    def interdit(*_a, **_k):
        raise AssertionError("aucune confirmation attendue en dry-run")

    monkeypatch.setattr("builtins.input", interdit)
    sortie = tmp_path / "res"
    code = evaluer_fiche.main(["--dry-run", "--cours", str(_cours(tmp_path / "cours")), "--sortie", str(sortie)])
    assert code == 0
    fichiers_csv = list(sortie.glob("*_dry-run.csv"))
    assert len(fichiers_csv) == 1 and list(sortie.glob("*_dry-run.md"))
    with fichiers_csv[0].open(encoding="utf-8-sig", newline="") as f:
        lignes = list(csv.DictReader(f))
    assert [r["version"] for r in lignes] == ["v1", "v2", "v3"]
    assert all(r["erreur"] == "" and r["fournisseur"] == "dry-run" for r in lignes)
    assert all(int(r["notions"]) > 0 for r in lignes)


def test_refus_de_la_confirmation_n_envoie_rien(tmp_path, monkeypatch, capsys):
    monkeypatch.setattr("builtins.input", lambda *_: "n")
    appels = []
    monkeypatch.setattr(evaluer_fiche, "executer", lambda *_: appels.append(1))
    code = evaluer_fiche.main(["--cours", str(_cours(tmp_path / "cours")), "--sortie", str(tmp_path / "res")])
    assert code == 1 and not appels
    sortie = capsys.readouterr().out
    assert "appel(s) au LLM" in sortie and "Annulé" in sortie
    assert not (tmp_path / "res").exists()


def test_version_inconnue_refusee(tmp_path):
    with pytest.raises(SystemExit):
        evaluer_fiche.main(["--dry-run", "--versions", "v9", "--cours", str(_cours(tmp_path / "cours"))])


def test_dossier_vide(tmp_path):
    (tmp_path / "vide").mkdir()
    assert evaluer_fiche.main(["--dry-run", "--cours", str(tmp_path / "vide")]) == 2
