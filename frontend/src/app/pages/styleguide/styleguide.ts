import { Component, signal } from '@angular/core';

import { AiBox } from '../../shared/components/ai-box/ai-box';
import { AiLoading } from '../../shared/components/ai-loading/ai-loading';
import { Badge } from '../../shared/components/badge/badge';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { Logo, LogoTheme } from '../../shared/components/logo/logo';
import { StatTile } from '../../shared/components/stat-tile/stat-tile';
import { BwButton } from '../../shared/directives/bw-button';

interface Swatch {
  token: string;
  usage: string;
}

/**
 * Charte graphique vivante (DESIGN.md) : palette, typographie, logos et composants partagés.
 * Visible en ADMIN ou en mode développement. Styles dans styles/_styleguide.scss.
 */
@Component({
  selector: 'app-styleguide',
  imports: [AiBox, AiLoading, Badge, EmptyState, Logo, StatTile, BwButton],
  templateUrl: './styleguide.html',
})
export class Styleguide {
  protected readonly palettes: { name: string; swatches: Swatch[] }[] = [
    {
      name: 'Navy : structure',
      swatches: [
        { token: '--color-navy-900', usage: 'Menu latéral' },
        { token: '--color-navy-700', usage: 'Primaire, titres' },
        { token: '--color-navy-500', usage: 'Survol' },
        { token: '--color-navy-100', usage: 'Fonds doux' },
      ],
    },
    {
      name: 'Bordeaux : action',
      swatches: [
        { token: '--color-bordeaux-700', usage: 'Pressé' },
        { token: '--color-bordeaux-600', usage: 'Liens, focus' },
        { token: '--color-bordeaux-500', usage: 'Accent' },
        { token: '--color-bordeaux-100', usage: 'Fonds d’accent' },
      ],
    },
    {
      name: 'Or : IA',
      swatches: [
        { token: '--color-gold-700', usage: 'Survol IA' },
        { token: '--color-gold-500', usage: 'Bouton IA, aiguille' },
        { token: '--color-gold-100', usage: 'Fond de boîte IA' },
      ],
    },
    {
      name: 'Neutres',
      swatches: [
        { token: '--color-bg', usage: 'Fond parchemin' },
        { token: '--color-surface', usage: 'Cartes' },
        { token: '--color-border', usage: 'Bordures' },
        { token: '--color-text', usage: 'Texte' },
        { token: '--color-text-muted', usage: 'Texte secondaire' },
      ],
    },
    {
      name: 'États',
      swatches: [
        { token: '--color-success', usage: 'Succès' },
        { token: '--color-warning', usage: 'Avertissement' },
        { token: '--color-error', usage: 'Erreur' },
        { token: '--color-info', usage: 'Information' },
      ],
    },
  ];

  protected readonly iconThemes: { theme: LogoTheme; label: string; dark: boolean }[] = [
    { theme: 'light', label: 'Clair', dark: false },
    { theme: 'mono', label: 'Mono', dark: false },
    { theme: 'dark', label: 'Sombre', dark: true },
    { theme: 'white', label: 'Blanc', dark: true },
  ];

  protected readonly loading = signal(false);
  protected readonly lastEvent = signal<string | null>(null);

  protected simulate(): void {
    this.loading.set(true);
    setTimeout(() => this.loading.set(false), 2000);
  }
}
