import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';

import { BwButton } from '../../directives/bw-button';

/** Délai après lequel on propose d'annuler (DESIGN.md, section 7, règle 5). */
const CANCEL_AFTER_MS = 10_000;

/**
 * Attente d'une génération IA : aiguille de boussole qui tourne lentement
 * (immobile si prefers-reduced-motion), puis bouton Annuler après 10 s.
 * <bw-ai-loading message="La boussole analyse ton cours…" (cancel)="annuler()" />
 */
@Component({
  selector: 'bw-ai-loading',
  imports: [BwButton],
  template: `
    <div class="wrap" role="status" aria-live="polite">
      <svg class="compass" viewBox="0 0 48 48" aria-hidden="true">
        <circle class="ring" cx="24" cy="24" r="21" />
        <g class="needle">
          <polygon class="north" points="24,6 29,24 19,24" />
          <polygon class="south" points="24,42 19,24 29,24" />
          <circle class="pivot" cx="24" cy="24" r="2.5" />
        </g>
      </svg>
      <p class="msg">{{ message() }}</p>
      @if (showCancel()) {
        <button type="button" bwButton="fantome" (click)="cancel.emit()">Annuler</button>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .wrap {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-3);
      padding: var(--space-5);
      text-align: center;
    }
    .compass { width: 48px; height: 48px; }
    .ring { fill: none; stroke: var(--color-border); stroke-width: 2; }
    .needle {
      transform-origin: 24px 24px;
      animation: bw-spin 4s linear infinite;
    }
    .north { fill: var(--color-gold-500); }
    .south { fill: var(--color-text-muted); }
    .pivot { fill: var(--color-surface); stroke: var(--color-text); stroke-width: 1; }
    .msg { margin: 0; color: var(--color-gold-700); font-weight: 500; }
    @media (prefers-reduced-motion: reduce) {
      .needle { animation: none; }
    }
  `,
})
export class AiLoading {
  readonly message = input('La boussole analyse…');
  readonly cancel = output<void>();

  protected readonly showCancel = signal(false);

  constructor() {
    const timer = setTimeout(() => this.showCancel.set(true), CANCEL_AFTER_MS);
    inject(DestroyRef).onDestroy(() => clearTimeout(timer));
  }
}
