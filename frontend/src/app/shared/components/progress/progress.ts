import { Component, computed, input } from '@angular/core';

/**
 * Barre de progression bordeaux (DESIGN.md : le bordeaux porte la progression).
 * <bw-progress [value]="45" label="Progression du cours" />
 */
@Component({
  selector: 'bw-progress',
  template: `
    <div class="track" role="progressbar" [attr.aria-label]="label()" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="clamped()">
      <div class="fill" [style.width.%]="clamped()"></div>
    </div>
    @if (showValue()) {
      <span class="value">{{ clamped() }} %</span>
    }
  `,
  styles: `
    :host { display: flex; align-items: center; gap: var(--space-2); }
    .track { flex: 1; height: 6px; border-radius: var(--radius-pill); background: var(--color-neutral-soft); overflow: hidden; }
    .fill { height: 100%; border-radius: inherit; background: var(--color-bordeaux-600); }
    .value { font-size: 12px; line-height: 16px; font-weight: 600; color: var(--color-text-muted); min-width: 36px; text-align: right; }
  `,
})
export class Progress {
  readonly value = input.required<number>();
  readonly label = input('Progression');
  readonly showValue = input(true);
  protected readonly clamped = computed(() => Math.round(Math.max(0, Math.min(100, this.value()))));
}
