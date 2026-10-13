import { Role, User } from '../../../shared/models/user.model';

/** Miroir de UserDto (user-service) tel que vu par l'ADMIN. */
export interface Utilisateur extends User {
  bio: string | null;
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
