import { Routes } from '@angular/router';

/**
 * Routes du module Forum. Fichier propriété du membre "forum".
 *  - FORUM_ETUDIANT_ROUTES : montées sous /app/forum (questions, fil, nouvelle question)
 *  - FORUM_STUDIO_ROUTES   : montées sous /studio/forum (modération par l'enseignant)
 */
export const FORUM_ETUDIANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/forum-list/forum-list').then((m) => m.ForumList) },
  { path: 'nouvelle', loadComponent: () => import('./pages/forum-ask/forum-ask').then((m) => m.ForumAsk) },
  { path: ':id', loadComponent: () => import('./pages/forum-thread/forum-thread').then((m) => m.ForumThread) },
];

export const FORUM_STUDIO_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/moderation/moderation').then((m) => m.Moderation) },
  { path: ':id', loadComponent: () => import('./pages/forum-thread/forum-thread').then((m) => m.ForumThread) },
];
