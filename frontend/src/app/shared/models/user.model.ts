export type Role = 'ADMIN' | 'ENSEIGNANT' | 'ETUDIANT';

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrateur',
  ENSEIGNANT: 'Enseignant',
  ETUDIANT: 'Étudiant',
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
