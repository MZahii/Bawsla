"""Schémas du module Quiz (IA). JSON en camelCase (CamelModel), Python en snake_case.

- Schémas de l'API : GenererQuizRequest / GenererQuizResponse (voir CONTRATS_API.md, contrat 5).
- Schémas internes (suffixe Llm) : forme du JSON attendu du LLM, validée par complete_json.
  Ils sont volontairement souples (listes de taille libre) : la validation stricte se fait question
  par question, pour rejeter une seule question mal formée sans perdre tout le lot.
"""

from enum import StrEnum
from typing import Annotated

from pydantic import BeforeValidator, Field, field_validator

from app.core.schemas import CamelModel


class NiveauBloom(StrEnum):
    MEMORISER = "MEMORISER"
    COMPRENDRE = "COMPRENDRE"
    APPLIQUER = "APPLIQUER"
    ANALYSER = "ANALYSER"
    EVALUER = "EVALUER"
    CREER = "CREER"


class Difficulte(StrEnum):
    FACILE = "FACILE"
    MOYEN = "MOYEN"
    DIFFICILE = "DIFFICILE"


class StatutQuestion(StrEnum):
    VALIDEE = "VALIDEE"
    REJETEE = "REJETEE"


# --- API ---------------------------------------------------------------------------------------


class GenererQuizRequest(CamelModel):
    """Objectifs pédagogiques uniquement : aucun texte de cours, aucune donnée d'apprenant."""

    cours_id: int = Field(ge=1)
    theme: str = Field(min_length=2, max_length=200)
    competences: list[str] = Field(min_length=1, max_length=8)
    niveaux_bloom: list[NiveauBloom] = Field(min_length=1, max_length=6)
    difficulte: Difficulte = Difficulte.MOYEN
    nb_questions: int = Field(default=5, ge=1, le=10)

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
    fournisseur: str | None = None
    alertes: list[str] = Field(default_factory=list)


# --- Réponses attendues du LLM -----------------------------------------------------------------

# Un null ou un type inattendu sur un champ ne doit pas invalider tout le lot : il devient une valeur vide,
# et la question concernée est rejetée ensuite par la validation stricte.
TexteLlm = Annotated[str, BeforeValidator(lambda v: v if isinstance(v, str) else "")]
BoolLlm = Annotated[bool, BeforeValidator(lambda v: v is True)]


class ChoixLlm(CamelModel):
    texte: TexteLlm = ""
    est_correcte: BoolLlm = False
    erreur_associee: TexteLlm = ""


class QuestionLlm(CamelModel):
    enonce: TexteLlm = ""
    niveau_bloom: TexteLlm = ""
    competence: TexteLlm = ""
    choix: Annotated[list[ChoixLlm], BeforeValidator(lambda v: v if isinstance(v, list) else [])] = Field(
        default_factory=list
    )
    explication: TexteLlm = ""


class GenerationLlm(CamelModel):
    questions: list[QuestionLlm]


class ReponseVerificationLlm(CamelModel):
    numero: int
    choix: TexteLlm = Field(default="", description="Lettre du choix retenu : A, B, C ou D")
    justification: TexteLlm = ""


class VerificationLlm(CamelModel):
    reponses: list[ReponseVerificationLlm]
