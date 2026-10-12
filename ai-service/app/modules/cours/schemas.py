from enum import Enum

from pydantic import Field

from app.core.schemas import CamelModel


class ResumeRequest(CamelModel):
    cours_id: int
    texte: str | None = None


class ResumeResponse(CamelModel):
    cours_id: int
    resume: str
    stub: bool = True


class Langue(str, Enum):
    """Langue du script, des sous-titres et de la voix."""

    FR = "fr"
    EN = "en"


class Niveau(str, Enum):
    """Mêmes codes que cours-service (enum Niveau)."""

    DEBUTANT = "DEBUTANT"
    INTERMEDIAIRE = "INTERMEDIAIRE"
    AVANCE = "AVANCE"


# ---------------------------------------------------------------- fiche de révision


class Notion(CamelModel):
    titre: str = Field(min_length=1, max_length=200)
    definition: str = Field(min_length=1)
    extrait_source: str = ""


class NotionVerifiee(Notion):
    """Notion de la réponse : `justifiee` vaut false si son extrait source est introuvable dans le texte du cours."""

    justifiee: bool = True


class FicheLLM(CamelModel):
    """Ce que le LLM doit renvoyer (validé par `complete_json`)."""

    resume: str = Field(min_length=1)
    mots_cles: list[str] = Field(default_factory=list)
    notions: list[Notion] = Field(default_factory=list)


class ResumeSeul(CamelModel):
    """Réponse du prompt de synthèse (textes longs)."""

    resume: str = Field(min_length=1)


class FicheRequest(CamelModel):
    cours_id: int
    texte: str = Field(min_length=20, max_length=300_000)
    niveau: Niveau = Niveau.INTERMEDIAIRE


class FicheResponse(CamelModel):
    resume: str
    mots_cles: list[str]
    notions: list[NotionVerifiee]
    niveau: Niveau
    score_fidelite: int = Field(ge=0, le=100)
    alertes: list[str]
    version_prompt: str
    modele: str


# ---------------------------------------------------------------- fidélité (sans LLM)


class FicheAVerifier(CamelModel):
    resume: str = ""
    mots_cles: list[str] = Field(default_factory=list)
    notions: list[Notion] = Field(default_factory=list)


class FideliteRequest(CamelModel):
    fiche: FicheAVerifier
    texte: str = Field(min_length=1, max_length=400_000)


class NotionJustifiee(CamelModel):
    titre: str
    justifiee: bool


class FideliteResponse(CamelModel):
    score_fidelite: int = Field(ge=0, le=100)
    alertes: list[str]
    notions: list[NotionJustifiee]


# ---------------------------------------------------------------- audio


class AudioRequest(CamelModel):
    texte: str = Field(min_length=1, max_length=20_000)
    langue: Langue = Langue.FR
