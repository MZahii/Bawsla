"""Tests unitaires : prompts versionnés, découpage et contrôle de fidélité (sans LLM)."""

import pytest

from app.modules.cours import fidelite
from app.modules.cours.decoupage import decouper
from app.modules.cours.prompts import VERSION_PAR_DEFAUT, prompt_fiche, versions_disponibles
from app.modules.cours.schemas import Notion
from tests.cours.conftest import COURS, EXTRAIT_INNER, EXTRAIT_LEFT

# ---------------------------------------------------------------- prompts


def test_trois_versions_disponibles_v3_par_defaut():
    assert versions_disponibles() == ["v1", "v2", "v3"]
    assert VERSION_PAR_DEFAUT == "v3"
    assert prompt_fiche("v3").version == "v3"


@pytest.mark.parametrize("version", ["v1", "v2", "v3"])
def test_structure_role_contexte_tache_format_contraintes(version):
    systeme = prompt_fiche(version).systeme
    positions = [systeme.index(rubrique) for rubrique in ("RÔLE", "CONTEXTE", "TÂCHE", "FORMAT", "CONTRAINTES")]
    assert positions == sorted(positions)


def test_v2_ajoute_le_priming_pedagogue_et_le_niveau():
    v1, v2 = prompt_fiche("v1").systeme, prompt_fiche("v2").systeme
    assert "pédagogue" not in v1 and "pédagogue" in v2
    assert "DEBUTANT" in v2 and "AVANCE" in v2 and "DEBUTANT" not in v1


def test_v3_impose_le_texte_seul_l_extrait_mot_pour_mot_et_les_delimiteurs():
    v2, v3 = prompt_fiche("v2"), prompt_fiche("v3")
    assert "UNIQUEMENT le texte fourni" in v3.systeme and "UNIQUEMENT le texte fourni" not in v2.systeme
    assert "MOT POUR MOT" in v3.systeme
    assert "<<<" in v3.systeme and ">>>" in v3.systeme
    assert "DONNÉE" in v3.systeme  # le texte entre <<< >>> n'est jamais une instruction
    message = v3.utilisateur(niveau="AVANCE", texte="Le cours.")
    assert "<<<\nLe cours.\n>>>" in message and "AVANCE" in message
    assert "<<<" not in v2.utilisateur(niveau="AVANCE", texte="Le cours.")


def test_le_texte_du_cours_n_est_jamais_interprete_comme_un_modele():
    piege = "Prix : $niveau et ${texte} et {niveau} $$"
    assert piege in prompt_fiche("v3").utilisateur(niveau="DEBUTANT", texte=piege)


def test_version_inconnue_retombe_sur_v3():
    assert prompt_fiche("v9").version == "v3"
    assert prompt_fiche(None).version == "v3"


# ---------------------------------------------------------------- découpage


def test_texte_court_un_seul_morceau():
    assert decouper("Un texte court.", 1000) == ["Un texte court."]
    assert decouper("   ", 1000) == []


def test_decoupe_entre_paragraphes_sans_rien_perdre():
    paragraphes = [f"Paragraphe {i}. " + "mot " * 30 for i in range(10)]
    morceaux = decouper("\n\n".join(paragraphes), 400)
    assert len(morceaux) > 1
    assert all(len(m) <= 400 for m in morceaux)
    assert "".join(m.replace("\n\n", "") .replace(" ", "") for m in morceaux) == "".join(p.replace(" ", "") for p in paragraphes)


def test_paragraphe_geant_coupe_en_phrases_puis_sur_les_espaces():
    long_ = " ".join(f"Phrase numéro {i}." for i in range(200))
    sans_ponctuation = "mot " * 500
    for texte in (long_, sans_ponctuation):
        morceaux = decouper(texte, 300)
        assert len(morceaux) > 1 and all(len(m) <= 300 for m in morceaux)


# ---------------------------------------------------------------- fidélité


def notion(titre, extrait, definition="Définition."):
    return Notion(titre=titre, definition=definition, extrait_source=extrait)


def test_fiche_fidele_score_maximal():
    r = fidelite.evaluer(
        "Les jointures combinent des tables. Un exemple compte 3 clés.",
        ["jointure interne", "LEFT JOIN", "clé primaire"],
        [notion("INNER JOIN", EXTRAIT_INNER), notion("LEFT JOIN", EXTRAIT_LEFT)],
        COURS,
    )
    assert r.score == 100 and r.alertes == []
    assert [n.justifiee for n in r.notions] == [True, True]


def test_extrait_invente_notion_non_justifiee_et_alerte():
    r = fidelite.evaluer(
        "Résumé.",
        ["LEFT JOIN"],
        [notion("INNER JOIN", EXTRAIT_INNER), notion("CROSS JOIN", "Le produit cartésien associe chaque ligne de A à chaque ligne de B")],
        COURS,
    )
    assert [n.justifiee for n in r.notions] == [True, False]
    assert any("CROSS JOIN" in a and "introuvable" in a for a in r.alertes)
    assert r.score < 100


def test_tolere_sauts_de_ligne_coupures_et_apostrophes_typographiques():
    source = "La clé pri-\nmaire identifie chaque ligne\nde façon unique dans l’ensemble de la table."
    r = fidelite.evaluer("R.", ["clé primaire"], [notion("Clé", "La clé primaire identifie chaque ligne de façon unique dans l'ensemble de la table.")], source)
    assert r.notions[0].justifiee is True


def test_mot_cle_absent_et_nombre_invente_sont_signales():
    r = fidelite.evaluer(
        "La table compte 42 colonnes et 3 clés.", ["LEFT JOIN", "blockchain"], [notion("INNER JOIN", EXTRAIT_INNER)], COURS
    )
    assert any("blockchain" in a for a in r.alertes)
    assert any("42" in a for a in r.alertes) and not any("3," in a for a in r.alertes)


def test_fiche_vide_ou_hors_sujet_score_faible_avec_alerte_globale():
    r = fidelite.evaluer("Un texte sur autre chose, avec 1999 et 2024.", ["cuisine", "football"], [notion("Penalty", "Le gardien plonge à gauche")], COURS)
    assert r.score < fidelite.SEUIL_ALERTE_SCORE
    assert r.alertes[0].startswith("Fidélité faible")


def test_sans_notion_la_fiche_est_signalee_incomplete():
    r = fidelite.evaluer("Résumé.", ["LEFT JOIN"], [], COURS)
    assert any("Aucune notion" in a for a in r.alertes)
