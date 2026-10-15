/**
 * La Vigie, l'IA du forum : miroir des DTO de forum-service. Tout contenu affiché dans bw-ai-box.
 */
import { Similaire } from './forum.model';

export type NiveauReponseIa = 'COURS' | 'FORUM' | 'GENERAL';
export type StatutReponseIa = 'EN_ATTENTE' | 'GENEREE' | 'VALIDEE' | 'CORRIGEE' | 'REJETEE' | 'HORS_COURS' | 'ERREUR';
export type ModeVigie = 'DIRECTE' | 'GUIDEE' | 'GENERAL';
export type NiveauConfiance = 'ELEVEE' | 'MOYENNE' | 'FAIBLE';
export type StatutIndex = 'A_JOUR' | 'EN_COURS' | 'ERREUR';

/** Source numérotée [n] : COURS (chapitre, page, lien vers le lecteur) ou FORUM (réponse validée d'un fil). */
export interface SourceVigie {
  numero: number;
  origine: Exclude<NiveauReponseIa, 'GENERAL'>;
  coursId?: number;
  cours?: string;
  chapitreId?: number;
  chapitre?: string;
  page?: number;
  passage: string;
  citation?: string;
  discussionId?: number;
  lien?: string;
}

/** Réponse de La Vigie. confiance absente pour une réponse générale (niveau GENERAL). */
export interface VigieResponse {
  id: number;
  discussionId?: number;
  conversationId?: number;
  niveau: NiveauReponseIa;
  statut: StatutReponseIa;
  mode: ModeVigie;
  texte?: string;
  texteCorrige?: string;
  sources: SourceVigie[];
  indices: string[];
  incertitudes: string[];
  aVerifier: string[];
  confiance?: number;
  niveauConfiance?: NiveauConfiance;
  verifieeParFormateur: boolean;
  utileOui: number;
  utileNon: number;
  monAvis?: boolean;
  questionsSimilaires: Similaire[];
  avertissement?: string;
  versionPrompt?: string;
  fournisseur?: string;
  creeLe: string;
}

export interface MessageVigie {
  id: number;
  role: 'APPRENANT' | 'VIGIE';
  texte: string;
  reponse?: VigieResponse;
  creeLe: string;
}

export interface ConversationVigie {
  id: number;
  coursId: number;
  coursTitre?: string;
  titre?: string;
  creeLe: string;
  derniereActiviteLe: string;
  messages: MessageVigie[];
}

export interface MessageVigieRequest {
  texte: string;
  mode: ModeVigie;
}

/** « Publier comme question » : brouillon pré-rempli, relu avant publication. */
export interface BrouillonQuestion {
  coursId: number;
  conversationId: number;
  titre: string;
  contenu: string;
  etiquettes: string[];
  similaires: Similaire[];
}

export interface ResumeFil {
  discussionId: number;
  puces: string[];
  nbMessages: number;
  versionPrompt?: string;
  creeLe: string;
}

export interface AmeliorerRequest {
  coursId: number;
  titre: string;
  contenu?: string;
}

export interface AmeliorerResponse {
  titre: string;
  manques: string[];
  etiquettes: string[];
}

export interface IndexCours {
  coursId: number;
  statut: StatutIndex;
  nbPassages: number;
  modeleEmbedding?: string;
  indexeLe?: string;
  sourceDemo: boolean;
  erreur?: string;
}

/** La Vigie dans un fil : réponse principale (cours ou forum) et, à part, la réponse générale demandée. */
export interface VigieFil {
  principale?: VigieResponse;
  generale?: VigieResponse;
  /** Le bouton « Voir une réponse générale (hors cours) » peut être proposé. */
  generaleDisponible: boolean;
}

/** Studio : réponse de La Vigie d'un fil du cours, à vérifier ou déjà décidée. */
export interface VigieAVerifier {
  discussionId: number;
  discussionTitre: string;
  reponse: VigieResponse;
}

export type FiltreVigie = 'A_VERIFIER' | 'VALIDEE' | 'CORRIGEE' | 'REJETEE' | 'TOUTES';
