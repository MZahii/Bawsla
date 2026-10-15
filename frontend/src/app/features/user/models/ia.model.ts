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
