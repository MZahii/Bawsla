import { Routes } from '@angular/router';

/**
 * Routes du module Cours, une liste par espace (le module reste propriétaire de ces pages).
 * Le catalogue et la fiche d'un cours servent à la fois au visiteur et à l'étudiant.
 */
export const COURS_PUBLIC_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/catalogue/catalogue').then((m) => m.Catalogue), data: { espace: 'public' } },
  { path: ':id', loadComponent: () => import('./pages/cours-detail/cours-detail').then((m) => m.CoursDetail), data: { espace: 'public' } },
];

export const COURS_EXPLORER_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/catalogue/catalogue').then((m) => m.Catalogue), data: { espace: 'etudiant' } },
  { path: ':id', loadComponent: () => import('./pages/cours-detail/cours-detail').then((m) => m.CoursDetail), data: { espace: 'etudiant' } },
];

export const COURS_ETUDIANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/mes-cours/mes-cours').then((m) => m.MesCours) },
  { path: ':id/lecture', loadComponent: () => import('./pages/lecteur/lecteur').then((m) => m.Lecteur) },
];

export const COURS_STUDIO_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/studio-cours/studio-cours').then((m) => m.StudioCours) },
  { path: 'nouveau', loadComponent: () => import('./pages/studio-editeur/studio-editeur').then((m) => m.StudioEditeur) },
  { path: ':id', loadComponent: () => import('./pages/studio-editeur/studio-editeur').then((m) => m.StudioEditeur) },
];
