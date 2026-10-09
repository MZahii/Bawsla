import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { EVENEMENTS_APPRENANT, EVENEMENTS_CALENDRIER } from '../../core/demo/demo-plus';
import { BwButton } from '../../shared';
import { Calendar, CalendarEvent, PageHeader } from '../../shared/ui';

const TYPES: Record<CalendarEvent['type'], { libelle: string; icone: string }> = {
  cours: { libelle: 'Cours', icone: 'menu_book' },
  quiz: { libelle: 'Quiz', icone: 'quiz' },
  rappel: { libelle: 'Rappel', icone: 'flag' },
  live: { libelle: 'Session en direct', icone: 'videocam' },
  examen: { libelle: 'Examen final', icone: 'assignment' },
};

/**
 * Agenda personnel (apprenant) et calendrier (formateur) : vue mensuelle et liste du jour choisi.
 * Pas de calendrier de classe : l'apprenant voit ses propres rappels (objectif de la semaine, quiz à refaire),
 * les examens finaux qu'il peut passer et les sessions en direct des cours suivis.
 * À brancher : événements agrégés des modules Cours et Quiz (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-agenda',
  imports: [MatIconModule, MatButtonToggleModule, BwButton, Calendar, PageHeader],
  template: `
    <bw-page-header [title]="studio ? 'Calendrier' : 'Agenda'" [subtitle]="studio ? 'Publications prévues, sessions en direct et avis à traiter pour tes cours.' : 'Tes rappels, tes examens disponibles et les sessions en direct de tes cours. Tu avances à ton rythme.'">
      @if (studio) { <button type="button" bwButton="principal"><mat-icon>add</mat-icon> Planifier une session en direct</button> }
      @else {
        <button type="button" bwButton="secondaire"><mat-icon>sync</mat-icon> Synchroniser (iCal)</button>
        <button type="button" bwButton="principal"><mat-icon>add_alert</mat-icon> Ajouter un rappel</button>
      }
    </bw-page-header>

    <div class="layout">
      <section class="card card-pad">
        <div class="head">
          <button type="button" class="icon-btn" aria-label="Mois précédent"><mat-icon>chevron_left</mat-icon></button>
          <h2>Octobre 2026</h2>
          <button type="button" class="icon-btn" aria-label="Mois suivant"><mat-icon>chevron_right</mat-icon></button>
          <span class="grow"></span>
          <mat-button-toggle-group value="mois" hideSingleSelectionIndicator aria-label="Vue">
            <mat-button-toggle value="mois">Mois</mat-button-toggle>
            <mat-button-toggle value="semaine">Semaine</mat-button-toggle>
          </mat-button-toggle-group>
        </div>
        <bw-calendar [annee]="2026" [mois]="9" [events]="events" [aujourdhui]="8" (select)="jour.set($event.jour)" />
        <ul class="legend">
          @for (t of legende; track t.id) { <li><span [class]="'dot ' + t.id"></span>{{ t.libelle }}</li> }
        </ul>
      </section>

      <aside class="card">
        <div class="card-head"><h2>{{ jour() === 8 ? 'Aujourd’hui et après' : jour() + ' octobre' }}</h2></div>
        <ul class="list">
          @for (e of liste(); track $index) {
            <li>
              <span class="d"><strong>{{ e.jour }}</strong><small>oct.</small></span>
              <span [class]="'ic ' + e.type"><mat-icon>{{ types[e.type].icone }}</mat-icon></span>
              <span class="t"><strong class="small">{{ e.titre }}</strong><span class="muted small">{{ types[e.type].libelle }}</span></span>
            </li>
          } @empty {
            <li class="muted small">Rien de prévu ce jour-là.</li>
          }
        </ul>
      </aside>
    </div>
  `,
  styles: `
    .layout { display: grid; gap: var(--space-5); align-items: start; }
    @media (min-width: 1100px) { .layout { grid-template-columns: minmax(0, 1fr) 340px; } }
    .head { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-4); flex-wrap: wrap; }
    .head h2 { margin: 0; font: 600 20px var(--font-heading); }
    .grow { flex: 1; }
    ul { list-style: none; margin: 0; padding: 0; }
    .legend { display: flex; flex-wrap: wrap; gap: var(--space-4); margin-top: var(--space-4); font-size: 14px; }
    .legend li { display: flex; align-items: center; gap: 6px; }
    .dot { width: 10px; height: 10px; border-radius: 2px; background: var(--chart-1); }
    .dot.quiz, .ic.quiz { --c: var(--chart-2); }
    .dot.quiz { background: var(--chart-2); }
    .dot.rappel { background: var(--chart-3); }
    .dot.live { background: var(--chart-4); }
    .dot.examen { background: var(--color-text); }
    .list li { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-bottom: 1px solid var(--color-border); }
    .d { display: flex; flex-direction: column; align-items: center; width: 40px; line-height: 1.1; }
    .d strong { font: 600 18px var(--font-heading); }
    .d small { font-size: 12px; color: var(--color-text-muted); }
    .ic { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 6px; background: var(--color-neutral-soft); color: var(--color-text-muted); }
    .ic mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .t { display: flex; flex-direction: column; flex: 1; }
  `,
})
export class Agenda {
  protected readonly studio = inject(Router).url.startsWith('/studio');
  protected readonly events = (this.studio ? EVENEMENTS_CALENDRIER : EVENEMENTS_APPRENANT) as CalendarEvent[];
  protected readonly types = TYPES;
  protected readonly legende = Object.entries(TYPES).map(([id, t]) => ({ id, libelle: t.libelle }));
  protected readonly jour = signal(8);
  protected readonly liste = computed(() =>
    this.jour() === 8 ? this.events.filter((e) => e.jour >= 8).slice(0, 6) : this.events.filter((e) => e.jour === this.jour()),
  );
}
