import { Component, computed, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';

import { BwButton } from '../../../../shared';
import { LangueExplication, ModeExplication } from '../../models/cours.model';
import { ExplicationStudio } from '../explication-studio';
import { FicheStudio } from '../fiche-studio';
import { ResumeLocalStudio } from '../resume-local-studio';
import { TITRE_GENERATION, TypeGeneration } from './studio-ia.modele';

export interface DonneesPanneau {
  type: TypeGeneration;
  coursId: number;
  /** Réglages préremplis (menu « Régénérer » d'une explication). */
  langue?: LangueExplication;
  mode?: ModeExplication;
  /** Ouvert par « Régénérer » : rappelle de cliquer sur le bouton du composant. */
  regenerer?: boolean;
}

/**
 * Contenu du volet latéral du Studio IA : réutilise tels quels les composants existants (résumé local, fiche, explication), qui gardent
 * leurs options, leur bouton « Générer », leur suivi et leurs règles de validation.
 */
@Component({
  selector: 'app-studio-ia-panneau',
  imports: [MatDialogModule, MatIconModule, BwButton, ResumeLocalStudio, FicheStudio, ExplicationStudio],
  template: `
    <header class="tete">
      <mat-icon class="boussole" aria-hidden="true">explore</mat-icon>
      <h2 mat-dialog-title id="studio-ia-titre">{{ titre() }}</h2>
      <button type="button" bwButton="fantome" mat-dialog-close aria-label="Fermer le panneau"><mat-icon aria-hidden="true">close</mat-icon></button>
    </header>
    <mat-dialog-content>
      @if (donnees.regenerer) {
        <p class="info" role="note">
          <mat-icon aria-hidden="true">refresh</mat-icon>
          <span>Pour relancer, clique sur « Régénérer » : les réglages de cette génération sont déjà sélectionnés.</span>
        </p>
      }
      @switch (donnees.type) {
        @case ('resume') {
          <app-resume-local-studio [coursId]="donnees.coursId" />
        }
        @case ('fiche') {
          <app-fiche-studio [coursId]="donnees.coursId" />
        }
        @case ('explication') {
          <app-explication-studio [coursId]="donnees.coursId" [langueInitiale]="donnees.langue ?? 'fr'" [modeInitial]="donnees.mode ?? 'monologue'" />
        }
      }
    </mat-dialog-content>
  `,
  styles: `
    :host { display: block; }
    .tete { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-4) 0; }
    .tete h2 { flex: 1; margin: 0; padding: 0; font: 600 20px var(--font-heading); }
    .boussole { color: var(--color-gold-700); }
    mat-dialog-content { padding-top: var(--space-4); }
    .info { display: flex; align-items: flex-start; gap: var(--space-2); margin: 0 0 var(--space-4); padding: var(--space-3); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-gold-100); color: var(--color-gold-700); font-size: 14px; mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class StudioIaPanneau {
  protected readonly donnees = inject<DonneesPanneau>(MAT_DIALOG_DATA);
  protected readonly titre = computed(() => TITRE_GENERATION[this.donnees.type]);
}
