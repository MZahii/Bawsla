"""Tests boîte blanche : chemins du code que les tests fonctionnels ne parcouraient pas.

Chaque test vise une branche précise, repérée par la couverture de branches (pytest --cov-branch) :
le nom de la fonction et la condition sont rappelés en tête de test.
"""

import pytest
from pydantic import ValidationError

from app.modules.quiz import coherence
from app.modules.quiz.generation import Objectifs, valider
from app.modules.quiz.prompts import prompt_verification, versions_disponibles
from app.modules.quiz.schemas import (
    ChoixQcm,
    GenererQuizRequest,
    NiveauBloom,
    QuestionLlm,
    QuestionQcm,
    ReponseVerificationLlm,
    StatutQuestion,
)
from tests.quiz.conftest import DEMANDE, question

OBJECTIFS = Objectifs("Jointures SQL", ["Écrire une jointure interne"], [NiveauBloom.APPLIQUER], "INTERMEDIAIRE")


def _qcm() -> QuestionQcm:
    return QuestionQcm(
        enonce="Q ?",
        choix=[ChoixQcm(texte=t, est_correcte=t == "A") for t in "ABCD"],
        explication="E",
        statut=StatutQuestion.VALIDEE,
    )


# --- coherence ------------------------------------------------------------------------------------------


def test_paquets_sans_question():
    """_paquets : boucle jamais exécutée → `if courant` faux, aucun paquet."""
    assert coherence._paquets(prompt_verification("v1"), [], budget=8000, marge=400) == []


def test_divergence_sans_justification():
    """_divergence : `if reponse.justification.strip()` faux → raison sans « — justification »."""
    raison = coherence._divergence(_qcm(), ReponseVerificationLlm(numero=1, choix="B", justification="   "))
    assert raison == "Désaccord à la vérification : « B » choisi au lieu de « A »"


@pytest.mark.parametrize(
    ("choix", "attendu"),
    [("A", None), (" a) car… ", None), ("Z", "illisible"), ("", "illisible")],
)
def test_divergence_lettre(choix, attendu):
    """_divergence : lettre correcte (casse, espaces, ponctuation), puis lettre hors A-D ou vide.

    Défaut trouvé par ce test : `"" in "ABCD"` est vrai, une réponse vide était lue comme « A ».
    """
    raison = coherence._divergence(_qcm(), ReponseVerificationLlm(numero=1, choix=choix))
    assert raison is None if attendu is None else attendu in raison


def test_divergence_reponse_absente():
    """_divergence : `if reponse is None` vrai."""
    assert "n'a pas répondu" in coherence._divergence(_qcm(), None)


# --- generation.valider -------------------------------------------------------------------------------


def test_valider_choix_vide():
    """valider : `any(not c.texte for c in choix)` vrai (texte réduit à des espaces)."""
    brute = question(1)
    brute["choix"][2]["texte"] = "   "
    resultat = valider(QuestionLlm.model_validate(brute), OBJECTIFS)
    assert resultat.statut is StatutQuestion.REJETEE
    assert "choix vide" in resultat.raison_rejet


def test_valider_question_conforme():
    """valider : toutes les conditions fausses → VALIDEE, erreurAssociee absente sur la bonne réponse."""
    resultat = valider(QuestionLlm.model_validate(question(1)), OBJECTIFS)
    assert resultat.statut is StatutQuestion.VALIDEE
    assert [c.erreur_associee is None for c in resultat.choix] == [True, False, False, False]


# --- schemas : validateurs ----------------------------------------------------------------------------


def test_theme_trop_court_apres_nettoyage():
    """_theme_non_vide : 2 caractères bruts (min_length passe) mais 1 après strip → refus."""
    with pytest.raises(ValidationError, match="Le thème est obligatoire"):
        GenererQuizRequest.model_validate({**DEMANDE, "theme": " J "})


def test_competence_faite_d_espaces():
    """_competences_propres : `if not v` vrai."""
    with pytest.raises(ValidationError, match="ne peut pas être vide"):
        GenererQuizRequest.model_validate({**DEMANDE, "competences": ["   "]})


def test_competences_en_double_fusionnees():
    """_competences_propres : `if v not in propres` faux (doublon après strip) → une seule fois."""
    demande = GenererQuizRequest.model_validate({**DEMANDE, "competences": ["Jointures", " Jointures ", "Index"]})
    assert demande.competences == ["Jointures", "Index"]


# --- prompts ------------------------------------------------------------------------------------------


def test_versions_disponibles():
    assert versions_disponibles() == ["v1", "v2"]
