import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { Certificat, HistoriqueLigne } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/** Mes certificats : ceux obtenus, et les examens déjà tentés mais pas encore réussis. */
@Component({
  selector: 'app-certificats',
  imports: [DatePipe, RouterLink, MatIconModule, AiLoading, BwButton, EmptyState, PageHeader],
  template: `
    <bw-page-header title="Mes certificats" subtitle="Un certificat par examen final réussi. Chacun a un numéro que tout le monde peut vérifier." />

    @if (chargement()) {
      <bw-ai-loading message="Chargement de tes certificats…" />
    } @else if (erreur()) {
      <bw-empty-state icon="error" title="Certificats indisponibles" message="Réessaie dans un instant." />
    } @else {
      @if (certificats().length) {
        <div class="grid">
          @for (c of certificats(); track c.numero) {
            <article class="card cert">
              <div class="b">
                <span class="k"><mat-icon aria-hidden="true">workspace_premium</mat-icon> Certificat</span>
                <h2><a [routerLink]="['/app/certificats', c.numero]">{{ c.titreCours ?? cours.titre(c.coursId) }}</a></h2>
                <p class="muted small">Obtenu le {{ c.dateEmission | date: 'dd/MM/yyyy' }}, score {{ c.score }} %</p>
                <p class="muted small num">N° {{ c.numero }}</p>
              </div>
              <div class="acts">
                <a bwButton="fantome" [routerLink]="['/app/certificats', c.numero]">Voir</a>
                <button type="button" bwButton="fantome" (click)="copier(c.numero)" [attr.aria-label]="'Copier le lien de vérification du certificat ' + c.numero">
                  <mat-icon aria-hidden="true">{{ copie() === c.numero ? 'check' : 'link' }}</mat-icon>
                  {{ copie() === c.numero ? 'Lien copié' : 'Copier le lien' }}
                </button>
              </div>
            </article>
          }
        </div>
      } @else {
        <bw-empty-state icon="workspace_premium" title="Pas encore de certificat" message="Termine un cours et réussis son examen final pour obtenir ton premier certificat." />
      }

      @if (enCours().length) {
        <h2 class="sec">En route vers un certificat</h2>
        <ul class="card list">
          @for (h of enCours(); track h.coursId) {
            <li>
              <span class="t"><strong>{{ cours.titre(h.coursId) }}</strong>
                <span class="muted small">Meilleur score à l’examen : {{ h.pourcentage }} %</span>
              </span>
              <a bwButton="fantome" [routerLink]="['/app/evaluations/examen', h.coursId]">Retenter l’examen</a>
            </li>
          }
        </ul>
      }
    }
  `,
  styles: `
    .grid { display: grid; gap: var(--space-5); grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
    .cert { display: flex; flex-direction: column; overflow: hidden; border-top: 3px solid var(--color-gold-500); }
    .cert h2 a { color: inherit; text-decoration: none; }
    .cert h2 a:hover { text-decoration: underline; }
    .b { flex: 1; padding: var(--space-4); }
    .acts { display: flex; flex-wrap: wrap; gap: var(--space-2); padding: var(--space-2) var(--space-3); border-top: 1px solid var(--color-border); }
    .b p { margin: 2px 0 0; }
    .k { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 700; color: var(--color-gold-700); }
    .k mat-icon { font-size: 18px; width: 18px; height: 18px; }
    h2 { margin: var(--space-1) 0 var(--space-2); font: 600 17px/24px var(--font-heading); }
    .sec { margin: var(--space-7) 0 var(--space-3); font: 600 20px var(--font-heading); }
    .list { margin: 0; padding: 0; list-style: none; }
    .list li { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-bottom: 1px solid var(--color-border); }
    .list li:last-child { border-bottom: 0; }
    .t { display: flex; flex: 1; flex-direction: column; }
  `,
})
export class Certificats {
  private readonly quizService = inject(QuizService);
  protected readonly cours = inject(CoursTitresService);

  protected readonly certificats = signal<Certificat[]>([]);
  private readonly historique = signal<HistoriqueLigne[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal(false);
  /** Numéro du certificat dont le lien vient d'être copié (retour visuel pendant 2 s). */
  protected readonly copie = signal<string | null>(null);

  /** Examens tentés sans certificat : meilleur score par cours. */
  protected readonly enCours = computed(() => {
    const obtenus = new Set(this.certificats().map((c) => c.coursId));
    const meilleurs = new Map<number, HistoriqueLigne>();
    for (const h of this.historique()) {
      if (h.type !== 'EXAMEN' || obtenus.has(h.coursId)) continue;
      const m = meilleurs.get(h.coursId);
      if (!m || h.pourcentage > m.pourcentage) meilleurs.set(h.coursId, h);
    }
    return [...meilleurs.values()];
  });

  constructor() {
    this.cours.charger();
    this.quizService.mesCertificats().subscribe({
      next: (c) => {
        this.certificats.set(c);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set(true);
        this.chargement.set(false);
      },
    });
    this.quizService.historique().subscribe({ next: (h) => this.historique.set(h), error: () => this.historique.set([]) });
  }

  /** Copie le lien public de vérification (/verifier/<numéro>), à coller dans un CV ou un profil. */
  protected copier(numero: string): void {
    navigator.clipboard?.writeText(`${location.origin}/verifier/${numero}`).then(() => {
      this.copie.set(numero);
      setTimeout(() => this.copie.set(null), 2000);
    });
  }
}
