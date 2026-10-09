import { Component, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { QUESTIONS, QUIZ, coursParId } from '../../../../core/demo/demo-data';
import { meta } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';

/**
 * Studio : les quiz de l'enseignant, leurs résultats, et l'analyse des questions les plus ratées.
 * À brancher : QuizService.findByEnseignant() et les statistiques de tentatives.
 */
@Component({
  selector: 'app-studio-quiz',
  imports: [RouterLink, MatTableModule, MatMenuModule, MatIconModule, BwButton, PageHeader, Kpi, BwChart],
  templateUrl: './studio-quiz.html',
})
export class StudioQuiz {
  protected readonly colonnes = ['titre', 'questions', 'tentatives', 'moyenne', 'reussite', 'statut', 'actions'];
  protected readonly lignes = QUIZ.map((q, i) => {
    const moyenne = [58, 72, 69, 64, 81, 61][i];
    return {
      ...q,
      cours: coursParId(q.coursId)?.titre ?? '',
      image: meta(q.coursId).image,
      tentatives: [86, 64, 51, 77, 120, 12][i],
      moyenne,
      reussite: Math.min(100, moyenne + 8),
      statut: i === 5 ? 'BROUILLON' : 'PUBLIE',
    };
  });
  protected readonly selection = signal(this.lignes[0]);

  protected readonly distribution = {
    labels: ['0-20', '20-40', '40-60', '60-80', '80-100'],
    series: [{ label: 'Étudiants', data: [4, 17, 29, 24, 12] }],
  };

  /** Taux d'erreur par question du quiz sélectionné (la question 2 est la plus ratée). */
  protected readonly questions = computed(() =>
    QUESTIONS.map((q, i) => ({ ...q, numero: i + 1, erreurs: [62, 71, 18, 55, 33][i] })).sort((a, b) => b.erreurs - a.erreurs),
  );

  protected ton(v: number): string {
    return v >= 70 ? 'ok' : v >= 60 ? 'warn' : 'ko';
  }
}
