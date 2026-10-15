"""Schémas du module Forum (La Vigie). JSON en camelCase, Python en snake_case.

Toutes les entrées héritent de `ForumModel` (extra="forbid") : un champ inattendu, par exemple un identifiant
d'utilisateur, est refusé avec une 400. Aucune route du forum ne reçoit de nom, d'email ni d'id d'utilisateur ;
les seuls identifiants admis sont ceux des cours, chapitres, discussions et passages.
"""

from typing import Literal

from pydantic import ConfigDict, Field

from app.core.schemas import CamelModel

# Bornes communes (le Sanitizer tronque de toute façon avant l'envoi au LLM).
MAX_QUESTION = 2000
MAX_TEXTE = 20000
MAX_PASSAGE = 4000

Tache = Literal["DOCUMENT", "REQUETE"]
Origine = Literal["COURS", "FORUM"]
Categorie = Literal["OK", "HORS_SUJET", "IRRESPECTUEUX", "SPAM", "FUITE_EXAMEN", "INJECTION", "DONNEES_PERSONNELLES"]


class ForumModel(CamelModel):
    """Base des schémas d'entrée du forum : refuse tout champ non déclaré."""

    model_config = ConfigDict(extra="forbid")


# ---------------------------------------------------------------- Contrat 5 (stub d'origine, conservé)


class QuestionsSimilairesRequest(CamelModel):
    question: str = Field(min_length=1)
    cours_id: int | None = None
    limite: int = Field(default=5, ge=1, le=20)


class DiscussionSimilaire(CamelModel):
    discussion_id: int
    titre: str
    score: float


class QuestionsSimilairesResponse(CamelModel):
    question: str
    similaires: list[DiscussionSimilaire]
    stub: bool = True


# ---------------------------------------------------------------- POST /vectoriser


class VectoriserRequest(ForumModel):
    textes: list[str] = Field(min_length=1, max_length=100)
    tache: Tache


class VectoriserResponse(CamelModel):
    vecteurs: list[list[float]]
    modele: str
    dimension: int


# ---------------------------------------------------------------- POST /indexer


class PageCours(ForumModel):
    numero: int = Field(ge=1)
    texte: str


class ChapitreCours(ForumModel):
    id: int
    titre: str = Field(max_length=300)
    contenu: str


class IndexerRequest(ForumModel):
    cours_id: int
    pages: list[PageCours] = Field(default_factory=list)
    chapitres: list[ChapitreCours] = Field(default_factory=list)


class PassageIndexe(CamelModel):
    chapitre_id: int | None = None
    page: int | None = None
    ordre: int
    titre_section: str | None = None
    texte: str
    vecteur: list[float]


class IndexerResponse(CamelModel):
    passages: list[PassageIndexe]
    modele: str
    dimension: int


# ---------------------------------------------------------------- POST /reformuler


class MessageHistorique(ForumModel):
    role: Literal["APPRENANT", "VIGIE"]
    texte: str = Field(max_length=MAX_TEXTE)


class ReformulerRequest(ForumModel):
    question: str = Field(min_length=1, max_length=MAX_QUESTION)
    historique: list[MessageHistorique] = Field(default_factory=list, max_length=12)


class ReformulerResponse(CamelModel):
    requete: str
    langue_detectee: str
    version_prompt: str | None = None
    fournisseur: str | None = None


# ---------------------------------------------------------------- POST /repondre (format du cahier des charges)


class PassageContexte(ForumModel):
    id: int
    origine: Origine = "COURS"
    cours: str = Field(max_length=300)
    chapitre: str | None = Field(default=None, max_length=300)
    page: int | None = None
    texte: str = Field(max_length=MAX_PASSAGE)
    score: float = Field(ge=0)


class RepondreRequest(ForumModel):
    question: str = Field(min_length=1, max_length=MAX_QUESTION)
    mode: Literal["DIRECTE", "GUIDEE"] = "DIRECTE"
    passages: list[PassageContexte] = Field(default_factory=list, max_length=8)


class SourceReponse(CamelModel):
    id: int | None = None
    origine: Origine = "COURS"
    cours: str
    passage: str
    chapitre: str | None = None
    page: int | None = None
    citation: str


class RepondreResponse(CamelModel):
    """`{ reponse, sources: [{ cours, passage }], confiance, questions_similaires }` du cahier, en camelCase.

    confiance (0 à 100) est CALCULÉE après génération, jamais recopiée du modèle.
    questionsSimilaires est rempli par forum-service (il possède les discussions).
    """

    reponse: str
    sources: list[SourceReponse] = Field(default_factory=list)
    confiance: int = Field(ge=0, le=100)
    hors_cours: bool = False
    indices: list[str] = Field(default_factory=list)
    questions_similaires: list[DiscussionSimilaire] = Field(default_factory=list)
    version_prompt: str
    fournisseur: str | None = None
    alertes: list[str] = Field(default_factory=list)


# ---------------------------------------------------------------- POST /repondre-general (niveau 3, sur demande)


class RepondreGeneralRequest(ForumModel):
    question: str = Field(min_length=1, max_length=MAX_QUESTION)


class RepondreGeneralResponse(CamelModel):
    """Réponse hors cours : ni source ni confiance (une confiance auto-déclarée n'est jamais affichée)."""

    reponse: str
    incertitudes: list[str] = Field(default_factory=list)
    a_verifier: list[str] = Field(default_factory=list)
    version_prompt: str
    fournisseur: str | None = None


# ---------------------------------------------------------------- POST /moderer


class ModererRequest(ForumModel):
    titre: str | None = Field(default=None, max_length=300)
    contenu: str = Field(min_length=1, max_length=MAX_TEXTE)


class ModererResponse(CamelModel):
    categorie: Categorie
    score: float = Field(ge=0, le=1)
    raison: str
    regle: str | None = None  # règle sans LLM qui a tranché, le cas échéant
    version_prompt: str | None = None
    fournisseur: str | None = None


# ---------------------------------------------------------------- POST /resumer


class MessageFil(ForumModel):
    role: Literal["QUESTION", "REPONSE", "VIGIE", "COMMENTAIRE"]
    texte: str = Field(max_length=MAX_TEXTE)


class ResumerRequest(ForumModel):
    titre: str = Field(min_length=1, max_length=300)
    messages: list[MessageFil] = Field(min_length=1, max_length=200)


class ResumerResponse(CamelModel):
    puces: list[str] = Field(min_length=1, max_length=5)
    version_prompt: str
    fournisseur: str | None = None


# ---------------------------------------------------------------- POST /regrouper


class QuestionVecteur(ForumModel):
    id: int
    titre: str = Field(max_length=300)
    vecteur: list[float] = Field(min_length=1)


class RegrouperRequest(ForumModel):
    questions: list[QuestionVecteur] = Field(min_length=1, max_length=2000)


class Groupe(CamelModel):
    nom: str
    ids: list[int]
    taille: int


class RegrouperResponse(CamelModel):
    groupes: list[Groupe]
    k: int
    silhouette: float | None = None


# ---------------------------------------------------------------- POST /ameliorer


class AmeliorerRequest(ForumModel):
    titre: str = Field(min_length=1, max_length=300)
    contenu: str = Field(default="", max_length=MAX_TEXTE)
    etiquettes_existantes: list[str] = Field(default_factory=list, max_length=200)


class AmeliorerResponse(CamelModel):
    titre: str
    manques: list[str] = Field(default_factory=list)
    etiquettes: list[str] = Field(default_factory=list, max_length=3)
    version_prompt: str
    fournisseur: str | None = None
