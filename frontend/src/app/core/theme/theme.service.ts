import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const THEME_KEY = 'bawsla.theme';

/**
 * Thème clair / sombre : pose l'attribut data-theme sur <html>, lu par styles/_tokens.scss.
 * Le choix est mémorisé ; à défaut on suit prefers-color-scheme.
 * Un script dans index.html applique déjà le thème avant le premier rendu.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);

  private readonly themeSignal = signal<Theme>(this.initialTheme());
  readonly theme = this.themeSignal.asReadonly();

  constructor() {
    effect(() => {
      const theme = this.themeSignal();
      this.document.documentElement.setAttribute('data-theme', theme);
    });
  }

  toggle(): void {
    this.set(this.themeSignal() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    this.themeSignal.set(theme);
    try {
      localStorage.setItem(THEME_KEY, theme);
    } catch {
      /* stockage indisponible : le choix reste en mémoire */
    }
  }

  private initialTheme(): Theme {
    try {
      const stored = localStorage.getItem(THEME_KEY);
      if (stored === 'light' || stored === 'dark') {
        return stored;
      }
    } catch {
      /* ignoré */
    }
    const prefersDark = this.document.defaultView?.matchMedia?.('(prefers-color-scheme: dark)').matches;
    return prefersDark ? 'dark' : 'light';
  }
}
