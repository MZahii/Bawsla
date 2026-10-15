"""Schémas du module Quiz (IA). JSON en camelCase (CamelModel), Python en snake_case.

- Schémas de l'API : GenererQuizRequest / GenererQuizResponse et RemedierRequest / RemedierResponse
  (voir CONTRATS_API.md, contrat 5).
- Schémas internes (suffixe Llm) : forme du JSON attendu du LLM, validée par complete_json.
  Ils sont volontairement souples (listes de taille libre) : la validation stricte se fait question
  par question, pour rejeter une seule question mal formée sans perdre tout le lot.
"""

from enum import StrEnum
from typing import Annotated

from pydantic import BeforeValidator, ConfigDict, Field, field_validator, model_validator

from app.core.schemas import CamelModel


class NiveauBloom(StrEnum):
    MEMORISER = "MEMORISER"
    COMPRENDRE = "COMPRENDRE"
    APPLIQUER = "APPLIQUER"
    ANALYSER = "ANALYSER"
    EVALUER = "EVALUER"
    CREER = "CREER"


class Difficulte(StrEnum):
    """Cinq niveaux, du rappel direct au cas limite d'expert (grille détaillée : prompts/difficultes.py)."""

    FACILE = "FACILE"
    INTERMEDIAIRE = "INTERMEDIAIRE"
    AVANCE = "AVANCE"
    DIFFICILE = "DIFFICILE"
    MASTER = "MASTER"


def _ancienne_difficulte(valeur: object) -> object:
    """« MOYEN » (échelle à 3 niveaux) reste accepté : il correspond à INTERMEDIAIRE."""
    return Difficulte.INTERMEDIAIRE if valeur == "MOYEN" else valeur


class StatutQuestion(StrEnum):
    VALIDEE = "VALIDEE"
    REJETEE = "REJETEE"


# --- API ---------------------------------------------------------------------------------------


class GenererQuizRequest(CamelModel):
    """Objectifs pédagogiques uniquement : aucun texte de cours, aucune donnée d'apprenant."""

    cours_id: int = Field(ge=1)
    # Discipline du cours (Mathématiques, Histoire, ou la précision donnée pour « Autre ») : cadre le vocabulaire.
    matiere: str | None = Field(default=None, max_length=80)
    theme: str = Field(min_length=2, max_length=200)
    competences: list[str] = Field(min_length=1, max_length=8)
    niveaux_bloom: list[NiveauBloom] = Field(min_length=1, max_length=6)
    difficulte: Annotated[Difficulte, BeforeValidator(_ancienne_difficulte)] = Difficulte.INTERMEDIAIRE
    nb_questions: int = Field(default=5, ge=1, le=10)

    @field_validator("matiere")
    @classmethod
    def _matiere_propre(cls, valeur: str | None) -> str | None:
        return (valeur or "").strip() or None

    @field_validator("theme")
    @classmethod
    def _theme_non_vide(cls, valeur: str) -> str:
        valeur = valeur.strip()
        if len(valeur) < 2:
            raise ValueError("Le thème est obligatoire")
        return valeur

    @field_validator("competences")
    @classmethod
    def _competences_propres(cls, valeurs: list[str]) -> list[str]:
        propres: list[str] = []
        for v in valeurs:
            v = v.strip()
            if not v:
                raise ValueError("Une compétence ne peut pas être vide")
            if len(v) > 150:
                raise ValueError("Une compétence fait au plus 150 caractères")
            if v not in propres:
                propres.append(v)
        return propres

    @field_validator("niveaux_bloom")
    @classmethod
    def _niveaux_uniques(cls, valeurs: list[NiveauBloom]) -> list[NiveauBloom]:
        return list(dict.fromkeys(valeurs))


class ChoixQcm(CamelModel):
    texte: str
    est_correcte: bool
    erreur_associee: str | None = None


class QuestionQcm(CamelModel):
    enonce: str
    niveau_bloom: NiveauBloom | None = None
    competence: str | None = None
    choix: list[ChoixQcm]
    explication: str
    statut: StatutQuestion
    raison_rejet: str | None = None


class GenererQuizResponse(CamelModel):
    cours_id: int
    questions: list[QuestionQcm]
    nb_validees: int
    nb_rejetees: int
    version_prompt: str
    difficulte: Difficulte
    fournisseur: str | None = None
    alertes: list[str] = Field(default_factory=list)


