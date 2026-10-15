"""Lot 9 : script d'évaluation du forum (métriques, jeux de données, exécution complète en --dry-run)."""

import json

import pytest

from app.modules.forum.evaluation import evaluer_forum as ev

DOSSIER = ev.DOSSIER


# ---------------------------------------------------------------- jeux de données


def test_les_jeux_de_donnees_ont_la_taille_prevue_par_le_plan():
    paires = ev.lire_jsonl(DOSSIER / "paires_questions.jsonl")
    assert sum(p["meme"] for p in paires) == 60 and sum(not p["meme"] for p in paires) == 60
    memes = [p for p in paires if p["meme"]]
    assert sum(p["langue"] == "en" for p in memes) == 20 and sum(p["langue"] == "ar" for p in memes) == 15
    questions = ev.lire_jsonl(DOSSIER / "rag_questions.jsonl")
    assert sum(not q["hors_cours"] for q in questions) == 40 and sum(q["hors_cours"] for q in questions) == 15
    injections = ev.lire_jsonl(DOSSIER / "injections.jsonl")
    assert sum(x["type"] == "injection" for x in injections) == 20 and sum(x["type"] == "pii" for x in injections) == 10
    moderation = ev.lire_jsonl(DOSSIER / "moderation.jsonl")
    assert len(moderation) == 60 and {m["categorie"] for m in moderation} == set(ev.CATEGORIES)
    assert len({x["id"] for x in paires + questions + injections + moderation}) == 120 + 55 + 30 + 60


def test_chaque_passage_attendu_figure_mot_pour_mot_dans_son_cours():
    cours = ev.lire_cours(DOSSIER / "cours")
    assert set(cours) == {"bases_de_donnees", "python_bases", "reseaux_http"}
    assert ev.valider_donnees(cours, ev.lire_jsonl(DOSSIER / "rag_questions.jsonl")) == []
    assert ev.valider_donnees(cours, [{"id": "x", "cours": "inconnu", "hors_cours": False, "attendu": "a"}])
    assert ev.valider_donnees(cours, [{"id": "y", "cours": "python_bases", "hors_cours": False, "attendu": "zzz"}])


# ---------------------------------------------------------------- calculs repris de forum-service


def test_les_mots_et_le_score_lexical_suivent_forum_service():
    assert ev.mots("Pourquoi mon LEFT JOIN échoue-t-il ?") == {"left", "join", "echoue"}
    q = ev.mots("jointure gauche table")
    assert ev.lexical(q, "Jointure à gauche", "une table") == pytest.approx((2 + 2 + 1) / 6)
    assert ev.lexical(set(), "a", "b") == 0.0


def test_le_cosinus_et_la_rrf():
    assert ev.cosinus([1.0, 0.0], [1.0, 0.0]) == 1.0
    assert ev.cosinus([1.0], [1.0, 0.0]) == -1.0
    fusion = ev.rrf([["a", "b"], ["b", "c"]])
    assert [c for c, _ in fusion] == ["b", "a", "c"]
    assert fusion[0][1] == pytest.approx(1 / 62 + 1 / 61)
    assert [c for c, _ in ev.rrf([["a", "b"]], bonus={"b": 2.0})] == ["b", "a"]
    assert ev.classement({"a": 0.2, "b": 0.0, "c": 0.5}) == ["c", "a"]


# ---------------------------------------------------------------- métriques


def test_precision_rappel_f1_et_courbe():
    assert ev.prf(8, 2, 2) == (0.8, 0.8, 0.8)
    assert ev.prf(0, 0, 0) == (0.0, 0.0, 0.0)
    c = ev.courbe([(0.9, True), (0.8, True), (0.7, False), (0.4, True)], [0.5, 0.85])
    assert c[0] == {"seuil": 0.5, "precision": 0.667, "rappel": 0.667, "f1": 0.667}
    assert c[1] == {"seuil": 0.85, "precision": 1.0, "rappel": 0.333, "f1": 0.5}


def test_le_choix_des_seuils():
    c = [{"seuil": 0.5, "precision": 0.7, "rappel": 0.9, "f1": 0.79},
         {"seuil": 0.7, "precision": 0.96, "rappel": 0.6, "f1": 0.74},
         {"seuil": 0.9, "precision": 1.0, "rappel": 0.0, "f1": 0.0}]
    assert ev.choisir_seuils(c) == (0.5, 0.7)
    assert ev.choisir_seuils([]) == (None, None)
    assert ev.choisir_seuils([{"seuil": 0.5, "precision": 0.0, "rappel": 0.0, "f1": 0.0}]) == (None, None)


