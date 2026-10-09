import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { DISCUSSIONS, coursParId } from '../../../../core/demo/demo-data';
import { SIGNALEMENTS_FORUM } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader } from '../../../../shared/ui';

/**
 * Studio : modération du forum par l'enseignant. Questions sans réponse humaine, réponses de l'IA à
 * confirmer, et signalements à traiter.
 * À brancher : ForumService (discussions par cours de l'enseignant, signalements, masquage).
 */
@Component({
  selector: 'app-moderation',
  imports: [RouterLink, MatIconModule, MatTabsModule, BwButton, EmptyState, Avatar, Kpi, PageHeader],
  templateUrl: './moderation.html',
})
export class Moderation {
  private readonly snack = inject(MatSnackBar);

  protected readonly aTraiter = signal(
    DISCUSSIONS.filter((d) => !d.resolue).map((d, i) => ({ ...d, cours: coursParId(d.coursId)?.titre ?? '', confiance: [74, 58][i] ?? 60 })),
  );
  protected readonly signalements = signal(SIGNALEMENTS_FORUM);
  protected readonly toutes = DISCUSSIONS.map((d) => ({ ...d, cours: coursParId(d.coursId)?.titre ?? '' }));

  protected confirmer(id: number): void {
    this.aTraiter.update((l) => l.filter((d) => d.id !== id));
    this.snack.open('Réponse de l’IA confirmée et marquée comme vérifiée', 'OK', { duration: 2500 });
  }

  protected traiter(s: (typeof SIGNALEMENTS_FORUM)[number], action: string): void {
    this.signalements.update((l) => l.filter((x) => x !== s));
    this.snack.open(action, 'Annuler', { duration: 3000 });
  }
}
