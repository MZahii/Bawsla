import { Component, computed, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { COURS, libellePrix } from '../../../../core/demo/demo-data';
import { CERTIFICATS, dureeTotale, formatDuree, meta } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, ProgressRing } from '../../../../shared/ui';

/**
 * « Mes cours » de l'apprenant : en cours, terminés (examen final à passer ou certificat obtenu),
 * enregistrés pour plus tard (pas encore inscrit).
 * À brancher : inscriptions de l'apprenant (cours-service) et certificats (quiz-service).
 */
@Component({
  selector: 'app-mes-cours',
  imports: [RouterLink, MatTabsModule, MatIconModule, BwButton, EmptyState, PageHeader, ProgressRing],
  templateUrl: './mes-cours.html',
  styleUrl: './mes-cours.scss',
})
export class MesCours {
  protected readonly enCours = COURS.filter((c) => c.inscrit && c.progression < 100);
  protected readonly termines = COURS.filter((c) => c.inscrit && c.progression >= 100);
  protected readonly enregistres = signal(COURS.filter((c) => !c.inscrit && c.id !== 7).slice(0, 2));
  protected readonly certificat = (id: number) => CERTIFICATS.find((x) => x.coursId === id);
  protected readonly prix = libellePrix;
  protected readonly img = (id: number) => meta(id).image;
  protected readonly reste = (c: (typeof COURS)[number]) =>
    formatDuree(c.chapitres.filter((ch) => !ch.fait).reduce((t, ch) => t + ch.duree, 0));
  protected readonly prochain = (c: (typeof COURS)[number]) => c.chapitres.find((ch) => !ch.fait)?.titre ?? '';
  protected readonly total = (c: (typeof COURS)[number]) => formatDuree(dureeTotale(c));
  protected readonly tempsTotal = computed(() => '12 h 40');

  protected retirer(id: number): void {
    this.enregistres.update((l) => l.filter((c) => c.id !== id));
  }
}
