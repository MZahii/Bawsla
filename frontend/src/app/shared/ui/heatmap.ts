import { Component, computed, input } from '@angular/core';

const JOURS = ['Lun.', 'Mar.', 'Mer.', 'Jeu.', 'Ven.', 'Sam.', 'Dim.'];

/**
 * Carte d'activité façon GitHub : une case par jour, intensité selon les minutes d'étude.
 * Une seule teinte (séquentielle), du clair au foncé. Chaque case a une infobulle.
 * <bw-heatmap [values]="minutesParJour" />  (multiple de 7, lundi → dimanche)
 */
@Component({
  selector: 'bw-heatmap',
  template: `
    <div class="grid" role="img" [attr.aria-label]="resume()">
      <div class="days" aria-hidden="true">
        @for (j of jours; track j; let i = $index) {
          <span [style.visibility]="i % 2 === 0 ? 'visible' : 'hidden'">{{ j }}</span>
        }
      </div>
      <div class="weeks">
        @for (w of semaines(); track $index) {
          <div class="week">
            @for (v of w; track $index) {
              <span [class]="'cell l' + niveau(v)" [title]="v + ' min'"></span>
            }
          </div>
        }
      </div>
    </div>
    <div class="legend" aria-hidden="true">
      Moins
      @for (l of [0, 1, 2, 3, 4]; track l) {
        <span [class]="'cell l' + l"></span>
      }
      Plus
    </div>
  `,
  styles: `
    :host { --c: 18px; display: block; overflow-x: auto; }
    .grid { display: flex; gap: 6px; }
    .days { display: grid; grid-template-rows: repeat(7, var(--c)); gap: 4px; font-size: 11px; color: var(--color-text-muted); }
    .days span { line-height: 13px; }
    .weeks { display: flex; gap: 4px; }
    .week { display: grid; grid-template-rows: repeat(7, var(--c)); gap: 4px; }
    .cell { display: inline-block; width: var(--c); height: var(--c); border-radius: 3px; }
    .l0 { background: var(--color-neutral-soft); }
    .l1 { background: color-mix(in srgb, var(--chart-1) 30%, var(--color-surface)); }
    .l2 { background: color-mix(in srgb, var(--chart-1) 55%, var(--color-surface)); }
    .l3 { background: color-mix(in srgb, var(--chart-1) 78%, var(--color-surface)); }
    .l4 { background: var(--chart-1); }
    .legend { display: flex; align-items: center; justify-content: flex-end; gap: 3px; margin-top: var(--space-2); font-size: 12px; color: var(--color-text-muted); }
    .legend .cell { width: 11px; height: 11px; }
  `,
})
export class Heatmap {
  readonly values = input.required<number[]>();
  protected readonly jours = JOURS;

  protected readonly semaines = computed(() => {
    const v = this.values();
    return Array.from({ length: Math.ceil(v.length / 7) }, (_, i) => v.slice(i * 7, i * 7 + 7));
  });
  protected readonly resume = computed(() => {
    const v = this.values();
    return `Activité : ${v.filter((x) => x > 0).length} jours actifs sur ${v.length}`;
  });

  protected niveau(v: number): number {
    return v === 0 ? 0 : v < 20 ? 1 : v < 40 ? 2 : v < 60 ? 3 : 4;
  }
}
