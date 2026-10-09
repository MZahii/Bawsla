import { Component, computed, inject, input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { Router } from '@angular/router';

import { certificatParNumero } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { CertificatVue } from '../../components/certificat-vue';

/**
 * Page publique : un recruteur (ou n'importe qui) vérifie un certificat avec son numéro, sans compte.
 * À brancher : endpoint public GET /api/quiz/certificats/{numero} (à décrire dans CONTRATS_API.md
 * et à ouvrir sans JWT dans la gateway). Il ne renvoie que le nom, le cours, la date et le score.
 */
@Component({
  selector: 'app-verifier',
  imports: [FormsModule, MatIconModule, BwButton, PageHeader, CertificatVue],
  template: `
    <bw-page-header title="Vérifier un certificat" subtitle="Entre le numéro inscrit sur le certificat (par exemple BWS-2026-0412)." [crumbs]="[{ label: 'Accueil', link: '/bienvenue' }, { label: 'Vérifier un certificat' }]" />
    <form class="search" (ngSubmit)="verifier()" role="search">
      <label class="field-search grow">
        <mat-icon aria-hidden="true">badge</mat-icon>
        <input name="n" [(ngModel)]="saisie" placeholder="BWS-2026-0412" aria-label="Numéro du certificat" autocomplete="off" />
      </label>
      <button type="submit" bwButton="principal">Vérifier</button>
    </form>

    @if (numero()) {
      @if (certificat(); as c) {
        <p class="res ok" role="status"><mat-icon aria-hidden="true">verified</mat-icon> Certificat valide, délivré par Bawsla le {{ c.date }}.</p>
        <app-certificat-vue [certificat]="c" />
      } @else {
        <p class="res ko" role="status"><mat-icon aria-hidden="true">error</mat-icon> Aucun certificat ne porte le numéro « {{ numero() }} ». Vérifie la saisie ; un certificat révoqué n’apparaît plus.</p>
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
  readonly numero = input('');
  protected saisie = '';
  protected readonly certificat = computed(() => certificatParNumero(this.numero()));

  constructor() {
    queueMicrotask(() => (this.saisie = this.numero()));
  }

  protected verifier(): void {
    const n = this.saisie.trim().toUpperCase();
    if (n) this.router.navigate(['/verifier', n]);
  }
}
