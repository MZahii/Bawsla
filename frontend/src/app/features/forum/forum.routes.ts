import { Routes } from '@angular/router';

/**
 * Routes du module Forum. Fichier propriété du membre "forum".
 *  - FORUM_ETUDIANT_ROUTES : /app/forum (accueil, nouvelle question, assistant, activité, notifications, fil)
 *  - FORUM_STUDIO_ROUTES   : /studio/forum (studio du formateur, annonce, fil avec outils de modération)
 *  - FORUM_ADMIN_ROUTES    : /admin/forum (espace admin, fil)
 */
const fil = () => import('./pages/forum-thread/forum-thread').then((m) => m.ForumThread);
const nouvelle = () => import('./pages/forum-ask/forum-ask').then((m) => m.ForumAsk);

export const FORUM_ETUDIANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/forum-list/forum-list').then((m) => m.ForumList) },
  { path: 'nouvelle', loadComponent: nouvelle },
  { path: 'assistant', loadComponent: () => import('./pages/forum-assistant/forum-assistant').then((m) => m.ForumAssistant) },
  { path: 'moi', loadComponent: () => import('./pages/forum-activite/forum-activite').then((m) => m.ForumActivite) },
  { path: 'notifications', loadComponent: () => import('./pages/forum-notifications/forum-notifications').then((m) => m.ForumNotifications) },
  { path: ':id', loadComponent: fil },
];

export const FORUM_STUDIO_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/studio-forum/studio-forum').then((m) => m.StudioForum) },
  { path: 'nouvelle', loadComponent: nouvelle },
  { path: 'notifications', loadComponent: () => import('./pages/forum-notifications/forum-notifications').then((m) => m.ForumNotifications) },
  { path: ':id', loadComponent: fil },
];

export const FORUM_ADMIN_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/admin-forum/admin-forum').then((m) => m.AdminForum) },
  { path: ':id', loadComponent: fil },
];
