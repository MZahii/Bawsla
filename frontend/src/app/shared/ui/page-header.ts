import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

export interface Crumb {
  label: string;
  link?: string;
}

/**
 * En-tête de page : fil d'Ariane, titre, sous-titre et actions à droite (contenu projeté).
 * <bw-page-header title="Mes cours" [crumbs]="[{ label: 'Studio', link: '/studio' }]">
 *   <button bwButton="principal">Nouveau cours</button>
 * </bw-page-header>
 */
@Component({
  selector: 'bw-page-header',
  imports: [MatIconModule, RouterLink],
  template: `
    @if (crumbs().length) {
      <nav class="crumbs" aria-label="Fil d’Ariane">
        @for (c of crumbs(); track c.label) {
          @if (c.link) {
            <a [routerLink]="c.link">{{ c.label }}</a>
          } @else {
            <span aria-current="page">{{ c.label }}</span>
          }
          @if (!$last) {
            <mat-icon aria-hidden="true">chevron_right</mat-icon>
          }
        }
      </nav>
    }
    <div class="row">
      <div class="text">
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p>{{ subtitle() }}</p>
        }
      </div>
      <div class="actions"><ng-content /></div>
    </div>
  `,
  styles: `
    :host { display: block; margin-bottom: var(--space-5); }
    .crumbs { display: flex; flex-wrap: wrap; align-items: center; gap: 2px; margin-bottom: var(--space-2); font-size: 14px; color: var(--color-text-muted); }
    .crumbs a { color: var(--color-text-muted); text-decoration: none; }
    .crumbs a:hover { color: var(--color-text); text-decoration: underline; }
    .crumbs mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .row { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: var(--space-4); }
    h1 { margin: 0; font: 600 26px/34px var(--font-heading); }
    p { margin: var(--space-1) 0 0; color: var(--color-text-muted); }
    .actions { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .actions:empty { display: none; }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly crumbs = input<Crumb[]>([]);
}
