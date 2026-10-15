/**
 * Modèles du forum : miroir des DTO de forum-service (package dto). Dates en ISO-8601, champs null omis.
 */

export type TypeDiscussion = 'QUESTION' | 'DISCUSSION' | 'ANNONCE';
export type StatutDiscussion = 'OUVERTE' | 'RESOLUE' | 'FERMEE';
export type CibleType = 'DISCUSSION' | 'REPONSE' | 'COMMENTAIRE';
export type TypeReaction = 'MERCI' | 'APPRIS' | 'PILE';
export type NiveauReputation = 'MOUSSE' | 'MATELOT' | 'NAVIGATEUR' | 'CARTOGRAPHE' | 'CAPITAINE';

export type FiltreDiscussions = 'miennes' | 'repondues' | 'suivies' | 'favoris' | 'non-lues';
export type TriDiscussions = 'activite' | 'recentes' | 'votes' | 'sans-reponse' | 'pertinence';
export type ModeRecherche = 'semantique' | 'lexical';

/** Page de résultats (« Charger plus » tant que suivante vaut true). */
export interface PageResponse<T> {
  contenu: T[];
  page: number;
  taille: number;
  total: number;
  suivante: boolean;
}

/** Auteur d'un message ; anonyme = true et pas d'id pour un message anonyme vu par un autre apprenant. */
export interface Auteur {
  id?: number;
  nom?: string;
  role?: string;
  niveau?: NiveauReputation;
  niveauLibelle?: string;
  points?: number;
  anonyme: boolean;
  formateurDuCours: boolean;
}

/** Actions permises (confort d'affichage : le backend revérifie tout). */
export interface Droits {
  modifier: boolean;
  supprimer: boolean;
  repondre: boolean;
  accepter: boolean;
  moderer: boolean;
}

export interface DiscussionResume {
  id: number;
  coursId: number;
  coursTitre?: string;
  chapitreId?: number;
  page?: number;
  type: TypeDiscussion;
  titre: string;
  extrait: string;
  statut: StatutDiscussion;
  epinglee: boolean;
  verrouillee: boolean;
  faq: boolean;
  score: number;
  nbReponses: number;
  nbVues: number;
  etiquettes: string[];
  auteur: Auteur;
  dernierActeur?: Auteur;
  derniereActiviteLe: string;
  creeLe: string;
  nonLue: boolean;
  approuvee: boolean;
  vigie: boolean;
  pertinence?: number;
}

export interface DiscussionDetail {
  id: number;
  coursId: number;
  coursTitre?: string;
  chapitreId?: number;
  page?: number;
  type: TypeDiscussion;
  titre: string;
  contenu: string;
  statut: StatutDiscussion;
  epinglee: boolean;
  verrouillee: boolean;
  masquee: boolean;
  faq: boolean;
  reponseAccepteeId?: number;
  fusionneeDansId?: number;
  score: number;
  monVote: number;
  nbReponses: number;
  nbVues: number;
  nbAbonnes: number;
  etiquettes: string[];
  auteur: Auteur;
  creeLe: string;
  modifieLe?: string;
  derniereActiviteLe: string;
  suivie: boolean;
  favori: boolean;
  droits: Droits;
  reponses: Reponse[];
  commentaires: Commentaire[];
  piecesJointes: PieceJointe[];
}

export interface Reponse {
  id: number;
  discussionId: number;
  contenu: string;
  auteur: Auteur;
  score: number;
  monVote: number;
  acceptee: boolean;
  approuvee: boolean;
  approuveePar?: Auteur;
  approuveeLe?: string;
  masquee: boolean;
  reactions: Partial<Record<TypeReaction, number>>;
  mesReactions: TypeReaction[];
  commentaires: Commentaire[];
  creeLe: string;
  modifieLe?: string;
  droits: Droits;
}

export interface Commentaire {
  id: number;
  discussionId: number;
  reponseId?: number;
  contenu: string;
  auteur: Auteur;
  masque: boolean;
  creeLe: string;
  modifieLe?: string;
  peutModifier: boolean;
}

export interface PieceJointe {
  id: number;
  url: string;
  type: string;
  taille: number;
}

export interface VersionDiscussion {
  titre: string;
  contenu: string;
  modifieLe: string;
  modifieParId: number;
}

export interface Etiquette {
  id: number;
  libelle: string;
  nbUtilisations: number;
}

/** Proximité affichée en clair : « très proche », « proche ». */
export interface Similaire {
  discussionId: number;
  titre: string;
  extrait: string;
  statut: StatutDiscussion;
  nbReponses: number;
  score: number;
  proximite: 'TRES_PROCHE' | 'PROCHE';
}

/** semantique = false : recherche par mots-clés seulement (embeddings indisponibles). */
export interface SimilairesResponse {
  similaires: Similaire[];
  semantique: boolean;
  doublonId?: number;
}

export interface VoteResponse {
  cibleType: CibleType;
  cibleId: number;
  score: number;
  monVote: number;
}

export interface ReactionsResponse {
  reponseId: number;
  compteurs: Partial<Record<TypeReaction, number>>;
  miennes: TypeReaction[];
}

// ---------------------------------------------------------------- Requêtes

export interface DiscussionRequest {
  coursId: number;
  chapitreId?: number;
  page?: number;
  type: TypeDiscussion;
  titre: string;
  contenu: string;
  etiquettes: string[];
  anonyme: boolean;
  /** Publier malgré l'avertissement « doublon probable » ou « données personnelles ». */
  confirmer: boolean;
  /** Publication depuis l'assistant privé : la réponse de La Vigie est jointe au fil. */
  conversationId?: number;
}

export interface DiscussionModificationRequest {
  titre: string;
  contenu: string;
  etiquettes: string[];
}

export interface DiscussionsFiltres {
  coursId?: number;
  chapitreId?: number;
  type?: TypeDiscussion;
  statut?: StatutDiscussion | 'SANS_REPONSE';
  etiquette?: string;
  filtre?: FiltreDiscussions;
  tri?: TriDiscussions;
  q?: string;
  mode?: ModeRecherche;
  page?: number;
  taille?: number;
}

export interface SimilairesRequest {
  coursId: number;
  texte: string;
  exclureId?: number;
}

export interface ReponseRequest {
  contenu: string;
  anonyme: boolean;
}

export interface CommentaireRequest {
  contenu: string;
}

/** valeur 0 = annuler son vote. */
export interface VoteRequest {
  cibleType: Exclude<CibleType, 'COMMENTAIRE'>;
  cibleId: number;
  valeur: -1 | 0 | 1;
}
