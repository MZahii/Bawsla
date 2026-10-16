/**
 * IA générative du module User (le Machine Learning décide, le LLM explique). Miroir de ContenuIaResponse
 * (user-service) et des réponses d'ai-service (/api/ai/user/conseil, /plan, /explication-risque, /rapport-cohorte,
 * /domaines).
 */

/** Dernier texte généré ; `depuisCache` : les données n'avaient pas changé, aucun nouvel appel au LLM. */
export interface ContenuIa<T> {
  type: string;
  contenu: T;
  fournisseur?: string;
  dateGeneration: string;
  depuisCache: boolean;
}

export interface ConseilIa {
  conseil: string;
  actions: string[];
}

export interface SeanceIa {
  jour: string;
  activite: string;
  dureeMinutes: number;
}

export interface PlanIa {
  resume: string;
  seances: SeanceIa[];
  totalMinutes: number;
  budgetMinutes: number;
}

export interface ExplicationRisqueIa {
  resume: string;
  actions: string[];
  messageSuggere: string;
}

export interface RapportCohorteIa {
  synthese: string;
  pointsCles: string[];
  recommandations: string[];
}

export interface SuggestionParcoursIa {
  domaines: string[];
  objectif?: string;
  explication: string;
}
