import { DatePipe } from '@angular/common';
import { Component, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { AiLoading, BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { CertificatVue } from '../../components/certificat-vue';
import { Certificat } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

type Etat = 'vide' | 'chargement' | 'valide' | 'inconnu';

/**
 * Vérification d'un certificat par son numéro (GET /api/quiz/certificats/verification/{numero}). La réponse ne contient
 * aucune donnée personnelle : seulement le cours, l'examen, la date et le score. Page publique : le catalogue
 * des cours (réservé aux connectés) n'est chargé qu'avec un compte, sinon le titre vient du certificat.
 */
@Component({
  selector: 'app-verifier',
  imports: [DatePipe, FormsModule, MatIconModule, AiLoading, BwButton, PageHeader, CertificatVue],
  template: `
    <bw-page-header title="Vérifier un certificat" subtitle="Entre le numéro inscrit sur le certificat." [crumbs]="[{ label: 'Accueil', link: '/bienvenue' }, { label: 'Vérifier un certificat' }]" />
    <form class="search" (ngSubmit)="verifier()" role="search">
      <label class="field-search grow">
        <mat-icon aria-hidden="true">badge</mat-icon>
        <input name="n" [(ngModel)]="saisie" placeholder="Numéro du certificat" aria-label="Numéro du certificat" autocomplete="off" />
      </label>
      <button type="submit" bwButton="principal">Vérifier</button>
    </form>

    @switch (etat()) {
      @case ('chargement') { <bw-ai-loading message="Vérification…" /> }
      @case ('valide') {
        @if (certificat(); as c) {
          <p class="res ok" role="status"><mat-icon aria-hidden="true">verified</mat-icon> Certificat valide, délivré par Bawsla le {{ c.dateEmission | date: 'dd/MM/yyyy' }}.</p>
          <app-certificat-vue [certificat]="c" [titreCours]="c.titreCours ?? cours.titre(c.coursId)" />
        }
      }
      @case ('inconnu') {
        <p class="res ko" role="status"><mat-icon aria-hidden="true">error</mat-icon> Aucun certificat ne porte le numéro « {{ numero() }} ». Vérifie la saisie.</p>
      }
    }
  `,
  styles: `
    .search { display: flex; flex-wrap: wrap; gap: var(--space-3); max-width: 560px; margin-bottom: var(--space-5); }
    .search .field-search { height: 48px; }
    .res { display: flex; align-items: center; gap: var(--space-2); max-width: 860px; margin: 0 auto var(--space-4); font-weight: 600; }
    .res.ok { color: var(--color-success); }
    .res.ko { color: var(--color-error); }
  `,
})
export class Verifier {
  private readonly router = inject(Router);
  private readonly quizService = inject(QuizService);
  protected readonly cours = inject(CoursTitresService);

  /** Absent sur /verifier : le routeur lie alors undefined, d'où le repli sur ''. */
  readonly numero = input<string | undefined>('');
  protected saisie = '';
  protected readonly etat = signal<Etat>('vide');
  protected readonly certificat = signal<Certificat | null>(null);

  constructor() {
    if (inject(AuthService).isAuthenticated()) this.cours.charger();
    effect(() => {
      const n = (this.numero() ?? '').trim();
      this.saisie = n;
      this.certificat.set(null);
      if (!n) {
        this.etat.set('vide');
        return;
      }
      this.etat.set('chargement');
      this.quizService.verifierCertificat(n).subscribe({
        next: (c) => {
          this.certificat.set(c);
          this.etat.set('valide');
        },
        error: () => this.etat.set('inconnu'),
      });
    });
  }

  protected verifier(): void {
    const n = this.saisie.trim().toUpperCase();
    if (n) this.router.navigate(['/verifier', n]);
  }
}
