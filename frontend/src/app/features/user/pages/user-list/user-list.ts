import { Component, inject, signal } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared/components/empty-state/empty-state';
import { ROLE_LABELS, User } from '../../../../shared/models/user.model';
import { UserService } from '../../services/user.service';

/** Page squelette du module User (ADMIN) : à enrichir par le membre "user". */
@Component({
  selector: 'app-user-list',
  imports: [MatListModule, MatProgressBarModule, EmptyState],
  template: `
    <h1>Utilisateurs</h1>
    @if (loading()) {
      <mat-progress-bar mode="indeterminate" />
    } @else if (error()) {
      <app-empty-state icon="error_outline" title="Erreur" [message]="error()!" />
    } @else if (users().length === 0) {
      <app-empty-state icon="group" title="Aucun utilisateur" />
    } @else {
      <mat-list>
        @for (u of users(); track u.id) {
          <mat-list-item>
            <span matListItemTitle>{{ u.prenom }} {{ u.nom }}</span>
            <span matListItemLine>{{ u.email }} · {{ roleLabels[u.role] }}</span>
          </mat-list-item>
        }
      </mat-list>
    }
  `,
})
export class UserList {
  private readonly service = inject(UserService);

  protected readonly roleLabels = ROLE_LABELS;
  protected readonly users = signal<User[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  constructor() {
    this.service.findAll().subscribe({
      next: (data) => {
        this.users.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
