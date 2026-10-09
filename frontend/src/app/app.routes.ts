import { Routes } from '@angular/router';

import { guestGuard } from './core/auth/auth.guard';
import { espaceGuard, roleGuard } from './core/auth/role.guard';

/**
 * Routes racine (socle : toute modification passe par une PR).
 *
 * Bawsla est une plateforme ouverte (comme Coursera) : n'importe qui s'inscrit, choisit un cours,
 * passe ses quiz et son examen final et obtient un certificat. Pas de classe ni de groupe.
 * Les codes de rôle restent ETUDIANT / ENSEIGNANT (backend) ; à l'écran : « apprenant » et « formateur ».
 *
 * Quatre espaces, chacun avec sa propre coque et son propre menu :
 *  - site public (visiteur)            : /bienvenue, /catalogue, /verifier
 *  - espace apprenant (barre en haut)  : /app/...
 *  - studio formateur (menu clair)     : /studio/...
 *  - back-office admin (menu sombre)   : /admin/...
 * Chaque module expose ses routes par espace dans features/<module>/<module>.routes.ts.
 */
export const routes: Routes = [
  { path: '', pathMatch: 'full', canActivate: [espaceGuard], children: [] },

  // ------------------------------------------------------------ Site public
  {
    path: '',
    loadComponent: () => import('./core/layouts/public/public-shell').then((m) => m.PublicShell),
    children: [
      { path: 'bienvenue', canActivate: [guestGuard], loadComponent: () => import('./pages/public/home/home').then((m) => m.Home) },
      { path: 'catalogue', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_PUBLIC_ROUTES) },
      { path: 'verifier', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_PUBLIC_ROUTES) },
    ],
  },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./pages/auth/login/login').then((m) => m.Login) },
  { path: 'register', canActivate: [guestGuard], loadComponent: () => import('./pages/auth/register/register').then((m) => m.Register) },
  // Questionnaire d'accueil de l'apprenant, juste après l'inscription (facultatif).
  { path: 'demarrer', canActivate: [roleGuard('ETUDIANT')], loadComponent: () => import('./pages/onboarding/onboarding').then((m) => m.Onboarding) },

  // ------------------------------------------------------------ Espace apprenant
  {
    path: 'app',
    canActivate: [roleGuard('ETUDIANT')],
    loadComponent: () => import('./core/layouts/student/student-shell').then((m) => m.StudentShell),
    children: [
      { path: '', loadComponent: () => import('./pages/student-home/student-home').then((m) => m.StudentHome) },
      { path: 'explorer', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_EXPLORER_ROUTES) },
      { path: 'cours', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_ETUDIANT_ROUTES) },
      { path: 'paiement/:id', loadComponent: () => import('./features/cours/pages/paiement/paiement').then((m) => m.Paiement) },
      { path: 'achats', loadComponent: () => import('./features/cours/pages/achats/achats').then((m) => m.Achats) },
      { path: 'evaluations', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_ETUDIANT_ROUTES) },
      { path: 'certificats', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_CERTIFICAT_ROUTES) },
      { path: 'quiz', redirectTo: 'evaluations' },
      { path: 'forum', loadChildren: () => import('./features/forum/forum.routes').then((m) => m.FORUM_ETUDIANT_ROUTES) },
      { path: 'progres', loadComponent: () => import('./features/user/pages/progres/progres').then((m) => m.Progres) },
      { path: 'agenda', loadComponent: () => import('./pages/agenda/agenda').then((m) => m.Agenda) },
      { path: 'profil', loadComponent: () => import('./pages/profile/profile').then((m) => m.Profile) },
    ],
  },

  // ------------------------------------------------------------ Studio formateur
  {
    path: 'studio',
    canActivate: [roleGuard('ENSEIGNANT')],
    loadComponent: () => import('./core/layouts/teacher/teacher-shell').then((m) => m.TeacherShell),
    children: [
      { path: '', loadComponent: () => import('./pages/teacher-home/teacher-home').then((m) => m.TeacherHome) },
      { path: 'cours', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_STUDIO_ROUTES) },
      { path: 'quiz', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_STUDIO_ROUTES) },
      { path: 'apprenants', loadComponent: () => import('./features/user/pages/apprenants/apprenants').then((m) => m.Apprenants) },
      { path: 'avis', loadComponent: () => import('./features/cours/pages/studio-avis/studio-avis').then((m) => m.StudioAvis) },
      { path: 'forum', loadChildren: () => import('./features/forum/forum.routes').then((m) => m.FORUM_STUDIO_ROUTES) },
      { path: 'calendrier', loadComponent: () => import('./pages/agenda/agenda').then((m) => m.Agenda) },
      { path: 'profil', loadComponent: () => import('./pages/profile/profile').then((m) => m.Profile) },
    ],
  },

  // ------------------------------------------------------------ Back-office admin
  {
    path: 'admin',
    canActivate: [roleGuard('ADMIN')],
    loadComponent: () => import('./core/layouts/admin/admin-shell').then((m) => m.AdminShell),
    children: [
      { path: '', loadComponent: () => import('./pages/admin/admin-home').then((m) => m.AdminHome) },
      { path: 'utilisateurs', loadComponent: () => import('./features/user/pages/admin-users/admin-users').then((m) => m.AdminUsers) },
      { path: 'formateurs', loadComponent: () => import('./features/user/pages/admin-formateurs/admin-formateurs').then((m) => m.AdminFormateurs) },
      { path: 'catalogue', loadComponent: () => import('./features/cours/pages/admin-catalogue/admin-catalogue').then((m) => m.AdminCatalogue) },
      { path: 'certificats', loadComponent: () => import('./features/quiz/pages/admin-certificats/admin-certificats').then((m) => m.AdminCertificats) },
      { path: 'ventes', loadComponent: () => import('./features/cours/pages/admin-ventes/admin-ventes').then((m) => m.AdminVentes) },
      { path: 'ia', loadComponent: () => import('./features/user/pages/admin-ia/admin-ia').then((m) => m.AdminIa) },
      { path: 'audit', loadComponent: () => import('./pages/admin/audit').then((m) => m.Audit) },
      { path: 'parametres', loadComponent: () => import('./pages/admin/parametres').then((m) => m.Parametres) },
      { path: 'composants', loadComponent: () => import('./pages/styleguide/styleguide').then((m) => m.Styleguide) },
    ],
  },

  { path: 'forbidden', loadComponent: () => import('./pages/forbidden/forbidden').then((m) => m.Forbidden) },
  { path: '**', loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound) },
];
