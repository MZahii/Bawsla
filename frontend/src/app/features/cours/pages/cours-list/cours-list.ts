import { Component, inject, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { Cours } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';

/** Page squelette du module Cours : à enrichir par le membre "cours". */
@Component({
  selector: 'app-cours-list',
  imports: [MatListModule, MatProgressBarModule, EmptyState],
  template: `
    <h1>Cours</h1>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    } @else if (error()) {
      <app-empty-state icon="error_outline" title="Erreur" [message]="error()!" />
    } @else if (cours().length === 0) {
      <app-empty-state icon="menu_book" title="Aucun cours pour le moment" />
    } @else {
      <mat-list>
        @for (c of cours(); track c.id) {
          <mat-list-item>
            <span matListItemTitle>{{ c.titre }}</span>
            <span matListItemLine>{{ c.categorie ?? 'Sans catégorie' }} · {{ c.niveau ?? 'Niveau non précisé' }}</span>
          </mat-list-item>
        }
      </mat-list>
    }
  `,
})
export class CoursList {
  private readonly service = inject(CoursService);

  protected readonly cours = signal<Cours[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.findAll().subscribe({
      next: (data) => {
        this.cours.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
