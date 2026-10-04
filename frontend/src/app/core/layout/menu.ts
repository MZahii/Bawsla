import { Role } from '../../shared/models/user.model';

export interface MenuItem {
  label: string;
  icon: string;
  route: string;
  /** Rôles autorisés ; absent = tous les utilisateurs connectés. */
  roles?: Role[];
}

/** Menu latéral. Ajouter une entrée ici passe par une PR (fichier du socle). */
export const MENU: MenuItem[] = [
  { label: 'Accueil', icon: 'home', route: '/' },
  { label: 'Utilisateurs', icon: 'group', route: '/users', roles: ['ADMIN'] },
  { label: 'Cours', icon: 'menu_book', route: '/cours' },
  { label: 'Quiz', icon: 'quiz', route: '/quiz' },
  { label: 'Forum', icon: 'forum', route: '/forum' },
];
