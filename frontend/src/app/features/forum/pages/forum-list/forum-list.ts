import { Component, inject, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { Discussion } from '../../models/discussion.model';
import { ForumService } from '../../services/forum.service';

/** Page squelette du module Forum : à enrichir par le membre "forum". */
@Component({
  selector: 'app-forum-list',
  imports: [MatListModule, MatProgressBarModule, EmptyState],
  template: `
    <h1>Forum</h1>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    } @else if (error()) {
      <app-empty-state icon="error_outline" title="Erreur" [message]="error()!" />
    } @else if (discussions().length === 0) {
      <app-empty-state icon="forum" title="Aucune discussion pour le moment" />
    } @else {
      <mat-list>
        @for (d of discussions(); track d.id) {
          <mat-list-item>
            <span matListItemTitle>{{ d.titre }}</span>
            <span matListItemLine>{{ d.contenu }}</span>
          </mat-list-item>
        }
      </mat-list>
    }
  `,
})
export class ForumList {
  private readonly service = inject(ForumService);

  protected readonly discussions = signal<Discussion[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.findAll().subscribe({
      next: (data) => {
        this.discussions.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
