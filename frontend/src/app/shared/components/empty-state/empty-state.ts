import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Bloc "rien à afficher" réutilisable par tous les modules. */
@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  template: `
    <div class="empty">
      <mat-icon>{{ icon() }}</mat-icon>
      <p class="title">{{ title() }}</p>
      @if (message()) {
        <p class="message">{{ message() }}</p>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 8px;
      padding: 48px 16px;
      text-align: center;
      color: var(--mat-sys-on-surface-variant);
    }
    mat-icon { font-size: 48px; width: 48px; height: 48px; }
    .title { font: var(--mat-sys-title-medium); margin: 0; }
    .message { font: var(--mat-sys-body-medium); margin: 0; }
  `,
})
export class EmptyState {
  readonly icon = input('inbox');
  readonly title = input.required<string>();
  readonly message = input<string>();
}
