import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { CertificatVue } from '../../components/certificat-vue';
import { Certificat } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/** Un certificat de l'apprenant : aperçu, téléchargement (impression en PDF), partage et lien de vérification. */
@Component({
  selector: 'app-certificat',
  imports: [RouterLink, MatIconModule, AiLoading, BwButton, EmptyState, PageHeader, CertificatVue],
  template: `
    @if (chargement()) {
      <bw-ai-loading message="Chargement du certificat…" />
    } @else if (certificat(); as c) {
      <bw-page-header title="Mon certificat" [crumbs]="[{ label: 'Accueil', link: '/app' }, { label: 'Mes certificats', link: '/app/certificats' }, { label: c.numero }]">
        <button type="button" bwButton="secondaire" (click)="copier(c.numero)"><mat-icon>link</mat-icon> {{ copie() ? 'Lien copié' : 'Copier le lien de vérification' }}</button>
        <a bwButton="secondaire" [href]="linkedin(c.numero)" target="_blank" rel="noopener"><mat-icon>share</mat-icon> Ajouter sur LinkedIn</a>
        <button type="button" bwButton="principal" (click)="imprimer()"><mat-icon>download</mat-icon> Télécharger en PDF</button>
      </bw-page-header>
      <app-certificat-vue [certificat]="c" [titreCours]="c.titreCours ?? cours.titre(c.coursId)" [titulaire]="titulaire()" class="print-zone" />
      <p class="muted small center">Un recruteur peut vérifier ce certificat sur la page <a [routerLink]="['/verifier', c.numero]">Vérifier un certificat</a>.</p>
    } @else {
      <bw-empty-state icon="workspace_premium" title="Ce certificat n’existe pas.">
        <a bwButton="principal" routerLink="/app/certificats">Mes certificats</a>
      </bw-empty-state>
    }
  `,
  styles: `.center { text-align: center; margin-top: var(--space-4); }`,
})
export class CertificatPage {
  private readonly quizService = inject(QuizService);
  private readonly auth = inject(AuthService);
  protected readonly cours = inject(CoursTitresService);

  readonly numero = input('');
  protected readonly certificat = signal<Certificat | null>(null);
  protected readonly chargement = signal(true);
  protected readonly copie = signal(false);
  protected readonly titulaire = computed(() => {
    const u = this.auth.currentUser();
    return u ? `${u.prenom} ${u.nom}` : null;
  });

  constructor() {
    this.cours.charger();
    effect(() => {
      const numero = this.numero();
      this.chargement.set(true);
      this.quizService.mesCertificats().subscribe({
        next: (liste) => {
          this.certificat.set(liste.find((c) => c.numero === numero) ?? null);
          this.chargement.set(false);
        },
        error: () => {
          this.certificat.set(null);
          this.chargement.set(false);
        },
      });
    });
  }

  protected imprimer(): void {
    window.print();
  }

  protected copier(numero: string): void {
    const url = `${location.origin}/verifier/${encodeURIComponent(numero)}`;
    navigator.clipboard?.writeText(url).then(() => this.copie.set(true), () => this.copie.set(false));
  }

  protected linkedin(numero: string): string {
    const params = new URLSearchParams({ startTask: 'CERTIFICATION_NAME', name: 'Certificat Bawsla', organizationName: 'Bawsla', certId: numero });
    return `https://www.linkedin.com/profile/add?${params.toString()}`;
  }
}