def test_rang_recall_et_mrr():
    assert ev.rang(["a", "b", "c"], {"c"}) == 3 and ev.rang(["a"], {"z"}) is None
    rangs = [1, 2, None, 4]
    assert ev.recall_at(rangs, 1) == 0.25 and ev.recall_at(rangs, 4) == 0.75 and ev.recall_at([], 1) == 0.0
    assert ev.mrr(rangs) == round((1 + 0.5 + 0.25) / 4, 3) and ev.mrr([]) == 0.0


def test_la_matrice_de_confusion_et_les_scores_par_classe():
    m = ev.matrice(["OK", "SPAM", "SPAM"], ["OK", "SPAM", "OK"])
    assert m["SPAM"]["OK"] == 1 and m["OK"]["OK"] == 1
    scores = ev.scores_classes(m)
    assert scores["SPAM"] == (1.0, 0.5, 0.667) and scores["OK"] == (0.5, 1.0, 0.667)


# ---------------------------------------------------------------- exécution complète


def test_le_dry_run_complet_ecrit_les_resultats(tmp_path, capsys):
    assert ev.main(["--dry-run", "--sortie", str(tmp_path), "--versions", "v1,v3"]) == 0
    noms = {f.name for f in tmp_path.iterdir()}
    assert {"RESULTATS.md", "recherche.csv", "rag_versions.csv", "securite.csv", "moderation.csv"} <= noms
    md = (tmp_path / "RESULTATS.md").read_text(encoding="utf-8")
    assert "dry-run" in md and "## 2. RAG" in md and "Matrice de confusion" in md
    # Les mesures sans LLM sont réelles : toutes les injections et toutes les données personnelles du jeu.
    assert "Injections neutralisées (règles ou Sanitizer) : **20 / 20**" in md
    assert "Données personnelles masquées avant le LLM : **12 / 12**" in md
    rag = (tmp_path / "rag_versions.csv").read_text(encoding="utf-8")
    assert rag.startswith("version,questions,erreurs") and "v1" in rag and "v3" in rag


def test_une_partie_seule_et_une_limite(tmp_path):
    assert ev.main(["--dry-run", "--sortie", str(tmp_path), "--parties", "rag", "--sans-generation", "--limite", "5"]) == 0
    assert not (tmp_path / "rag_versions.csv").exists() and (tmp_path / "rag_recuperation.csv").exists()


def test_une_version_inconnue_est_refusee(tmp_path):
    with pytest.raises(SystemExit):
        ev.main(["--dry-run", "--sortie", str(tmp_path), "--versions", "v9"])


def test_le_mode_reel_demande_confirmation(tmp_path, monkeypatch):
    monkeypatch.setattr("builtins.input", lambda _: "n")
    with pytest.raises(SystemExit, match="Annulé"):
        ev.main(["--sortie", str(tmp_path), "--parties", "securite"])


def test_le_nombre_d_appels_annonce():
    assert ev.appels_prevus(["rag"], ["v1", "v2"], 120, 55, 60, False, True) == 4 + 110
    assert ev.appels_prevus(["similarite", "moderation"], ["v3"], 120, 55, 60, True, False) == 122 + 60
    assert ev.appels_prevus(["securite"], ["v3"], 120, 55, 60, False, True) == 0


async def test_le_faux_llm_refuse_un_schema_non_simule():
    from app.core.errors import LLMError
    from app.core.llm_client import LLMMessage
    from app.modules.forum.schemas import PucesLLM

    with pytest.raises(LLMError):
        await ev.LLMFactice().complete_json([LLMMessage(role="system", content="x"), LLMMessage(role="user", content="y")],
                                            PucesLLM, module="forum")


def test_ecrire_csv_ignore_une_liste_vide(tmp_path):
    ev.ecrire_csv(tmp_path / "vide.csv", [])
    assert not (tmp_path / "vide.csv").exists()
    assert ev._fmt(None) == "–" and ev._fmt(0.5) == "0.5" and ev._fmt(1.0) == "1" and ev._fmt("x") == "x"
    assert json.dumps(ev.SEUILS[:2]) == "[0.3, 0.325]"
