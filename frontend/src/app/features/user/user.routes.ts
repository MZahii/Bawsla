import { Routes } from '@angular/router';

/** Routes du module User (montées sous /users, ADMIN). Fichier propriété du membre "user". */
export const USER_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/user-list/user-list').then((m) => m.UserList) },
];
