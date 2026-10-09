import { Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { certificatParNumero } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { CertificatVue } from '../../components/certificat-vue';

/**
 * Un certificat de l'apprenant : aperçu, téléchargement (impression en PDF), partage et lien de vérification.
 * À brancher : QuizService.certificat(numero) et le PDF généré par quiz-service.
 */
@Component({
  selector: 'app-certificat',
  imports: [RouterLink, MatIconModule, BwButton, EmptyState, PageHeader, CertificatVue],
  template: `
    @if (certificat(); as c) {
      <bw-page-header title="Mon certificat" [crumbs]="[{ label: 'Accueil', link: '/app' }, { label: 'Mes certificats', link: '/app/certificats' }, { label: c.numero }]">
        <button type="button" bwButton="secondaire" (click)="copier(c.numero)"><mat-icon>link</mat-icon> {{ copie() ? 'Lien copié' : 'Copier le lien de vérification' }}</button>
        <a bwButton="secondaire" [href]="linkedin(c.numero)" target="_blank" rel="noopener"><mat-icon>share</mat-icon> Ajouter sur LinkedIn</a>
        <button type="button" bwButton="principal" (click)="imprimer()"><mat-icon>download</mat-icon> Télécharger en PDF</button>
      </bw-page-header>
      <app-certificat-vue [certificat]="c" class="print-zone" />
      <p class="muted small center">Un recruteur peut vérifier ce certificat sur la page <a [routerLink]="['/verifier', c.numero]">Vérifier un certificat</a>, sans compte.</p>
    } @else {
      <bw-empty-state icon="workspace_premium" title="Ce certificat n’existe pas.">
        <a bwButton="principal" routerLink="/app/certificats">Mes certificats</a>
      </bw-empty-state>
    }
  `,
  styles: `.center { text-align: center; margin-top: var(--space-4); }`,
})
export class CertificatPage {
  readonly numero = input('');
  protected readonly certificat = computed(() => certificatParNumero(this.numero()));
  protected readonly copie = signal(false);

  protected imprimer(): void {
    window.print();
  }

  protected copier(numero: string): void {
    const url = `${location.origin}${location.pathname}#/verifier/${numero}`;
    navigator.clipboard?.writeText(url).then(() => this.copie.set(true), () => this.copie.set(false));
  }

  protected linkedin(numero: string): string {
    const c = this.certificat();
    const params = new URLSearchParams({ startTask: 'CERTIFICATION_NAME', name: 'Certificat Bawsla', organizationName: 'Bawsla', certId: numero });
    return c ? `https://www.linkedin.com/profile/add?${params.toString()}` : '#';
  }
}
