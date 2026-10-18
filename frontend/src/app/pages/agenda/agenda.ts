import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { EmptyState } from '../../shared';
import { Calendar, PageHeader } from '../../shared/ui';

/**
 * Agenda personnel (apprenant) et calendrier (formateur) : vue mensuelle et jour choisi.
 * Aucun service ne fournit encore d'événements (rappels, sessions en direct, échéances) :
 * le calendrier est affiché vide, sans données inventées, en attendant un endpoint décrit dans CONTRATS_API.md.
 */
@Component({
  selector: 'app-agenda',
  imports: [MatIconModule, EmptyState, Calendar, PageHeader],
  template: `
    <bw-page-header [title]="studio ? 'Calendrier' : 'Agenda'" [subtitle]="studio ? 'Les échéances de tes cours, au fil des mois.' : 'Tes rappels et tes échéances, au fil des mois. Tu avances à ton rythme.'" />

    <div class="layout">
      <section class="card card-pad">
        <div class="head">
          <button type="button" class="icon-btn" aria-label="Mois précédent" (click)="decaler(-1)"><mat-icon>chevron_left</mat-icon></button>
          <h2>{{ titreMois() }}</h2>
          <button type="button" class="icon-btn" aria-label="Mois suivant" (click)="decaler(1)"><mat-icon>chevron_right</mat-icon></button>
        </div>
        <bw-calendar [annee]="annee()" [mois]="mois()" [events]="[]" [aujourdhui]="jourCourant()" (select)="jour.set($event.jour)" />
      </section>

      <aside class="card">
        <div class="card-head"><h2>{{ titreJour() }}</h2></div>
        <bw-empty-state
          title="Aucun événement prévu"
          [message]="studio ? 'Les sessions en direct et les échéances de tes cours apparaîtront ici dès qu’elles seront disponibles.' : 'Tes rappels et les examens à passer apparaîtront ici dès qu’ils seront disponibles.'"
        />
      </aside>
    </div>
  `,
  styles: `
    .layout { display: grid; gap: var(--space-5); align-items: start; }
    @media (min-width: 1100px) { .layout { grid-template-columns: minmax(0, 1fr) 340px; } }
    .head { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-4); flex-wrap: wrap; }
    .head h2 { margin: 0; font: 600 20px var(--font-heading); text-transform: capitalize; }
  `,
})
export class Agenda {
  private readonly maintenant = new Date();
  protected readonly studio = inject(Router).url.startsWith('/studio');
  protected readonly annee = signal(this.maintenant.getFullYear());
  protected readonly mois = signal(this.maintenant.getMonth());
  protected readonly jour = signal<number | null>(null);

  /** Jour à mettre en évidence : seulement si le mois affiché est le mois courant. */
  protected readonly jourCourant = computed(() =>
    this.annee() === this.maintenant.getFullYear() && this.mois() === this.maintenant.getMonth() ? this.maintenant.getDate() : null,
  );
  protected readonly titreMois = computed(() =>
    new Date(this.annee(), this.mois(), 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }),
  );
  protected readonly titreJour = computed(() => {
    const j = this.jour();
    if (j === null) return this.jourCourant() !== null ? 'Aujourd’hui et après' : this.titreMois();
    return new Date(this.annee(), this.mois(), j).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  });

  protected decaler(delta: number): void {
    const d = new Date(this.annee(), this.mois() + delta, 1);
    this.annee.set(d.getFullYear());
    this.mois.set(d.getMonth());
    this.jour.set(null);
  }
}
