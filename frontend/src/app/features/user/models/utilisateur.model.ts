import { Role, User } from '../../../shared/models/user.model';

/** Miroir de UserDto (user-service) : son propre compte (/me) ou un compte vu par l'ADMIN. */
export interface Utilisateur extends User {
  bio: string | null;
  /** Formateur validé par l'admin (toujours false pour les autres rôles). */
  formateurValide: boolean;
  /** Version de la photo de profil (ms), null sans photo. */
  avatarVersion: number | null;
  /** ISO, null si l'utilisateur ne s'est jamais connecté. */
  derniereConnexion: string | null;
}

/** Miroir de ProfilRequest : ce que l'utilisateur modifie lui-même. */
export interface ProfilModification {
  nom: string;
  prenom: string;
  bio: string;
}

/** Miroir de AdminCreationRequest. */
export interface UtilisateurCreation {
  nom: string;
  prenom: string;
  email: string;
  motDePasse: string;
  role: Role;
  actif: boolean;
}

/** Miroir de AdminModificationRequest. */
export interface UtilisateurModification {
  nom: string;
  prenom: string;
  email: string;
  role: Role;
  actif: boolean;
}

/** Miroir de ParcoursDto : questionnaire d'accueil de l'apprenant. */
export interface Parcours {
  profil: string | null;
  objectif: string | null;
  domaines: string[];
  temps: string | null;
}

export type StatutDemande = 'EN_ATTENTE' | 'ACCEPTEE' | 'REFUSEE';

/** Miroir de DemandeFormateurDto. */
export interface DemandeFormateur {
  id: number;
  statut: StatutDemande;
  expertise: string;
  presentation: string | null;
  lien: string | null;
  motifRefus: string | null;
  dateCreation: string;
  dateTraitement: string | null;
  demandeur: { id: number; nom: string; prenom: string; email: string; role: Role; avatarVersion: number | null };
}

/** Miroir de DemandeFormateurRequest. */
export interface DemandeFormateurEnvoi {
  expertise: string;
  presentation: string;
  lien: string;
}
