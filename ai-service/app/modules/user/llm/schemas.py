"""Schémas de l'IA générative du module User.

Entrées (`...Request`) : seulement des nombres, des libellés (profil, niveau de risque), des noms de notions et des
titres de cours. Aucun identifiant, nom ni email : tout champ inattendu est refusé (extra="forbid", 400).
Sorties du LLM (`...Llm`) : validées par pydantic dans `complete_json`, puis contrôlées dans generation.py.
"""

from typing import Annotated, Literal

from pydantic import Field

from app.core.schemas import CamelModel
from app.modules.user.schemas import EntreeStricte, Indicateurs

Libelle = Annotated[str, Field(min_length=1, max_length=150)]
Titre = Annotated[str, Field(min_length=1, max_length=200)]


# ---------------------------------------------------------------- 1. conseil
class ConseilRequest(EntreeStricte):
    indicateurs: Indicateurs
    profil: str | None = Field(default=None, max_length=60, description="Nom du profil K-Means")
    objectif: str | None = Field(default=None, max_length=150)
    cours_suivis: list[Titre] = Field(default_factory=list, max_length=10)
    notions_a_revoir: list[Libelle] = Field(default_factory=list, max_length=5)
    cours_recommandes: list[Titre] = Field(default_factory=list, max_length=3)


class ConseilLlm(CamelModel):
    conseil: str
    actions: list[str]


class ConseilResponse(CamelModel):
    conseil: str
    actions: list[str]
    fournisseur: str | None = None
    version_prompt: str


# ---------------------------------------------------------------- 2. explication du risque
class ExplicationRisqueRequest(EntreeStricte):
    niveau: Literal["faible", "moyen", "eleve"]
    probabilite: float = Field(ge=0, le=1)
    facteurs: list[Libelle] = Field(default_factory=list, max_length=6)
    indicateurs: Indicateurs
    profil: str | None = Field(default=None, max_length=60)
    notions_a_revoir: list[Libelle] = Field(default_factory=list, max_length=5)


class ExplicationRisqueLlm(CamelModel):
    resume: str
    actions: list[str]
    message_suggere: str


class ExplicationRisqueResponse(CamelModel):
    resume: str
    actions: list[str]
    message_suggere: str
    fournisseur: str | None = None
    version_prompt: str


# ---------------------------------------------------------------- 3. plan de la semaine
class CoursEnCours(EntreeStricte):
    titre: Titre
    progression: float = Field(ge=0, le=1)


class PlanRequest(EntreeStricte):
    temps_disponible: str | None = Field(default=None, max_length=30, description="Libellé du parcours : « 1 à 3 h »…")
    objectif: str | None = Field(default=None, max_length=150)
    profil: str | None = Field(default=None, max_length=60)
    cours_en_cours: list[CoursEnCours] = Field(default_factory=list, max_length=5)
    notions_a_revoir: list[Libelle] = Field(default_factory=list, max_length=5)
    cours_recommandes: list[Titre] = Field(default_factory=list, max_length=3)


class Seance(CamelModel):
    jour: str
    activite: str
    duree_minutes: int


class PlanLlm(CamelModel):
    resume: str
    seances: list[Seance]


class PlanResponse(CamelModel):
    resume: str
    seances: list[Seance]
    total_minutes: int
    budget_minutes: int
    fournisseur: str | None = None
    version_prompt: str


# ---------------------------------------------------------------- 4. rapport de la classe
class RepartitionRisque(EntreeStricte):
    faible: int = Field(default=0, ge=0, le=100_000)
    moyen: int = Field(default=0, ge=0, le=100_000)
    eleve: int = Field(default=0, ge=0, le=100_000)
    inconnu: int = Field(default=0, ge=0, le=100_000, description="Apprenants aux données incomplètes")


class Effectif(EntreeStricte):
    nom: Libelle
    nombre: int = Field(ge=0, le=100_000)


class CoursStat(EntreeStricte):
    titre: Titre
    nb_inscrits: int = Field(ge=0, le=100_000)
    progression_moyenne: float | None = Field(default=None, ge=0, le=1)


class CohorteRequest(EntreeStricte):
    nb_apprenants: int = Field(ge=1, le=100_000)
    nb_cours: int = Field(ge=0, le=10_000)
    risques: RepartitionRisque
    progression_moyenne: float | None = Field(default=None, ge=0, le=1)
    note_moyenne: float | None = Field(default=None, ge=0, le=1)
    profils: list[Effectif] = Field(default_factory=list, max_length=10)
    notions: list[Effectif] = Field(default_factory=list, max_length=10)
    cours: list[CoursStat] = Field(default_factory=list, max_length=10)


class CohorteLlm(CamelModel):
    synthese: str
    points_cles: list[str]
    recommandations: list[str]


class CohorteResponse(CamelModel):
    synthese: str
    points_cles: list[str]
    recommandations: list[str]
    fournisseur: str | None = None
    version_prompt: str


# ---------------------------------------------------------------- 5. domaines depuis une phrase
class DomainesRequest(EntreeStricte):
    texte: str = Field(min_length=3, max_length=500)
    domaines_disponibles: list[Libelle] = Field(min_length=1, max_length=30)
    objectifs_disponibles: list[Libelle] = Field(default_factory=list, max_length=15)


class DomainesLlm(CamelModel):
    domaines: list[str]
    objectif: str | None = None
    explication: str = ""


class DomainesResponse(CamelModel):
    domaines: list[str]
    objectif: str | None = None
    explication: str
    fournisseur: str | None = None
    version_prompt: str
