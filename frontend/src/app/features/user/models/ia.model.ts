/** Miroir de metriques.json renvoyé par GET /api/ai/user/metriques (ai-service, module User). */

export interface ScoresTest {
  accuracy: number;
  precision: number;
  rappel: number;
  f1: number;
  auc: number;
  /** [[réussites bien prédites, fausses alertes], [échecs ratés, échecs repérés]] */
  matriceConfusion: [[number, number], [number, number]];
}

export interface ScoresValidationCroisee {
  accuracy: number;
  f1: number;
  auc: number;
}

export interface MetriquesRisque {
  nbApprenants: number;
  nbEntrainement: number;
  nbTest: number;
  tauxEchec: number;
  hyperparametres: { max_depth?: number | null; min_samples_leaf?: number };
  seuilDecision: number;
  test: ScoresTest;
  testSeuil05: ScoresTest;
  comparaison: Record<'classeMajoritaire' | 'regressionLogistique' | 'randomForest',
    { validationCroisee: ScoresValidationCroisee; test: ScoresTest }>;
  importances: Record<string, number>;
  importancesPermutation: Record<string, number>;
  variables: string[];
}

export interface GroupeProfil {
  id: number;
  nom: string;
  description: string;
  taille: number;
  centre: Record<string, number>;
}

export interface MetriquesProfils {
  k: number;
  regleChoixK: string;
  silhouette: number;
  courbes: { k: number; inertie: number; silhouette: number }[];
  nbApprenants: number;
  variables: string[];
  accordProfilsCaches?: number;
  tauxEchecParGroupe?: Record<string, number>;
  groupes: GroupeProfil[];
}

export interface ScoresRecommandation {
  reussiteA3: number;
  reussiteA5: number;
  couvertureA3: number;
  couvertureA5: number;
}

export interface MetriquesRecommandation {
  poids: Record<string, number>;
  kVoisins: number;
  nbApprenants: number;
  nbCours: number;
  nbInscriptions: number;
  densite: number;
  evaluation: Record<'hybride' | 'popularite' | 'collaboratifSeul', ScoresRecommandation> & {
    nbApprenantsEvalues: number;
    tirages: number;
  };
}

export interface MetriquesIa {
  dateEntrainement: string;
  dureeSecondes: number;
  source: string;
  donneesSimulees: boolean;
  versions: Record<string, string>;
  profils: MetriquesProfils;
  risque: MetriquesRisque;
  recommandation: MetriquesRecommandation;
}

// ---------- Un apprenant (user-service : GET /api/users/me/ia et /api/users/{id}/ia) ----------

export type StatutSource = 'DISPONIBLE' | 'STUB' | 'PAS_ENCORE_DISPONIBLE' | 'INDISPONIBLE';

/** Miroir de IndicateursResponse.Source. */
export interface SourceDonnees {
  fournisseur: string;
  contrat: string;
  statut: StatutSource;
  detail?: string;
}

/** Miroir de IndicateursResponse.Indicateurs : un champ absent = source pas encore disponible. */
export interface IndicateursApprenant {
  noteMoyenne?: number;
  tauxReussite?: number;
  nbQuiz?: number;
  nbNotionsRatees?: number;
  nbErreurs?: number;
  progressionMoyenne?: number;
  tempsPasseMinutes?: number;
  nbCoursSuivis?: number;
  nbCoursTermines?: number;
  joursActifs30j?: number;
  joursDepuisDerniereConnexion?: number;
}

export interface CoursRecommande {
  coursId: number;
  titre: string;
  score: number;
  raison: string;
}

/** Miroir de IaApprenantResponse : chaque partie peut manquer, son champ « …Indisponible » dit pourquoi. */
export interface IaApprenant {
  profil?: { id: number; nom: string; description: string };
  profilIndisponible?: string;
  risque?: { probabilite: number; niveau: 'faible' | 'moyen' | 'eleve'; facteurs: string[] };
  risqueIndisponible?: string;
  recommandations?: CoursRecommande[];
  recommandationsIndisponibles?: string;
  donneesSimulees?: boolean;
  sources: Record<string, SourceDonnees>;
  complet: boolean;
  indicateurs?: IndicateursApprenant;
}

// ---------- Page Apprenants du formateur (user-service : GET /api/users/formateur/apprenants) ----------

export type NiveauRisque = 'faible' | 'moyen' | 'eleve';

/** Miroir de ApprenantsResponse.Ligne : une inscription (apprenant × cours). */
export interface LigneApprenant {
  etudiantId: number;
  nom: string;
  prenom: string;
  avatarVersion?: number;
  coursId: number;
  coursTitre?: string;
  dateInscription?: string;
  progression?: number;
  tempsPasseMinutes?: number;
  dateDerniereActivite?: string;
  risque?: { probabilite: number; niveau: NiveauRisque; facteurs: string[] };
  risqueIndisponible?: string;
  profil?: string;
}

/** Miroir de ApprenantsResponse. */
export interface ApprenantsFormateur {
  cours: { id: number; titre: string }[];
  lignes: LigneApprenant[];
  inscriptions?: SourceDonnees;
  message?: string;
  donneesSimulees?: boolean;
}
