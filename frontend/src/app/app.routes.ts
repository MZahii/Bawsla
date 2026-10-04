import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

/**
 * Routes racine (socle : toute modification passe par une PR).
 * Chaque module est chargé à la demande depuis features/<module>/<module>.routes.ts.
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
      { path: '', loadComponent: () => import('./pages/home/home').then((m) => m.Home) },
      {
        path: 'users',
        canActivate: [roleGuard('ADMIN')],
        loadChildren: () => import('./features/user/user.routes').then((m) => m.USER_ROUTES),
      },
      { path: 'cours', loadChildren: () => import('./features/cours/cours.routes').then((m) => m.COURS_ROUTES) },
      { path: 'quiz', loadChildren: () => import('./features/quiz/quiz.routes').then((m) => m.QUIZ_ROUTES) },
      { path: 'forum', loadChildren: () => import('./features/forum/forum.routes').then((m) => m.FORUM_ROUTES) },
      { path: 'forbidden', loadComponent: () => import('./pages/forbidden/forbidden').then((m) => m.Forbidden) },
      { path: '**', loadComponent: () => import('./pages/not-found/not-found').then((m) => m.NotFound) },
    ],
  },
];
