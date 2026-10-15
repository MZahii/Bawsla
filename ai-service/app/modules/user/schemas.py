"""Schémas du module User (IA). Les entrées ne contiennent que des nombres et des listes de cours ou de domaines :
aucun identifiant, nom ni email d'apprenant. Tout champ inattendu (etudiantId, email…) est refusé (400)."""

from pydantic import ConfigDict, Field

from app.core.schemas import CamelModel


class EntreeStricte(CamelModel):
    model_config = ConfigDict(extra="forbid")  # s'ajoute à la configuration camelCase de CamelModel


class Indicateurs(EntreeStricte):
    """Chiffres d'un apprenant, calculés par user-service à partir de quiz-service, cours-service et user-service.
    Les noms (camelCase) sont ceux des colonnes du jeu de données et des contrats."""

    note_moyenne: float = Field(ge=0, le=1, description="Note moyenne aux quiz, de 0 à 1")
    taux_reussite: float = Field(ge=0, le=1, description="Part de bonnes réponses, de 0 à 1")
    nb_quiz: int = Field(ge=0, le=10_000)
    nb_notions_ratees: int = Field(ge=0, le=10_000)
    nb_erreurs: int = Field(ge=0, le=100_000)
    progression_moyenne: float = Field(ge=0, le=1, description="Progression moyenne dans les cours suivis")
    temps_passe_minutes: int = Field(ge=0, le=1_000_000)
    nb_cours_suivis: int = Field(ge=0, le=1_000)
    nb_cours_termines: int = Field(ge=0, le=1_000)
    # Alias explicite : la génération automatique donnerait « joursActifs30J ».
    jours_actifs30j: int = Field(ge=0, le=30, alias="joursActifs30j",
                                 description="Jours avec au moins une connexion sur les 30 derniers")
    jours_depuis_derniere_connexion: int = Field(ge=0, le=36_500)


class LotIndicateurs(EntreeStricte):
    """Un ou plusieurs apprenants ; les résultats reviennent dans le même ordre."""

    apprenants: list[Indicateurs] = Field(min_length=1, max_length=500)


class Groupe(CamelModel):
    id: int
    nom: str
    description: str


class ProfilResponse(CamelModel):
    groupes: list[Groupe]
    donnees_simulees: bool


class RisqueApprenant(CamelModel):
    probabilite: float
    niveau: str  # faible, moyen, eleve
    facteurs: list[str]


class RisqueResponse(CamelModel):
    resultats: list[RisqueApprenant]
    seuil: float
    donnees_simulees: bool


class RecommandationsRequest(EntreeStricte):
    cours_suivis: list[int] = Field(default_factory=list, max_length=500)
    domaines: list[str] = Field(default_factory=list, max_length=20, description="Domaines du parcours de l'apprenant")
    cours_en_difficulte: list[int] = Field(default_factory=list, max_length=500,
                                           description="coursId des notions ratées (quiz-service, contrat 3)")
    catalogue: list[int] | None = Field(default=None, max_length=5_000,
                                        description="Cours recommandables (ex. : cours publiés) ; tous par défaut")
    nombre: int = Field(default=3, ge=1, le=10)


class CoursRecommande(CamelModel):
    cours_id: int
    titre: str
    score: float
    raison: str


class RecommandationsResponse(CamelModel):
    recommandations: list[CoursRecommande]
    donnees_simulees: bool
