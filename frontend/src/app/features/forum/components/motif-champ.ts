import { Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ChampAide } from './champ-aide';
import { ContexteMotif, MOTIFS_PREDEFINIS, MOTIF_MAX, erreurMotif } from './validation';

let suivant = 0;

/**
 * Motif d'une décision de modération : zone de texte, motifs prédéfinis cliquables (modifiables ensuite), erreur
 * sous le champ (même règle que le serveur) et compteur. Le parent désactive son bouton avec erreurMotif().
 */
@Component({
  selector: 'app-motif-champ',
  imports: [FormsModule, ChampAide],
  template: `
    <div class="motif">
      <label class="small" [for]="id">{{ libelle() }} <span aria-hidden="true">*</span></label>
      <div class="suggestions" role="group" [attr.aria-label]="'Motifs proposés pour : ' + libelle()">
        @for (m of suggestions(); track m) {
          <button type="button" class="tag" [class.on]="valeur().trim() === m" [attr.aria-pressed]="valeur().trim() === m" (click)="choisir(m)">{{ m }}</button>
        }
      </div>
      <textarea class="input zone" [id]="id" dir="auto" rows="2" [attr.maxlength]="max" [ngModel]="valeur()" (ngModelChange)="valeur.set($event)"
                (blur)="touche.set(true)" [attr.aria-invalid]="!!erreurVisible()" [attr.aria-describedby]="id + '-aide'" aria-required="true"
                [placeholder]="placeholder()"></textarea>
      <app-champ-aide [id]="id + '-aide'" [erreur]="erreurVisible()" aide="Entre 10 et 500 caractères, tracé dans le journal de modération."
                      [longueur]="valeur().trim().length" [max]="max" />
    </div>
  `,
  styles: `
    .motif { display: grid; gap: var(--space-2); }
    .suggestions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .tag { min-height: 44px; padding: 6px 14px; border: 1px solid var(--color-border); border-radius: var(--radius-pill); background: var(--color-surface);
      color: var(--color-text); font: 500 13px var(--font-body); cursor: pointer; text-align: left; }
    .tag:hover { border-color: var(--color-navy-700); }
    .tag.on { border-color: var(--color-bordeaux-600); background: var(--color-bordeaux-100); color: var(--color-bordeaux-600); }
    .tag:focus-visible { outline: 2px solid var(--color-bordeaux-600); outline-offset: 2px; }
    .input { width: 100%; box-sizing: border-box; padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: 400 15px/22px var(--font-body); resize: vertical; }
    .input:focus { outline: 1px solid var(--color-navy-700); border-color: var(--color-navy-700); }
    .input[aria-invalid='true'] { border-color: var(--color-error); }
  `,
})
export class MotifChamp {
  readonly valeur = model('');
  readonly contexte = input<ContexteMotif>('decision');
  readonly libelle = input('Motif');
  readonly placeholder = input('Explique ta décision en une phrase.');
  /** Montre l'erreur même si le champ n'a pas encore été quitté (après une tentative d'envoi). */
  readonly forcer = input(false);

  protected readonly id = `motif-${++suivant}`;
  protected readonly max = MOTIF_MAX;
  protected readonly touche = signal(false);
  protected readonly suggestions = computed(() => MOTIFS_PREDEFINIS[this.contexte()]);
  protected readonly erreurVisible = computed(() =>
    this.touche() || this.forcer() || this.valeur().trim() ? erreurMotif(this.valeur()) : null,
  );

  protected choisir(m: string): void {
    this.valeur.set(m);
    this.touche.set(true);
  }
}
