import { Routes } from '@angular/router';

/** Routes du module Cours (montées sous /cours). Fichier propriété du membre "cours". */
export const COURS_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/cours-list/cours-list').then((m) => m.CoursList) },
];
