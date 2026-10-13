import { Component, input, signal } from '@angular/core';

import { AiBox } from '../../../shared';
import { Fiche } from '../models/cours.model';
import { FicheAudio } from './fiche-audio';
import { FicheCartes } from './fiche-cartes/fiche-cartes';
import { VueFiche } from './fiche-cartes/fiche-cartes.modele';
import { FicheVueBascule } from './fiche-cartes/fiche-vue-bascule';
import { FicheContenu } from './fiche-contenu';

/**
 * Fiche de révision VALIDÉE, en lecture seule, pour l'apprenant : résumé, mots-clés, notions et audio, sans score ni alertes
 * (le serveur ne les renvoie pas). Deux affichages : « Vue fiches » (cartes, par défaut) et « Vue liste » (inchangée).
 */
@Component({
  selector: 'app-fiche-lecteur',
  imports: [AiBox, FicheAudio, FicheCartes, FicheContenu, FicheVueBascule],
  template: `
    <bw-ai-box heading="Fiche de révision">
      <app-fiche-vue-bascule [vue]="vue()" (vueChange)="vue.set($event)" />
      @if (vue() === 'cartes') {
        <app-fiche-cartes [fiche]="fiche()" />
      } @else {
        <app-fiche-contenu [fiche]="fiche()" />
      }
      <span bwAiSource>Source : {{ titreCours() }}, relu et validé par le formateur</span>
    </bw-ai-box>
    <app-fiche-audio class="audio-fiche" [coursId]="coursId()" [version]="fiche().id" [serveur]="fiche().aAudio" [fournisseur]="fiche().fournisseurAudio" [texte]="fiche().resume" />
  `,
  styles: `
    :host { display: block; }
    .audio-fiche { margin-top: var(--space-3); }
  `,
})
export class FicheLecteur {
  readonly coursId = input.required<number>();
  readonly fiche = input.required<Fiche>();
  readonly titreCours = input('');
  /** Vue fiches par défaut pour l'apprenant ; la vue liste reste l'affichage habituel. */
  protected readonly vue = signal<VueFiche>('cartes');
}
