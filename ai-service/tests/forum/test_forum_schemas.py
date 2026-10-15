"""Schémas du module Forum : JSON en camelCase, champs inconnus refusés, bornes respectées."""

import types

import pytest
from pydantic import ValidationError

from app.modules.forum import prompts
from app.modules.forum.config import ForumAiSettings
from app.modules.forum.schemas import (
    AmeliorerRequest,
    IndexerRequest,
    ModererRequest,
    ModererResponse,
    RegrouperRequest,
    RepondreGeneralResponse,
    RepondreRequest,
    RepondreResponse,
    ResumerResponse,
    VectoriserRequest,
)


def test_une_entree_refuse_un_champ_inattendu_comme_un_identifiant_utilisateur():
    with pytest.raises(ValidationError) as erreur:
        RepondreRequest.model_validate({"question": "Pourquoi LEFT JOIN ?", "auteurId": 3})
    assert erreur.value.errors()[0]["type"] == "extra_forbidden"

    with pytest.raises(ValidationError):
        ModererRequest.model_validate({"contenu": "Bonjour", "email": "x@y.tn"})


def test_les_passages_imbriques_refusent_aussi_les_champs_inconnus():
    corps = {"question": "q", "passages": [{"id": 1, "cours": "BD", "texte": "t", "score": 0.5, "nom": "Ali"}]}
    with pytest.raises(ValidationError):
        RepondreRequest.model_validate(corps)


def test_les_entrees_se_lisent_en_camel_case():
    req = IndexerRequest.model_validate(
        {"coursId": 5, "pages": [{"numero": 1, "texte": "Les jointures"}],
         "chapitres": [{"id": 3, "titre": "Jointures", "contenu": "LEFT JOIN garde tout à gauche."}]}
    )
    assert req.cours_id == 5 and req.chapitres[0].id == 3

    ameliorer = AmeliorerRequest.model_validate({"titre": "Aide", "etiquettesExistantes": ["sql"]})
    assert ameliorer.etiquettes_existantes == ["sql"]


def test_la_reponse_suit_le_format_du_cahier_en_camel_case():
    reponse = RepondreResponse(
        reponse="LEFT JOIN garde les lignes de gauche [1].",
        sources=[{"numero": 1, "cours": "Bases de données", "passage": "…", "citation": "garde toutes les lignes"}],
        confiance=82,
        version_prompt="v3",
    )
    json = reponse.model_dump()
    assert set(json) >= {"reponse", "sources", "confiance", "questionsSimilaires", "horsCours", "versionPrompt"}
    assert json["sources"][0]["cours"] == "Bases de données"
    assert json["questionsSimilaires"] == []


def test_la_reponse_generale_n_a_ni_source_ni_confiance():
    champs = RepondreGeneralResponse.model_fields
    assert "sources" not in champs and "confiance" not in champs


@pytest.mark.parametrize(
    ("schema", "corps"),
    [
        (VectoriserRequest, {"textes": [], "tache": "DOCUMENT"}),
        (VectoriserRequest, {"textes": ["a"], "tache": "AUTRE"}),
        (RepondreRequest, {"question": ""}),
        (RepondreRequest, {"question": "q", "mode": "GENERAL"}),
        (RegrouperRequest, {"questions": []}),
    ],
)
def test_les_bornes_sont_controlees(schema, corps):
    with pytest.raises(ValidationError):
        schema.model_validate(corps)


def test_les_sorties_bornent_score_confiance_et_puces():
    with pytest.raises(ValidationError):
        ModererResponse(categorie="OK", score=1.2, raison="")
    with pytest.raises(ValidationError):
        ModererResponse(categorie="INCONNUE", score=0.2, raison="")
    with pytest.raises(ValidationError):
        RepondreResponse(reponse="r", confiance=101, version_prompt="v3")
    with pytest.raises(ValidationError):
        ResumerResponse(puces=["1", "2", "3", "4", "5", "6"], version_prompt="v1")


def test_la_version_de_prompt_par_defaut_est_v3(monkeypatch):
    monkeypatch.delenv("PROMPT_FORUM_VERSION", raising=False)
    assert ForumAiSettings(_env_file=None).prompt_forum_version == "v3"


def _version(v: str) -> types.ModuleType:
    module = types.ModuleType(f"essai_{v}")
    module.VERSION, module.SYSTEME, module.UTILISATEUR = v, f"Système {v}", "<question>$question</question>"
    return module


def test_une_version_inconnue_retombe_sur_v3():
    prompts.enregistrer("essai", _version("v1"), _version("v2"), _version("v3"))
    assert prompts.versions_disponibles("essai") == ["v1", "v2", "v3"]
    assert prompts.prompt("essai", "v2").version == "v2"
    assert prompts.prompt("essai", "v9").version == "v3"
    assert prompts.prompt("essai", None).version == "v3"


def test_une_tache_sans_v3_retombe_sur_sa_version_la_plus_recente():
    prompts.enregistrer("essai_court", _version("v1"), _version("v2"))
    assert prompts.prompt("essai_court", "v3").version == "v2"


def test_le_modele_insere_le_texte_sans_l_interpreter():
    prompts.enregistrer("essai_modele", _version("v1"))
    texte = prompts.prompt("essai_modele", "v1").utilisateur(question="$autre ${piege}")
    assert texte == "<question>$autre ${piege}</question>"
