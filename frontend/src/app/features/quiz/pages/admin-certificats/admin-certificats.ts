import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { Kpi, PageHeader } from '../../../../shared/ui';
import { Certificat } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/** Back-office : tous les certificats délivrés (GET /api/quiz/certificats, réservé à l'admin). */
@Component({
  selector: 'app-admin-certificats',
  imports: [DatePipe, FormsModule, RouterLink, MatIconModule, BwButton, EmptyState, Kpi, PageHeader],
  template: `
    <bw-page-header title="Certificats" subtitle="Tous les certificats délivrés après réussite d’un examen final." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Certificats' }]">
      <a bwButton="secondaire" routerLink="/verifier"><mat-icon>open_in_new</mat-icon> Page de vérification</a>
    </bw-page-header>

    @if (erreur()) {
      <bw-empty-state icon="error" title="Certificats indisponibles" message="Réessaie dans un instant." />
    } @else {
      <div class="grid-4">
        <bw-kpi label="Certificats délivrés" [value]="'' + certificats().length" icon="workspace_premium" />
        <bw-kpi label="Score moyen" [value]="moyenne() == null ? '—' : moyenne() + ' %'" icon="percent" />
        <bw-kpi label="Cours certifiants" [value]="'' + nbCours()" icon="school" />
        <bw-kpi label="Apprenants certifiés" [value]="'' + nbApprenants()" icon="group" />
      </div>

      <section class="card mt">
        <div class="card-head">
          <label class="field-search grow">
            <mat-icon aria-hidden="true">search</mat-icon>
            <input [ngModel]="q()" (ngModelChange)="q.set($event)" placeholder="Numéro ou cours" aria-label="Rechercher un certificat" />
          </label>
        </div>
        <div class="table-wrap">
          <table class="simple">
            <thead><tr><th>Numéro</th><th>Titulaire</th><th>Cours</th><th>Date</th><th>Score</th><th><span class="bw-visually-hidden">Actions</span></th></tr></thead>
            <tbody>
              @for (c of liste(); track c.numero) {
                <tr>
                  <td class="num">{{ c.numero }}</td>
                  <td>Apprenant n° {{ c.etudiantId }}</td>
                  <td class="small">{{ cours.titre(c.coursId) }}</td>
                  <td class="num muted">{{ c.dateEmission | date: 'dd/MM/yyyy' }}</td>
                  <td class="num">{{ c.score }} %</td>
                  <td><a class="icon-btn" [routerLink]="['/verifier', c.numero]" [attr.aria-label]="'Vérifier ' + c.numero"><mat-icon>verified</mat-icon></a></td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="muted">{{ chargement() ? 'Chargement…' : 'Aucun certificat ne correspond.' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    }
  `,
  styles: `
    .grow { flex: 1; }
    .simple { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { padding: var(--space-3) var(--space-4); text-align: left; font: 600 13px var(--font-body); color: var(--color-text-muted); background: var(--color-bg); white-space: nowrap; }
    td { padding: 10px var(--space-4); border-top: 1px solid var(--color-border); }
  `,
})
export class AdminCertificats {
  private readonly quizService = inject(QuizService);
  protected readonly cours = inject(CoursTitresService);

  protected readonly q = signal('');
  protected readonly certificats = signal<Certificat[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal(false);

  protected readonly liste = computed(() => {
    const q = this.q().trim().toLowerCase();
    return this.certificats().filter((c) => !q || `${c.numero} ${this.cours.titre(c.coursId)}`.toLowerCase().includes(q));
  });
  protected readonly moyenne = computed(() => {
    const l = this.certificats();
    return l.length ? Math.round(l.reduce((s, c) => s + c.score, 0) / l.length) : null;
  });
  protected readonly nbCours = computed(() => new Set(this.certificats().map((c) => c.coursId)).size);
  protected readonly nbApprenants = computed(() => new Set(this.certificats().map((c) => c.etudiantId)).size);

  constructor() {
    this.cours.charger();
    this.quizService.tousLesCertificats().subscribe({
      next: (c) => {
        this.certificats.set(c);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set(true);
        this.chargement.set(false);
      },
    });
  }
}
