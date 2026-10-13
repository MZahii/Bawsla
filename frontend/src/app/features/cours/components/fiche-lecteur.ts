import { Component, input } from '@angular/core';

import { AiBox } from '../../../shared';
import { Fiche } from '../models/cours.model';
import { FicheAudio } from './fiche-audio';
import { FicheContenu } from './fiche-contenu';

/**
 * Fiche de révision VALIDÉE, en lecture seule, pour l'apprenant : résumé, mots-clés, notions et audio, sans score ni alertes
 * (le serveur ne les renvoie pas). Extrait tel quel du lecteur de cours.
 */
@Component({
  selector: 'app-fiche-lecteur',
  imports: [AiBox, FicheAudio, FicheContenu],
  template: `
    <bw-ai-box heading="Fiche de révision">
      <app-fiche-contenu [fiche]="fiche()" />
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
}
