/**
 * Engagement : notifications, réputation, badges, profil, classement (miroir des DTO de forum-service).
 */
import { Auteur, CibleType, DiscussionResume, NiveauReputation } from './forum.model';

export type TypeNotification =
  | 'REPONSE'
  | 'COMMENTAIRE'
  | 'MENTION'
  | 'REPONSE_ACCEPTEE'
  | 'APPROBATION'
  | 'VIGIE_PRETE'
  | 'MESSAGE_MASQUE'
  | 'HORS_COURS'
  | 'DEMANDE_GENERALE'
  | 'SIGNALEMENT'
  | 'SANCTION'
  | 'BADGE';

export type CodeBadge =
  | 'PREMIERE_QUESTION'
  | 'PREMIERE_REPONSE'
  | 'BONNE_QUESTION'
  | 'REPONSE_ACCEPTEE'
  | 'APPROUVE_FORMATEUR'
  | 'SECOURISTE'
  | 'ECLAIREUR'
  | 'PEDAGOGUE'
  | 'ASSIDU'
  | 'GARDIEN';

export type RaisonReputation =
  | 'QUESTION_VOTEE'
  | 'REPONSE_VOTEE'
  | 'REPONSE_ACCEPTEE'
  | 'ACCEPTATION'
  | 'APPROBATION_FORMATEUR'
  | 'VIGIE_UTILE'
  | 'VOTE_NEGATIF_RECU'
  | 'VOTE_NEGATIF_DONNE'
  | 'MESSAGE_MASQUE';

export interface NotificationForum {
  id: number;
  type: TypeNotification;
  discussionId?: number;
  reponseId?: number;
  discussionTitre?: string;
  acteur?: Auteur;
  extrait?: string;
  lue: boolean;
  creeLe: string;
}

export interface CompteurNotifications {
  nonLues: number;
}

export interface Badge {
  code: CodeBadge;
  libelle: string;
  description: string;
  obtenu: boolean;
  obtenuLe?: string;
  progression: number;
  objectif: number;
}

export interface EvenementReputation {
  delta: number;
  raison: RaisonReputation;
  cibleType?: CibleType;
  cibleId?: number;
  creeLe: string;
}

export interface Reputation {
  points: number;
  niveau: NiveauReputation;
  niveauLibelle: string;
  prochainNiveau?: NiveauReputation;
  seuilProchainNiveau?: number;
  historique: EvenementReputation[];
  badges: Badge[];
}

/** Carte de survol d'un auteur. */
export interface ProfilForum {
  userId: number;
  nom: string;
  role: string;
  niveau: NiveauReputation;
  niveauLibelle: string;
  points: number;
  badges: Badge[];
  etiquettesFavorites: string[];
  nbQuestions: number;
  nbReponses: number;
  nbReponsesAcceptees: number;
}

export interface Activite {
  questions: DiscussionResume[];
  reponses: DiscussionResume[];
  favoris: DiscussionResume[];
  suivis: DiscussionResume[];
  reputation: Reputation;
}

export interface ClassementEntree {
  rang: number;
  auteur: Auteur;
  points: number;
}
