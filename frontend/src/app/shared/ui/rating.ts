import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Note sur 5 avec étoiles et nombre d'avis. <bw-rating [value]="4.7" [count]="54" /> */
@Component({
  selector: 'bw-rating',
  imports: [MatIconModule],
  template: `
    <span class="v">{{ value().toFixed(1) }}</span>
    <span class="stars" [attr.aria-label]="'Note : ' + value().toFixed(1) + ' sur 5'" role="img">
      @for (s of etoiles(); track $index) {
        <mat-icon aria-hidden="true" [class.on]="s !== 'star_outline'">{{ s }}</mat-icon>
      }
    </span>
    @if (count() !== null) {
      <span class="n">({{ count() }})</span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; }
    .v { font-weight: 700; }
    .stars { display: inline-flex; }
    mat-icon { font-size: 16px; width: 16px; height: 16px; color: var(--color-border); }
    mat-icon.on { color: var(--chart-3); }
    .n { color: var(--color-text-muted); }
  `,
})
export class Rating {
  readonly value = input(0);
  readonly count = input<number | null>(null);
  protected readonly etoiles = computed(() =>
    [1, 2, 3, 4, 5].map((i) => (this.value() >= i ? 'star' : this.value() >= i - 0.5 ? 'star_half' : 'star_outline')),
  );
}
