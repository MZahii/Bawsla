export type Role = 'ADMIN' | 'ENSEIGNANT' | 'ETUDIANT';

/**
 * Libellés affichés. Les codes ETUDIANT / ENSEIGNANT restent ceux du backend (CONTRATS_API.md) :
 * à l'écran on parle d'« apprenant » et de « formateur », car Bawsla est ouvert à tous.
 */
export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  ENSEIGNANT: 'Formateur',
  ETUDIANT: 'Apprenant',
};

export interface User {
  id: number;
  nom: string;
  prenom: string;
  email: string;
  role: Role;
  actif: boolean;
  dateCreation: string;
}

export interface LoginRequest {
  email: string;
  motDePasse: string;
}

export interface RegisterRequest {
  nom: string;
  prenom: string;
  email: string;
  motDePasse: string;
  role: Exclude<Role, 'ADMIN'>;
}

export interface AuthResponse {
  token: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}
