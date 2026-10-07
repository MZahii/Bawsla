import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { MENU } from '../../core/layout/menu';

const INTROS = {
  ETUDIANT: 'Où en es-tu aujourd’hui ? Choisis un module pour reprendre ton parcours.',
  ENSEIGNANT: 'Prépare vos cours, vos quiz et suivez les échanges du forum.',
  ADMIN: 'Vue d’ensemble de la plateforme : utilisateurs, cours, quiz et forum.',
} as const;

@Component({
  selector: 'app-home',
  imports: [MatIconModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly auth = inject(AuthService);

  /** Les entrées du menu (hors tableau de bord) visibles pour le rôle courant. */
  protected readonly modules = computed(() => {
    const role = this.auth.role();
    return MENU.filter((m) => m.route !== '/' && (!m.roles || (role !== null && m.roles.includes(role))));
  });

  protected readonly intro = computed(() => {
    const role = this.auth.role();
    return role ? INTROS[role] : '';
  });
}
