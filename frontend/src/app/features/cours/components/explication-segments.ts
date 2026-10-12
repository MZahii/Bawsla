import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { SegmentExplication, TypeSegment } from '../models/cours.model';

export const LIBELLES_SEGMENT: Record<TypeSegment, string> = {
  intro: 'Accroche',
  explication: 'Explication',
  analogie: 'Analogie',
  exemple: 'Exemple',
  demonstration: 'Démonstration',
  question: 'Question',
  recap: 'Récapitulatif',
};

/**
 * Transcription d'une explication : un bloc par segment, avec le locuteur, la page du cours et un badge « Analogie »
 * (l'analogie est libre mais signalée). Le segment en cours de lecture est surligné. Pour le formateur (`gestionnaire`),
 * l'extrait source est visible et les segments non justifiés sont en orange.
 */
@Component({
  selector: 'app-explication-segments',
  imports: [MatIconModule],
  template: `
    <ol class="segments">
      @for (s of segments(); track s.ordre) {
        <li [class.courant]="s.ordre === courant()" [class.suspect]="gestionnaire() && s.justifiee === false">
          <div class="entete">
            <span class="type">{{ libelle(s) }}</span>
            @if (s.locuteur === 'etudiant') {
              <span class="pill plain">Apprenant</span>
            } @else if (dialogue()) {
              <span class="pill ia plain">Formateur</span>
            }
            @if (s.analogie) {
              <span class="pill ia plain" title="Image pour mieux comprendre, non tirée du cours">Analogie</span>
            }
            @if (s.page) {
              <span class="muted small">Page {{ s.page }}</span>
            }
            @if (s.notion) {
              <span class="muted small notion">{{ s.notion }}</span>
            }
          </div>
          <p class="texte" dir="auto">{{ s.texte }}</p>
          @if (gestionnaire() && s.extraitSource) {
            <blockquote><span class="lbl">Source dans le cours</span> « {{ s.extraitSource }} »</blockquote>
          }
          @if (gestionnaire() && s.justifiee === false) {
            <p class="alerte"><mat-icon aria-hidden="true">warning</mat-icon> Extrait ou chiffre introuvable dans le cours : vérifie ce passage avant de valider.</p>
          }
        </li>
      }
    </ol>
  `,
  styles: `
    :host { display: block; }
    .segments { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
    li { padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    li.courant { border-color: var(--color-gold-500); background: color-mix(in srgb, var(--color-gold-500) 10%, var(--color-surface)); }
    li.suspect { border-color: var(--color-warning); background: color-mix(in srgb, var(--color-warning) 8%, var(--color-surface)); }
    .entete { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-2); }
    .type { font: 600 13px var(--font-body); text-transform: uppercase; letter-spacing: 0.04em; color: var(--color-text-muted); }
    .notion { margin-left: auto; }
    .texte { margin: 0; line-height: 24px; white-space: pre-line; }
    blockquote { margin: var(--space-2) 0 0; padding-left: var(--space-3); border-left: 3px solid var(--color-gold-500); color: var(--color-text-muted); font-size: 14px; font-style: italic; }
    blockquote .lbl { display: block; font-style: normal; font-size: 12px; font-weight: 600; }
    .alerte { display: flex; align-items: flex-start; gap: var(--space-2); margin: var(--space-2) 0 0; color: var(--color-warning); font-size: 14px; font-weight: 600; mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class ExplicationSegments {
  readonly segments = input.required<SegmentExplication[]>();
  /** Ordre du segment en cours de lecture (0 : aucun). */
  readonly courant = input(0);
  readonly dialogue = input(false);
  readonly gestionnaire = input(false);

  protected libelle(s: SegmentExplication): string {
    return LIBELLES_SEGMENT[s.type] ?? s.type;
  }
}
