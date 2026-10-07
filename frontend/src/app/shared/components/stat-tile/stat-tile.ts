import { Component, computed, input } from '@angular/core';

/**
 * Tuile de statistique (tableau de bord) : valeur en H1, libellé en caption, tendance colorée.
 * <bw-stat-tile label="Quiz réussis" value="12" [trend]="8" trendLabel="vs semaine dernière" />
 */
@Component({
  selector: 'bw-stat-tile',
  template: `
    <div class="tile">
      <span class="label">{{ label() }}</span>
      <span class="value">{{ value() }}</span>
      @if (trend() !== undefined) {
        <span class="trend" [class.up]="trend()! > 0" [class.down]="trend()! < 0">
          <span aria-hidden="true">{{ arrow() }}</span>
          <span class="bw-visually-hidden">{{ trendText() }}</span>
          <span aria-hidden="true">{{ abs() }} %</span>
          @if (trendLabel()) {
            <span class="hint">{{ trendLabel() }}</span>
          }
        </span>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .tile {
      display: flex;
      flex-direction: column;
      gap: var(--space-1);
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: var(--radius-md);
      box-shadow: var(--shadow-sm);
      padding: var(--space-5);
    }
    .label {
      font-size: 12px;
      line-height: 16px;
      font-weight: 500;
      color: var(--color-text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .value {
      font-family: var(--font-heading);
      font-size: 32px;
      line-height: 40px;
      font-weight: 700;
      color: var(--color-primary);
    }
    .trend {
      display: inline-flex;
      gap: var(--space-1);
      font-size: 14px;
      line-height: 20px;
      font-weight: 600;
      color: var(--color-text-muted);
    }
    .trend.up { color: var(--color-success); }
    .trend.down { color: var(--color-error); }
    .hint { font-weight: 400; color: var(--color-text-muted); }
  `,
})
export class StatTile {
  readonly label = input.required<string>();
  readonly value = input.required<string | number>();
  /** Variation en pourcentage (positif = hausse). */
  readonly trend = input<number>();
  readonly trendLabel = input<string>();

  protected readonly arrow = computed(() => {
    const t = this.trend() ?? 0;
    return t > 0 ? '↑' : t < 0 ? '↓' : '→';
  });
  protected readonly abs = computed(() => Math.abs(this.trend() ?? 0));
  protected readonly trendText = computed(() => {
    const t = this.trend() ?? 0;
    return t > 0 ? 'En hausse de' : t < 0 ? 'En baisse de' : 'Stable,';
  });
}
