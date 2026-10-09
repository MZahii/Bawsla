import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

/** Titre de section avec lien « Voir tout ». <bw-section-head title="Continuer" link="/app/cours" /> */
@Component({
  selector: 'bw-section-head',
  imports: [RouterLink, MatIconModule],
  template: `
    <div class="t">
      <h2>{{ title() }}</h2>
      @if (subtitle()) {
        <p>{{ subtitle() }}</p>
      }
    </div>
    @if (link()) {
      <a [routerLink]="link()">{{ linkLabel() }} <mat-icon aria-hidden="true">arrow_forward</mat-icon></a>
    }
  `,
  styles: `
    :host { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--space-2); margin-bottom: var(--space-4); }
    h2 { margin: 0; font: 600 20px/28px var(--font-heading); }
    p { margin: 2px 0 0; font-size: 14px; color: var(--color-text-muted); }
    a { display: inline-flex; align-items: center; gap: 4px; font-weight: 600; font-size: 15px; color: var(--color-bordeaux-600); text-decoration: none; }
    a:hover { text-decoration: underline; }
    mat-icon { font-size: 18px; width: 18px; height: 18px; }
  `,
})
export class SectionHead {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly link = input('');
  readonly linkLabel = input('Voir tout');
}
