import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Indicateur compact (back-office) : libellé, valeur, variation et mini-courbe facultative.
 * <bw-kpi label="Étudiants actifs" value="214" [delta]="12" deltaLabel="ce mois" [trend]="[3,5,4,8]" icon="group" />
 */
@Component({
  selector: 'bw-kpi',
  imports: [MatIconModule],
  template: `
    <div class="top">
      <span class="label">{{ label() }}</span>
      @if (icon()) {
        <mat-icon aria-hidden="true">{{ icon() }}</mat-icon>
      }
    </div>
    <div class="mid">
      <span class="value">{{ value() }}</span>
      @if (trend().length > 1) {
        <svg class="spark" viewBox="0 0 100 32" preserveAspectRatio="none" aria-hidden="true">
          <polyline [attr.points]="points()" />
        </svg>
      }
    </div>
    @if (delta() !== null) {
      <span class="delta" [class.down]="delta()! < 0">
        <mat-icon aria-hidden="true">{{ delta()! < 0 ? 'south_east' : 'north_east' }}</mat-icon>
        {{ delta()! > 0 ? '+' : '' }}{{ delta() }} %
        <span class="muted">{{ deltaLabel() }}</span>
      </span>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-4); background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
    .top { display: flex; align-items: center; justify-content: space-between; color: var(--color-text-muted); font-size: 14px; }
    .top mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .mid { display: flex; align-items: flex-end; justify-content: space-between; gap: var(--space-3); }
    .value { font: 600 26px/1 var(--font-heading); font-variant-numeric: tabular-nums; }
    .spark { width: 96px; height: 32px; }
    .spark polyline { fill: none; stroke: var(--chart-1); stroke-width: 2; vector-effect: non-scaling-stroke; stroke-linejoin: round; }
    .delta { display: inline-flex; align-items: center; gap: 2px; font-size: 13px; font-weight: 600; color: var(--color-success); }
    .delta.down { color: var(--color-error); }
    .delta mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .muted { margin-left: 4px; font-weight: 400; color: var(--color-text-muted); }
  `,
})
export class Kpi {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  readonly icon = input('');
  readonly delta = input<number | null>(null);
  readonly deltaLabel = input('');
  readonly trend = input<number[]>([]);

  protected readonly points = computed(() => {
    const t = this.trend();
    const min = Math.min(...t);
    const max = Math.max(...t);
    return t.map((v, i) => `${(i / (t.length - 1)) * 100},${30 - ((v - min) / (max - min || 1)) * 28}`).join(' ');
  });
}
