import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { ThemeService } from '../../theme/theme.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { CATEGORIES } from '../../demo/demo-plus';
import { ExplorerMenu } from '../parts/explorer-menu';
import { PaginatorFr } from '../../i18n/paginator-fr';

/** Site public (visiteur non connecté) : en-tête avec Explorer, pied de page complet. */
@Component({
  // Libellés français des tableaux paginés de tout l'espace.
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorFr }],
  selector: 'app-public-shell',
  imports: [RouterOutlet, RouterLink, MatIconModule, MatSidenavModule, Logo, BwButton, ExplorerMenu],
  templateUrl: './public-shell.html',
  styleUrl: './public-shell.scss',
})
export class PublicShell {
  protected readonly theme = inject(ThemeService);
  protected readonly categories = CATEGORIES;
  private readonly router = inject(Router);
  /** L'accueil occupe toute la largeur ; les autres pages ont une colonne centrée. */
  protected readonly pleinePage = toSignal(
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd), map(() => this.router.url.startsWith('/bienvenue'))),
    { initialValue: this.router.url.startsWith('/bienvenue') },
  );
}
