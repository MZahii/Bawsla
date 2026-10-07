import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, isDevMode, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { Logo } from '../../shared/components/logo/logo';
import { ROLE_LABELS } from '../../shared/models/user.model';
import { AuthService } from '../auth/auth.service';
import { ThemeService } from '../theme/theme.service';
import { MENU } from './menu';

const COLLAPSED_KEY = 'bawsla.sidebar.collapsed';

@Component({
  selector: 'app-layout',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, Logo,
    MatSidenavModule, MatIconModule, MatButtonModule, MatTooltipModule, MatMenuModule, MatDividerModule,
  ],
  templateUrl: './layout.html',
  styleUrl: './layout.scss',
})
export class Layout {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Tiroir sous 960 px (DESIGN.md, section 4). */
  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 959.98px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  private readonly collapsedPref = signal(readCollapsed());
  /** Menu replié (72 px) : uniquement sur desktop. */
  protected readonly collapsed = computed(() => !this.isHandset() && this.collapsedPref());

  protected readonly menu = computed(() => {
    const role = this.auth.role();
    return MENU.filter((item) => !item.roles || (role !== null && item.roles.includes(role)));
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '';
  });

  /** La charte graphique est visible en ADMIN ou en mode développement. */
  protected readonly canSeeStyleguide = computed(() => isDevMode() || this.auth.role() === 'ADMIN');

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e) => e instanceof NavigationEnd),
      map(() => this.router.url),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** Titre de la page : data.title de la route, sinon l'entrée de menu correspondante. */
  protected readonly pageTitle = computed(() => {
    const url = this.url().split(/[?#]/)[0];
    let snapshot = this.route.snapshot;
    let title: string | undefined;
    while (snapshot) {
      title = (snapshot.data['title'] as string | undefined) ?? title;
      snapshot = snapshot.firstChild!;
    }
    if (title) {
      return title;
    }
    const match = MENU.filter((m) => (m.route === '/' ? url === '/' : url.startsWith(m.route))).sort(
      (a, b) => b.route.length - a.route.length,
    )[0];
    return match?.label ?? 'Bawsla';
  });

  protected toggleCollapsed(): void {
    const next = !this.collapsedPref();
    this.collapsedPref.set(next);
    try {
      localStorage.setItem(COLLAPSED_KEY, String(next));
    } catch {
      /* ignoré */
    }
  }
}

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}
