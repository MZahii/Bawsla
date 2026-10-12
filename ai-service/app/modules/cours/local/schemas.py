"""Schémas et erreurs de POST /api/ai/cours/resume-local."""

from enum import Enum

from pydantic import Field

from app.core.errors import LLMError
from app.core.schemas import CamelModel
from app.modules.cours.explication.schemas import PageCours


class Longueur(str, Enum):
    COURTE = "courte"
    MOYENNE = "moyenne"
    LONGUE = "longue"


class ModeResume(str, Enum):
    AUTO = "auto"
    ABSTRACTIF = "abstractif"
    EXTRACTIF = "extractif"


class ResumeLocalRequest(CamelModel):
    cours_id: int
    pages: list[PageCours] = Field(max_length=2000)
    longueur: Longueur = Longueur.MOYENNE
    mode: ModeResume = ModeResume.AUTO


class PhraseResume(CamelModel):
    texte: str
    #: Phrase du cours la plus proche (celle-ci même pour un résumé extractif).
    extrait_source: str | None = None
    page: int | None = None
    #: false : aucune phrase du cours ne justifie assez cette phrase du résumé (affichée en orange au formateur).
    justifiee: bool = True


class ResumeLocalResponse(CamelModel):
    langue: str
    modele: str
    mode_utilise: ModeResume
    resume: str
    phrases: list[PhraseResume]
    score_fidelite: int = Field(ge=0, le=100)
    #: Score du résumé reformulé tel qu'il a été jugé, même s'il a été remplacé par l'extractif (None : non tenté).
    score_reformule: int | None = None
    alertes: list[str]
    duree_ms: int


class ResumeLocalIndisponibleError(LLMError):
    """Interrupteur éteint, ou bibliothèques (torch, transformers) absentes pour le mode demandé."""

    status_code = 503
    code = "RESUME_LOCAL_UNAVAILABLE"

    def __init__(self, message: str = "Résumé local indisponible sur ce serveur") -> None:
        super().__init__(message)


class LangueNonSupporteeError(LLMError):
    """Le cours n'est ni en français ni en anglais (ou trop court pour le dire)."""

    status_code = 400
    code = "BAD_REQUEST"
