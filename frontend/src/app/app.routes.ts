import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guard';
import { devOrAdminGuard } from './core/auth/dev-or-admin.guard';
import { roleGuard } from './core/auth/role.guard';

/**
 * Routes racine (socle : toute modification passe par une PR).
 * Chaque module est chargé à la demande depuis features/<module>/<module>.routes.ts.
 * `data.title` alimente le titre affiché dans l'en-tête du layout.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    loadComponent: () => import('./pages/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    loadComponent: () => import('./core/layout/layout').then((m) => m.Layout),
    canActivate: [authGuard],
    children: [
      {
        path: '',
        data: { title: 'Tableau de bord' },
        loadComponent: () => import('./pages/home/home').then((m) => m.Home),
      },
      {
        path: 'users',
        canActivate: [roleGuard('ADMIN')],
        loadChildren: () => import('./features/user/user.routes').then((m) => m.USER_ROUTES),
      },
      { path: 'cours', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_ROUTES) },
      { path: 'quiz', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_ROUTES) },
      { path: 'forum', loadChildren: () => import('./features/forum/forum.routes').then((m) => m.FORUM_ROUTES) },
      {
        // Emplacement réservé : le module user remplacera ce composant par sa page de profil (PR).
        path: 'profil',
        data: { title: 'Profil' },
        canActivate: [roleGuard('ETUDIANT', 'ENSEIGNANT')],
        loadComponent: () => import('./pages/profile/profile').then((m) => m.Profile),
      },
      {
        path: 'styleguide',
        data: { title: 'Charte graphique' },
        canActivate: [devOrAdminGuard],
        loadComponent: () => import('./pages/styleguide/styleguide').then((m) => m.Styleguide),
      },
      {
        path: 'forbidden',
        data: { title: 'Accès refusé' },
        loadComponent: () => import('./pages/forbidden/forbidden').then((m) => m.Forbidden),
      },
      {
        path: '**',
        data: { title: 'Page introuvable' },
        loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
