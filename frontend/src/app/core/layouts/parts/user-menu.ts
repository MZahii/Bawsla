import { Component, computed, inject, input } from '@angular/core';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { ThemeService } from '../../theme/theme.service';
import { Avatar } from '../../../shared/ui/avatar';

const ROLE: Record<string, string> = { ETUDIANT: 'Apprenant', ENSEIGNANT: 'Formateur', ADMIN: 'Administrateur' };

/** Avatar et menu du compte (profil, thème, déconnexion), commun aux trois espaces. */
@Component({
  selector: 'app-user-menu',
  imports: [RouterLink, MatMenuModule, MatIconModule, MatDividerModule, Avatar],
  template: `
    @if (auth.currentUser(); as u) {
      <button type="button" class="me" [matMenuTriggerFor]="m" aria-label="Menu du compte">
        <bw-avatar [nom]="u.prenom + ' ' + u.nom" [size]="34" />
        @if (showName()) {
          <span class="txt"><span class="n">{{ u.prenom }} {{ u.nom }}</span><span class="r">{{ role() }}</span></span>
          <mat-icon aria-hidden="true">expand_more</mat-icon>
        }
      </button>
      <mat-menu #m="matMenu" xPosition="before">
        <div class="head" mat-menu-item disabled>
          <span class="n">{{ u.prenom }} {{ u.nom }}</span>
          <span class="e">{{ u.email }}</span>
        </div>
        <mat-divider />
        <a mat-menu-item [routerLink]="profil()"><mat-icon>person</mat-icon>Mon profil</a>
        @if (auth.role() === 'ETUDIANT') {
          <a mat-menu-item routerLink="/app/certificats"><mat-icon>workspace_premium</mat-icon>Mes certificats</a>
          <a mat-menu-item routerLink="/app/achats"><mat-icon>receipt_long</mat-icon>Mes achats</a>
        }
        <a mat-menu-item [routerLink]="profil()" [queryParams]="{ onglet: 'preferences' }"><mat-icon>settings</mat-icon>Paramètres</a>
        <button mat-menu-item type="button" (click)="theme.toggle()">
          <mat-icon>{{ theme.theme() === 'dark' ? 'light_mode' : 'dark_mode' }}</mat-icon>
          {{ theme.theme() === 'dark' ? 'Thème clair' : 'Thème sombre' }}
        </button>
        <mat-divider />
        <button mat-menu-item type="button" (click)="auth.logout()"><mat-icon>logout</mat-icon>Se déconnecter</button>
      </mat-menu>
    }
  `,
  styles: `
    .me { display: flex; align-items: center; gap: var(--space-2); padding: 4px; border: 0; border-radius: 999px; background: none; color: inherit; cursor: pointer; }
    .me:hover { background: var(--hover, var(--color-neutral-soft)); }
    .txt { display: none; flex-direction: column; text-align: left; line-height: 1.2; }
    @media (min-width: 960px) { .txt { display: flex; } }
    .n { font-weight: 600; font-size: 14px; }
    .r { font-size: 12px; opacity: 0.75; }
    .head { display: flex; flex-direction: column; align-items: flex-start; height: auto; padding-block: 8px; opacity: 1; line-height: 1.3; }
    .head .n { color: var(--color-text); }
    .head .e { font-size: 13px; color: var(--color-text-muted); }
  `,
})
export class UserMenu {
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  readonly showName = input(true);
  protected readonly role = computed(() => ROLE[this.auth.role() ?? ''] ?? '');
  protected readonly profil = computed(() => {
    const r = this.auth.role();
    return r === 'ENSEIGNANT' ? '/studio/profil' : r === 'ADMIN' ? '/admin/parametres' : '/app/profil';
  });
}
