import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { Logo } from '../../../shared/components/logo/logo';
import { NotifMenu } from '../parts/notif-menu';
import { UserMenu } from '../parts/user-menu';
import { PaginatorFr } from '../../i18n/paginator-fr';

export interface NavItem {
  label: string;
  link: string;
  icone: string;
  exact?: boolean;
  compteur?: number;
}
export interface NavGroup {
  titre: string;
  items: NavItem[];
}
export interface QuickAction {
  label: string;
  link: string;
  icone: string;
}

/**
 * Coque à menu latéral, partagée par le studio enseignant (variant="light")
 * et le back-office admin (variant="dark", plus compact et plus dense).
 */
@Component({
  // Libellés français des tableaux paginés de tout l'espace.
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorFr }],
  selector: 'app-side-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatIconModule, MatMenuModule, MatTooltipModule, Logo, NotifMenu, UserMenu],
  templateUrl: './side-shell.html',
  styleUrl: './side-shell.scss',
  host: { '[class]': "'v-' + variant()" },
})
export class SideShell {
  readonly variant = input<'light' | 'dark'>('light');
  readonly espace = input('');
  readonly groups = input.required<NavGroup[]>();
  readonly home = input('/');
  readonly actions = input<QuickAction[]>([]);
  readonly searchPlaceholder = input('Rechercher');

  private readonly mobile = toSignal(inject(BreakpointObserver).observe('(max-width: 959px)').pipe(map((r) => r.matches)), { initialValue: false });
  protected readonly collapsed = signal(false);
  protected readonly mode = computed(() => (this.mobile() ? 'over' : 'side'));
  protected readonly isMobile = this.mobile;
}
