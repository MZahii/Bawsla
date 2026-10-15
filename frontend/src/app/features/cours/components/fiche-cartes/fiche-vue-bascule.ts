import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { VueFiche } from './fiche-cartes.modele';

/** Bascule « Vue fiches » / « Vue liste » de la fiche de révision. L'état courant est annoncé par aria-pressed. */
@Component({
  selector: 'app-fiche-vue-bascule',
  imports: [MatIconModule],
  template: `
    <div class="bascule" role="group" aria-label="Affichage de la fiche">
      <button type="button" [class.on]="vue() === 'cartes'" [attr.aria-pressed]="vue() === 'cartes'" (click)="vueChange.emit('cartes')">
        <mat-icon aria-hidden="true">style</mat-icon> Vue fiches
      </button>
      <button type="button" [class.on]="vue() === 'liste'" [attr.aria-pressed]="vue() === 'liste'" (click)="vueChange.emit('liste')">
        <mat-icon aria-hidden="true">view_list</mat-icon> Vue liste
      </button>
    </div>
  `,
  styles: `
    :host { display: block; margin-bottom: var(--space-3); }
    .bascule { display: inline-flex; border: 1px solid var(--color-border); border-radius: 999px; overflow: hidden; }
    button { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-4); border: 0; background: var(--color-surface); color: var(--color-text); font: 600 14px var(--font-body); cursor: pointer; }
    button.on { background: var(--color-primary); color: var(--color-on-primary); }
    button:focus-visible { outline: 3px solid var(--color-gold-500); outline-offset: -3px; }
    mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `,
})
export class FicheVueBascule {
  readonly vue = input.required<VueFiche>();
  readonly vueChange = output<VueFiche>();
}
