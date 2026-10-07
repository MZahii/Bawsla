import { Component, computed, inject, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { ThemeService } from '../../../core/theme/theme.service';
import { BwButton } from '../../directives/bw-button';
import { Logo } from '../logo/logo';

const LEGACY_ICONS: Record<string, string> = { error_outline: 'error', info_outline: 'info' };

/**
 * État vide (DESIGN.md, section 6) : symbole mono à 10 %, une phrase utile, un bouton d'action.
 * <bw-empty-state title="Aucun quiz pour l'instant." message="Crée le premier !"
 *                 actionLabel="Créer un quiz" (action)="creer()" />
 * Le sélecteur historique <app-empty-state> reste supporté.
 */
@Component({
  selector: 'bw-empty-state, app-empty-state',
  imports: [MatIconModule, Logo, BwButton],
  template: `
    <div class="empty">
      <bw-logo class="mark" variant="icon" [theme]="logoTheme()" [size]="96" aria-hidden="true" />
      <p class="title">
        @if (icon()) {
          <mat-icon aria-hidden="true">{{ symbol() }}</mat-icon>
        }
        {{ title() }}
      </p>
      @if (message()) {
        <p class="message">{{ message() }}</p>
      }
      @if (actionLabel()) {
        <button type="button" bwButton="principal" (click)="action.emit()">{{ actionLabel() }}</button>
      }
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: var(--space-2);
      padding: var(--space-7) var(--space-4);
      text-align: center;
    }
    .mark { opacity: 0.1; margin-bottom: var(--space-2); }
    .title {
      display: inline-flex;
      align-items: center;
      gap: var(--space-2);
      margin: 0;
      font-family: var(--font-heading);
      font-size: 20px;
      line-height: 28px;
      font-weight: 600;
      color: var(--color-text);
    }
    .title mat-icon { color: var(--color-text-muted); }
    .message { margin: 0 0 var(--space-3); color: var(--color-text-muted); }
  `,
})
export class EmptyState {
  private readonly themeService = inject(ThemeService);

  /** Icône Material optionnelle à côté du titre (ex. "error" pour une erreur). */
  readonly icon = input<string>();
  readonly title = input.required<string>();
  readonly message = input<string>();
  readonly actionLabel = input<string>();
  readonly action = output<void>();

  /** Noms de l'ancienne police Material Icons absents de Material Symbols. */
  protected readonly symbol = computed(() => LEGACY_ICONS[this.icon() ?? ''] ?? this.icon());

  protected readonly logoTheme = computed(() => (this.themeService.theme() === 'dark' ? 'white' : 'mono'));
}