class RemedierRequest(CamelModel):
    """Une mauvaise réponse à expliquer. Aucun identifiant, nom ni email d'apprenant : tout champ inattendu
    est refusé (400)."""

    model_config = ConfigDict(extra="forbid")  # s'ajoute à la configuration camelCase de CamelModel

    notion: str = Field(min_length=1, max_length=150, description="Compétence visée par la question")
    niveau_bloom: NiveauBloom
    enonce: str = Field(min_length=1, max_length=1000)
    choix_donne: str = Field(min_length=1, max_length=300)
    bonne_reponse: str = Field(min_length=1, max_length=300)
    erreur_associee: Annotated[str, BeforeValidator(lambda v: "" if v is None else v)] = Field(default="", max_length=300)
    explication: Annotated[str, BeforeValidator(lambda v: "" if v is None else v)] = Field(default="", max_length=1000)

    @field_validator("notion", "enonce", "choix_donne", "bonne_reponse")
    @classmethod
    def _non_vide(cls, valeur: str) -> str:
        valeur = valeur.strip()
        if not valeur:
            raise ValueError("Ce champ est obligatoire")
        return valeur

    @model_validator(mode="after")
    def _reponse_fausse(self) -> "RemedierRequest":
        if self.choix_donne.casefold() == self.bonne_reponse.casefold():
            raise ValueError("La réponse donnée est la bonne réponse : rien à remédier")
        return self


class RemedierResponse(CamelModel):
    notion: str
    # Chain-of-Thought : réservé au formateur (et à quiz-service). Retiré par le routeur pour un apprenant.
    raisonnement: str | None = None
    explication: str
    conseil: str
    question_rattrapage: QuestionQcm | None = None
    version_prompt: str
    fournisseur: str | None = None
    alertes: list[str] = Field(default_factory=list)


# --- Réponses attendues du LLM -----------------------------------------------------------------

# Un null ou un type inattendu sur un champ ne doit pas invalider tout le lot : il devient une valeur vide,
# et la question concernée est rejetée ensuite par la validation stricte.
TexteLlm = Annotated[str, BeforeValidator(lambda v: v if isinstance(v, str) else "")]
BoolLlm = Annotated[bool, BeforeValidator(lambda v: v is True)]


def _texte_ou_lignes(valeur: object) -> str:
    """Un raisonnement « étape par étape » arrive parfois en liste de chaînes : on la joint au lieu de la perdre."""
    if isinstance(valeur, list):
        return "\n".join(v.strip() for v in valeur if isinstance(v, str) and v.strip())
    return valeur if isinstance(valeur, str) else ""


TexteLongLlm = Annotated[str, BeforeValidator(_texte_ou_lignes)]


def _seulement_les_objets(valeur: object) -> list:
    return [v for v in valeur if isinstance(v, dict)] if isinstance(valeur, list) else []


class ChoixLlm(CamelModel):
    texte: TexteLlm = ""
    est_correcte: BoolLlm = False
    erreur_associee: TexteLlm = ""


class QuestionLlm(CamelModel):
    enonce: TexteLlm = ""
    niveau_bloom: TexteLlm = ""
    competence: TexteLlm = ""
    choix: Annotated[list[ChoixLlm], BeforeValidator(_seulement_les_objets)] = Field(default_factory=list)
    explication: TexteLlm = ""


class GenerationLlm(CamelModel):
    questions: list[QuestionLlm]


class ReponseVerificationLlm(CamelModel):
    numero: int
    choix: TexteLlm = Field(default="", description="Lettre du choix retenu : A, B, C ou D")
    justification: TexteLlm = ""


class VerificationLlm(CamelModel):
    reponses: list[ReponseVerificationLlm]


class RemediationLlm(CamelModel):
    """Chain-of-Thought : « raisonnement » vient en premier, le reste en découle."""

    raisonnement: TexteLongLlm = ""
    explication: TexteLongLlm = ""
    conseil: TexteLongLlm = ""
    question_rattrapage: Annotated[
        QuestionLlm | None, BeforeValidator(lambda v: v if isinstance(v, dict) else None)
    ] = None
