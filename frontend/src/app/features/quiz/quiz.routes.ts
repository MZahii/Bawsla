import { Routes } from '@angular/router';

/**
 * Routes du module Quiz. Fichier propriété du membre "quiz".
 * Le module couvre toutes les évaluations : quiz de chapitre, examen final et certificat.
 *  - QUIZ_ETUDIANT_ROUTES   : montées sous /app/evaluations (espace apprenant)
 *  - QUIZ_CERTIFICAT_ROUTES : montées sous /app/certificats (certificats de l'apprenant)
 *  - QUIZ_PUBLIC_ROUTES     : montées sous /verifier (vérification publique d'un certificat)
 *  - QUIZ_STUDIO_ROUTES     : montées sous /studio/quiz (studio formateur)
 */
export const QUIZ_ETUDIANT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/quiz-list/quiz-list').then((m) => m.QuizList) },
  { path: 'examen/:coursId', loadComponent: () => import('./pages/examen-final/examen-final').then((m) => m.ExamenFinalPage) },
  { path: ':id', loadComponent: () => import('./pages/quiz-player/quiz-player').then((m) => m.QuizPlayer) },
];

export const QUIZ_CERTIFICAT_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/certificats/certificats').then((m) => m.Certificats) },
  { path: ':numero', loadComponent: () => import('./pages/certificat/certificat').then((m) => m.CertificatPage) },
];

export const QUIZ_PUBLIC_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/verifier/verifier').then((m) => m.Verifier) },
  { path: ':numero', loadComponent: () => import('./pages/verifier/verifier').then((m) => m.Verifier) },
];

export const QUIZ_STUDIO_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/studio-quiz/studio-quiz').then((m) => m.StudioQuiz) },
  { path: 'generer', loadComponent: () => import('./pages/quiz-generateur/quiz-generateur').then((m) => m.QuizGenerateur) },
];
