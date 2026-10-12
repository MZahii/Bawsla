"""Schémas de l'explication orale : ce que le LLM renvoie, et ce que les routes reçoivent et renvoient (JSON en camelCase)."""

from enum import Enum

from pydantic import Field, field_validator

from app.core.schemas import CamelModel
from app.modules.cours.schemas import Langue, Niveau


class Mode(str, Enum):
    MONOLOGUE = "monologue"
    DIALOGUE = "dialogue"


class TypeSegment(str, Enum):
    INTRO = "intro"
    EXPLICATION = "explication"
    ANALOGIE = "analogie"
    EXEMPLE = "exemple"
    DEMONSTRATION = "demonstration"
    QUESTION = "question"
    RECAP = "recap"


class Locuteur(str, Enum):
    PROF = "prof"
    ETUDIANT = "etudiant"


# Les segments qui affirment des faits du cours doivent citer l'extrait qui les justifie.
TYPES_AVEC_EXTRAIT = frozenset({TypeSegment.EXPLICATION, TypeSegment.EXEMPLE, TypeSegment.DEMONSTRATION})


class SegmentLLM(CamelModel):
    """Un segment du script, tel que le LLM le renvoie."""

    type: TypeSegment
    locuteur: Locuteur = Locuteur.PROF
    texte: str = Field(min_length=1)
    notion: str | None = None
    extrait_source: str | None = None
    page: int | None = Field(default=None, ge=1)

    @field_validator("notion", "extrait_source", mode="before")
    @classmethod
    def _vide_en_none(cls, valeur):
        return None if isinstance(valeur, str) and not valeur.strip() else valeur


class ExplicationLLM(CamelModel):
    """Le script complet renvoyé par le LLM (validé par complete_json)."""

    langue: Langue = Langue.FR
    mode: Mode = Mode.MONOLOGUE
    segments: list[SegmentLLM] = Field(min_length=4, max_length=60)


# ---------------------------------------------------------------- entrées des routes


class PageCours(CamelModel):
    """Une page du cours (ou un chapitre, pour un cours sans PDF)."""

    numero: int = Field(ge=1)
    texte: str = ""


class SegmentEntree(CamelModel):
    """Un segment envoyé pour contrôle, traduction ou mise en voix."""

    ordre: int = Field(ge=1)
    type: TypeSegment = TypeSegment.EXPLICATION
    locuteur: Locuteur = Locuteur.PROF
    texte: str = Field(min_length=1)
    notion: str | None = None
    extrait_source: str | None = None
    page: int | None = None


class ExplicationRequest(CamelModel):
    cours_id: int
    pages: list[PageCours] = Field(max_length=2000)
    #: Titres des notions de la fiche validée (le plan). Vide : l'IA choisit les notions.
    notions: list[str] = Field(default_factory=list, max_length=30)
    niveau: Niveau = Niveau.INTERMEDIAIRE
    mode: Mode = Mode.MONOLOGUE
    langue: Langue = Langue.FR
    duree_minutes: int = Field(default=4, ge=2, le=6)


class TraductionRequest(CamelModel):
    segments: list[SegmentEntree] = Field(min_length=1, max_length=60)
    pages: list[PageCours] = Field(max_length=2000)
    notions: list[str] = Field(default_factory=list, max_length=30)
    langue_cible: Langue = Langue.EN
    langue_source: Langue = Langue.FR
    mode: Mode = Mode.MONOLOGUE


class FideliteExplicationRequest(CamelModel):
    segments: list[SegmentEntree] = Field(min_length=1, max_length=60)
    pages: list[PageCours] = Field(max_length=2000)
    notions: list[str] = Field(default_factory=list, max_length=30)


class AudioExplicationRequest(CamelModel):
    segments: list[SegmentEntree] = Field(min_length=1, max_length=60)
    langue: Langue = Langue.FR
    mode: Mode = Mode.MONOLOGUE


# ---------------------------------------------------------------- sorties


class SegmentVerifie(CamelModel):
    ordre: int
    type: TypeSegment
    locuteur: Locuteur
    texte: str
    notion: str | None = None
    extrait_source: str | None = None
    #: Page où l'extrait a été retrouvé dans le cours (corrigée si le modèle s'est trompé).
    page: int | None = None
    #: false : segment suspect (extrait manquant ou introuvable). Toujours true pour une analogie.
    justifiee: bool = True
    #: true : analogie, partie libre qui ne vient pas du cours (marquée comme telle dans l'interface).
    analogie: bool = False


class ExplicationResponse(CamelModel):
    langue: Langue
    mode: Mode
    segments: list[SegmentVerifie]
    score_fidelite: int = Field(ge=0, le=100)
    alertes: list[str]
    version_prompt: str
    modele: str
    #: Fournisseur LLM qui a réellement répondu ("gemini", ou "openai" si le secours a pris le relais).
    fournisseur: str | None = None
    nb_mots: int
    duree_estimee_secondes: int
    #: true : le plan n'a pas été donné (pas de fiche validée), l'IA a choisi les notions.
    notions_choisies_par_ia: bool = False


class FideliteExplicationResponse(CamelModel):
    score_fidelite: int = Field(ge=0, le=100)
    alertes: list[str]
    segments: list[SegmentVerifie]
    nb_mots: int
    duree_estimee_secondes: int


class MinutageSegment(CamelModel):
    ordre: int
    debut_ms: int
    fin_ms: int


class AudioExplicationResponse(CamelModel):
    fournisseur: str
    mime: str
    #: Audio encodé en base64 : le minutage voyage dans le même JSON (un MP3 de 5 min fait environ 6 Mo en base64).
    audio_base64: str
    duree_ms: int
    #: true : minutage réparti au prorata du nombre de caractères (Gemini fait un seul appel pour tout le script).
    minutage_estime: bool
    segments: list[MinutageSegment]
