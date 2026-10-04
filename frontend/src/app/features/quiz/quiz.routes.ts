import { Routes } from '@angular/router';

/** Routes du module Quiz (montées sous /quiz). Fichier propriété du membre "quiz". */
export const QUIZ_ROUTES: Routes = [
  { path: '', loadComponent: () => import('./pages/quiz-list/quiz-list').then((m) => m.QuizList) },
];
