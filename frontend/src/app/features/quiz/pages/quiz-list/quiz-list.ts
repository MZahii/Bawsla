import { Component, inject, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { Quiz } from '../../models/quiz.model';
import { QuizService } from '../../services/quiz.service';

/** Page squelette du module Quiz : à enrichir par le membre "quiz". */
@Component({
  selector: 'app-quiz-list',
  imports: [MatListModule, MatProgressBarModule, EmptyState],
  template: `
    <h1>Quiz</h1>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    } @else if (error()) {
      <app-empty-state icon="error_outline" title="Erreur" [message]="error()!" />
    } @else if (quiz().length === 0) {
      <app-empty-state icon="quiz" title="Aucun quiz pour le moment" />
    } @else {
      <mat-list>
        @for (q of quiz(); track q.id) {
          <mat-list-item>
            <span matListItemTitle>{{ q.titre }}</span>
            <span matListItemLine>Cours #{{ q.coursId }}</span>
          </mat-list-item>
        }
      </mat-list>
    }
  `,
})
export class QuizList {
  private readonly service = inject(QuizService);

  protected readonly quiz = signal<Quiz[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.findAll().subscribe({
      next: (data) => {
        this.quiz.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
