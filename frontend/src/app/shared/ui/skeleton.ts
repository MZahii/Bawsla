import { Component, input } from '@angular/core';

/** Bloc de chargement (squelette). <bw-skeleton height="16px" width="60%" /> */
@Component({
  selector: 'bw-skeleton',
  template: ``,
  host: { '[style.height]': 'height()', '[style.width]': 'width()', '[style.border-radius]': 'radius()', 'aria-hidden': 'true' },
  styles: `
    :host { display: block; background: linear-gradient(90deg, var(--color-neutral-soft) 0%, var(--color-bg) 50%, var(--color-neutral-soft) 100%); background-size: 200% 100%; animation: sk 1.4s ease-in-out infinite; }
    @keyframes sk { from { background-position: 200% 0; } to { background-position: -200% 0; } }
  `,
})
export class Skeleton {
  readonly height = input('16px');
  readonly width = input('100%');
  readonly radius = input('6px');
}
