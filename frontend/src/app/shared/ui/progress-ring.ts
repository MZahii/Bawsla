import { Component, computed, input } from '@angular/core';

/** Anneau de progression. <bw-progress-ring [value]="62" [size]="72" label="Objectif" /> */
@Component({
  selector: 'bw-progress-ring',
  template: `
    <svg [attr.width]="size()" [attr.height]="size()" viewBox="0 0 36 36" role="img" [attr.aria-label]="label() + ' : ' + value() + ' %'">
      <circle class="bg" cx="18" cy="18" r="15.9" />
      <circle class="fg" cx="18" cy="18" r="15.9" [attr.stroke-dasharray]="dash()" />
    </svg>
    <span class="v">{{ value() }}<small>%</small></span>
  `,
  styles: `
    :host { position: relative; display: inline-grid; place-items: center; }
    svg { transform: rotate(-90deg); }
    circle { fill: none; stroke-width: 3.2; }
    .bg { stroke: var(--color-neutral-soft); }
    .fg { stroke: var(--color-bordeaux-600); stroke-linecap: round; }
    .v { position: absolute; font: 600 15px var(--font-heading); }
    small { font-size: 10px; }
  `,
})
export class ProgressRing {
  readonly value = input(0);
  readonly size = input(64);
  readonly label = input('Progression');
  protected readonly dash = computed(() => `${this.value()} 100`);
}
