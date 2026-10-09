import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export interface TimelineItem {
  quand: string;
  titre: string;
  detail?: string;
  icone?: string;
  ton?: 'neutre' | 'accent' | 'ia' | 'alerte';
}

/**
 * Chronologie verticale (journal d'audit, activité récente).
 * <bw-timeline [items]="evenements" />
 */
@Component({
  selector: 'bw-timeline',
  imports: [MatIconModule],
  template: `
    <ol>
      @for (it of items(); track $index) {
        <li>
          <span [class]="'dot ' + (it.ton ?? 'neutre')"><mat-icon aria-hidden="true">{{ it.icone ?? 'circle' }}</mat-icon></span>
          <div class="body">
            <p class="title">{{ it.titre }}</p>
            @if (it.detail) {
              <p class="detail">{{ it.detail }}</p>
            }
          </div>
          <time>{{ it.quand }}</time>
        </li>
      }
    </ol>
  `,
  styles: `
    ol { margin: 0; padding: 0; list-style: none; }
    li { position: relative; display: grid; grid-template-columns: 32px 1fr auto; gap: var(--space-3); padding-bottom: var(--space-4); }
    li:not(:last-child)::before { content: ''; position: absolute; left: 15px; top: 32px; bottom: 0; width: 2px; background: var(--color-border); }
    .dot { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 50%; background: var(--color-neutral-soft); color: var(--color-text-muted); }
    .dot mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .dot.accent { background: var(--color-bordeaux-100); color: var(--color-bordeaux-600); }
    .dot.ia { background: var(--color-gold-100); color: var(--color-gold-700); }
    .dot.alerte { background: color-mix(in srgb, var(--color-error) 14%, var(--color-surface)); color: var(--color-error); }
    .title { margin: 4px 0 0; font-weight: 500; }
    .detail { margin: 2px 0 0; font-size: 14px; color: var(--color-text-muted); }
    time { padding-top: 6px; font-size: 13px; color: var(--color-text-muted); white-space: nowrap; font-variant-numeric: tabular-nums; }
  `,
})
export class Timeline {
  readonly items = input.required<TimelineItem[]>();
}
