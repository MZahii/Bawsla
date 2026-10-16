/**
 * Modération, statistiques, studio formateur et espace admin (miroir des DTO de forum-service).
 */
import { Auteur, CibleType, DiscussionResume } from './forum.model';
import { NiveauReponseIa } from './vigie.model';
import { Reputation } from './engagement.model';

export type MotifSignalement = 'HORS_SUJET' | 'IRRESPECTUEUX' | 'SPAM' | 'FUITE_EXAMEN' | 'AUTRE';
export type CategorieModeration =
  | 'OK'
  | 'HORS_SUJET'
  | 'IRRESPECTUEUX'
  | 'SPAM'
  | 'FUITE_EXAMEN'
  | 'INJECTION'
  | 'DONNEES_PERSONNELLES';
export type StatutSignalement = 'OUVERT' | 'CONFIRME' | 'REJETE';
export type TypeSanction = 'AVERTISSEMENT' | 'LECTURE_SEULE' | 'SUSPENSION';
export type ActionModeration =
  | 'MASQUER'
  | 'MASQUAGE_AUTO'
  | 'RETABLIR'
  | 'VERROUILLER'
  | 'DEVERROUILLER'
  | 'EPINGLER'
  | 'DESEPINGLER'
  | 'DEPLACER'
  | 'FUSIONNER'
  | 'MODIFIER_ETIQUETTES'
  | 'SUPPRIMER'
  | 'DECIDER_SIGNALEMENT'
  | 'ESCALADER'
  | 'SANCTIONNER'
  | 'LEVER_SANCTION'
  | 'VALIDER_VIGIE'
  | 'CORRIGER_VIGIE'
  | 'REJETER_VIGIE'
  | 'MODIFIER_PARAMETRES'
  | 'FUSIONNER_ETIQUETTES'
  | 'RENOMMER_ETIQUETTE';

/** Point d'une série (bw-chart). */
export interface SerieStat {
  libelle: string;
  valeur: number;
}

export interface SignalementRequest {
  cibleType: CibleType;
  cibleId: number;
  motif: MotifSignalement;
  commentaire?: string;
}

/** Élément de la file de modération ; signalePar absent = signalement automatique de l'IA. */
export interface Signalement {
  id: number;
  cibleType: CibleType;
  cibleId: number;
  coursId: number;
  coursTitre?: string;
  discussionId?: number;
  extrait?: string;
  auteurCible?: Auteur;
  signalePar?: Auteur;
  motif: MotifSignalement;
  commentaire?: string;
  categorieIa?: CategorieModeration;
  scoreIa?: number;
  raisonIa?: string;
  statut: StatutSignalement;
  escalade: boolean;
  cibleMasquee: boolean;
  decision?: string;
  decideLe?: string;
  creeLe: string;
}

/** statut OUVERT seulement avec escalader = true (transmettre à l'administration). */
export interface DecisionRequest {
  statut: StatutSignalement;
  masquer: boolean;
  escalader: boolean;
  motif: string;
}

export interface Statistiques {
  questions: number;
  sansReponse: number;
  delaiPremiereReponseHeures?: number;
  tauxResolution: number;
  tauxCouvertureVigie: number;
  tauxVigieUtile: number;
  parChapitre: SerieStat[];
  parSemaine: SerieStat[];
}

export interface ThemeTendance {
  nom: string;
  taille: number;
  part: number;
  discussionIds: number[];
  exemples: string[];
}

export interface Tendances {
  themes: ThemeTendance[];
  parChapitre: SerieStat[];
  suggestionsFaq: DiscussionResume[];
  demandesGenerales: SerieStat[];
  calculeLe: string;
}

export interface ParametresCours {
  coursId?: number;
  ouvert: boolean;
  anonymatAutorise: boolean;
  vigieActive: boolean;
  reponseGeneraleAutorisee: boolean;
  modeExamen: boolean;
  modeExamenFin?: string;
  questionModele?: string;
  dateModification?: string;
}

export interface ApprenantBloque {
  pseudo: string;
  questionsSansReponse: number;
  derniereQuestionLe: string;
  discussionIds: number[];
}

// ---------------------------------------------------------------- Admin

export interface SanctionRequest {
  userId: number;
  type: TypeSanction;
  /** Absent = sans fin. */
  dureeHeures?: number;
  motif: string;
  signalementId?: number;
}

export interface Sanction {
  id: number;
  membre: Auteur;
  type: TypeSanction;
  motif: string;
  dateDebut: string;
  dateFin?: string;
  active: boolean;
  decidePar: number;
  signalementId?: number;
  leveeLe?: string;
}

export interface HistoriqueMembre {
  membre: Auteur;
  messagesMasques: number;
  signalementsRecus: Signalement[];
  sanctions: Sanction[];
  reputation: Reputation;
}

export interface ParametresForum {
  valeurs: Record<string, string>;
}

export interface JournalModeration {
  id: number;
  acteurId: number;
  /** Absent pour la modération automatique (role IA). */
  acteurNom?: string;
  role: string;
  action: ActionModeration;
  cibleType: string;
  cibleId?: number;
  coursId?: number;
  motif?: string;
  creeLe: string;
}

export interface CoursActivite {
  coursId: number;
  coursTitre?: string;
  enseignantId?: number;
  questions: number;
  sansReponse: number;
  tauxResolution: number;
}

export interface AdminStatistiques {
  global: Statistiques;
  signalementsOuverts: number;
  coursActifs: CoursActivite[];
  coursSansReponse: CoursActivite[];
}

export interface Supervision {
  appels: number;
  jetonsEstimes: number;
  parFournisseur: Record<string, number>;
  parNiveau: Partial<Record<NiveauReponseIa, number>>;
  tauxAbstention: number;
  tauxUtile: number;
  injectionsBloquees: number;
  donneesPersonnellesMasquees: number;
  versionPromptActive?: string;
  appelsParJour: SerieStat[];
  sujetsGeneraux: SerieStat[];
}
