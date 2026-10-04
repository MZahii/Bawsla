import { Routes } from '@angular/router';

/** Routes du module Forum (montées sous /forum). Fichier propriété du membre "forum". */
export const FORUM_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/forum-list/forum-list').then((m) => m.ForumList) },
];
