import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MatSidenavModule } from '@angular/material/sidenav';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Logo } from '../../../shared/components/logo/logo';
import { ExplorerMenu } from '../parts/explorer-menu';
import { NotifMenu } from '../parts/notif-menu';
import { UserMenu } from '../parts/user-menu';
import { PaginatorFr } from '../../i18n/paginator-fr';

/**
 * Espace apprenant : barre de navigation en haut (style plateforme de cours), contenu pleine largeur.
 */
@Component({
  // Libellés français des tableaux paginés de tout l'espace.
  providers: [{ provide: MatPaginatorIntl, useClass: PaginatorFr }],
  selector: 'app-student-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, MatIconModule, MatSidenavModule, Logo, ExplorerMenu, NotifMenu, UserMenu],
  templateUrl: './student-shell.html',
  styleUrl: './student-shell.scss',
})
export class StudentShell {
  private readonly router = inject(Router);
  protected readonly q = signal('');
  protected readonly liens = [
    { label: 'Accueil', link: '/app', exact: true, icone: 'home' },
    { label: 'Mes cours', link: '/app/cours', exact: false, icone: 'menu_book' },
    { label: 'Évaluations', link: '/app/evaluations', exact: false, icone: 'quiz' },
    { label: 'Forum', link: '/app/forum', exact: false, icone: 'forum' },
    { label: 'Progrès', link: '/app/progres', exact: false, icone: 'insights' },
    { label: 'Agenda', link: '/app/agenda', exact: false, icone: 'event' },
  ];

  protected chercher(): void {
    this.router.navigate(['/app/explorer'], { queryParams: { q: this.q() || null } });
  }
}
