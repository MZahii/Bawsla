import { Role } from '../../shared/models/user.model';

export interface MenuItem {
  label: string;
  icon: string;
  route: string;
  /** Phrase courte affichée sur la carte de la page d'accueil. */
  description?: string;
  /** Rôles autorisés ; absent = tous les utilisateurs connectés. */
  roles?: Role[];
}

/** Menu latéral (icônes : DESIGN.md, section 5). Ajouter une entrée ici passe par une PR (fichier du socle). */
export const MENU: MenuItem[] = [
  { label: 'Tableau de bord', icon: 'dashboard', route: '/' },
  {
    label: 'Utilisateurs',
    icon: 'group',
    route: '/users',
    description: 'Gérer les comptes et les rôles.',
    roles: ['ADMIN'],
  },
  { label: 'Cours', icon: 'menu_book', route: '/cours', description: 'Lire, écouter et résumer les cours.' },
  { label: 'Quiz', icon: 'quiz', route: '/quiz', description: 'Tester ses connaissances, notion par notion.' },
  { label: 'Forum', icon: 'forum', route: '/forum', description: 'Poser une question, aider les autres.' },
  {
    label: 'Profil',
    icon: 'person',
    route: '/profil',
    description: "Profil d'apprentissage et recommandations.",
    roles: ['ETUDIANT', 'ENSEIGNANT'],
  },
];
