import { Component, computed, inject, input } from '@angular/core';

import { ThemeService } from '../../../core/theme/theme.service';

export type LogoVariant = 'icon' | 'horizontal' | 'stacked';
/** 'auto' suit le thème courant. 'mono' et 'white' n'existent que pour le symbole seul. */
export type LogoTheme = 'auto' | 'light' | 'dark' | 'mono' | 'white';

const BRAND = 'assets/brand/svg/';
/** Rapport largeur / hauteur des SVG (viewBox). */
const RATIO: Record<LogoVariant, number> = { icon: 1, horizontal: 918 / 420, stacked: 520 / 700 };
/** En dessous de cette taille, les dégradés deviennent flous : version aplat (DESIGN.md, section 5). */
const FLAT_BELOW = 48;

/**
 * Logo Bawsla. `size` = hauteur en pixels.
 * <bw-logo variant="horizontal" theme="dark" [size]="40" />
 */
@Component({
  selector: 'bw-logo',
  template: `<img [src]="src()" [width]="width()" [height]="size()" alt="Bawsla" />`,
  styles: `
    :host { display: inline-flex; line-height: 0; }
    img { display: block; max-width: 100%; height: auto; }
  `,
})
export class Logo {
  private readonly themeService = inject(ThemeService);

  readonly variant = input<LogoVariant>('icon');
  readonly theme = input<LogoTheme>('auto');
  readonly size = input(48);

  protected readonly width = computed(() => Math.round(this.size() * RATIO[this.variant()]));

  protected readonly src = computed(() => {
    const theme = this.theme() === 'auto' ? this.themeService.theme() : this.theme();
    const variant = this.variant();

    if (variant === 'icon') {
      switch (theme) {
        case 'dark':
          return `${BRAND}bawsla-icon-dark.svg`;
        case 'mono':
          return `${BRAND}bawsla-icon-mono.svg`;
        case 'white':
          return `${BRAND}bawsla-icon-white.svg`;
        default:
          return `${BRAND}bawsla-icon${this.size() < FLAT_BELOW ? '-flat' : ''}.svg`;
      }
    }
    const suffix = theme === 'dark' ? '-dark' : '';
    return `${BRAND}bawsla-logo-${variant}${suffix}.svg`;
  });
}
