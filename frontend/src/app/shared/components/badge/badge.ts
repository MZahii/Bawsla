import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export type BadgeVariant = 'categorie' | 'niveau' | 'ia' | 'meilleure-reponse';
export type BadgeLevel = 1 | 2 | 3;

const LEVEL_LABELS: Record<BadgeLevel, string> = { 1: 'Débutant', 2: 'Intermédiaire', 3: 'Avancé' };

/**
 * Chips et badges (DESIGN.md, section 6).
 * <bw-badge variant="categorie">SQL</bw-badge>
 * <bw-badge variant="niveau" [level]="2" />
 * <bw-badge variant="ia" />
 * <bw-badge variant="meilleure-reponse" />
 * Le contenu projeté remplace le libellé par défaut.
 */
@Component({
  selector: 'bw-badge',
  imports: [MatIconModule],
  template: `
    <span [class]="'badge ' + variant()">
      @if (icon()) {
        <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      }
      @if (variant() === 'niveau') {
        <span class="dots" aria-hidden="true">
          @for (i of [1, 2, 3]; track i) {
            <span class="dot" [class.on]="i <= level()"></span>
          }
        </span>
      }
      <span class="text"><ng-content />{{ defaultLabel() }}</span>
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: var(--space-1);
      min-height: 24px;
      padding: 2px var(--space-3);
      border-radius: var(--radius-pill);
      font-size: 12px;
      line-height: 16px;
      font-weight: 500;
      white-space: nowrap;
    }
    mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .text:empty { display: none; }
    .categorie { background: var(--color-neutral-soft); color: var(--color-on-neutral-soft); }
    .niveau {
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      color: var(--color-text);
    }
    .ia { background: var(--color-gold-100); color: var(--color-gold-700); }
    .meilleure-reponse { background: var(--color-bordeaux-100); color: var(--color-bordeaux-600); }
    .dots { display: inline-flex; gap: 3px; }
    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      border: 1px solid var(--color-bordeaux-600);
    }
    .dot.on { background: var(--color-bordeaux-600); }
  `,
})
export class Badge {
  readonly variant = input<BadgeVariant>('categorie');
  readonly level = input<BadgeLevel>(1);
  /** Libellé par défaut ; mettre à '' quand on projette son propre texte. */
  readonly label = input<string>();

  protected readonly icon = computed(() => {
    switch (this.variant()) {
      case 'ia':
        return 'explore';
      case 'meilleure-reponse':
        return 'verified';
      default:
        return null;
    }
  });

  protected readonly defaultLabel = computed(() => {
    const label = this.label();
    if (label !== undefined) {
      return label;
    }
    switch (this.variant()) {
      case 'ia':
        return "Généré par l'IA";
      case 'meilleure-reponse':
        return 'Meilleure réponse';
      case 'niveau':
        return LEVEL_LABELS[this.level()];
      default:
        return '';
    }
  });
}
