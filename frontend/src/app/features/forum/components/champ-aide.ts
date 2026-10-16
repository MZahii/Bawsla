import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Ligne sous un champ du forum (DESIGN.md §6, champs de formulaire) : message d'erreur en couleur error avec une
 * icône, sinon le texte d'aide, et le compteur de caractères quand un maximum est donné.
 */
@Component({
  selector: 'app-champ-aide',
  imports: [MatIconModule],
  template: `
    <div class="aide small">
      @if (erreur()) {
        <span class="erreur" [id]="id()" role="alert"><mat-icon aria-hidden="true">error</mat-icon>{{ erreur() }}</span>
      } @else if (aide()) {
        <span class="muted" [id]="id()">{{ aide() }}</span>
      } @else {
        <span></span>
      }
      @if (max()) {
        <span class="num compteur" [class.limite]="longueur() > max()! * 0.9" aria-live="polite">{{ longueur() }} / {{ max() }}</span>
      }
    </div>
  `,
  styles: `
    .aide { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-3); min-height: 20px; }
    .erreur { display: inline-flex; align-items: flex-start; gap: 4px; color: var(--color-error); font-weight: 600; }
    .erreur mat-icon { flex: none; width: 18px; height: 18px; font-size: 18px; }
    .compteur { flex: none; color: var(--color-text-muted); }
    .limite { color: var(--color-warning); font-weight: 600; }
  `,
})
export class ChampAide {
  /** Identifiant référencé par aria-describedby du champ. */
  readonly id = input('');
  readonly erreur = input<string | null>(null);
  readonly aide = input<string | null>(null);
  readonly longueur = input(0);
  readonly max = input<number | null>(null);
}
