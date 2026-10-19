import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { CATEGORIES, MATIERE_AUTRE, NIVEAUX } from '../../../features/cours/models/cours.constantes';

/**
 * Bouton « Explorer » et son grand menu (domaines et niveaux du catalogue), inspiré de Coursera.
 * base : '/catalogue' (visiteur) ou '/app/explorer' (étudiant connecté).
 */
@Component({
  selector: 'app-explorer-menu',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button type="button" class="trigger" [matMenuTriggerFor]="mega" #declencheur="matMenuTrigger">
      Explorer <mat-icon aria-hidden="true">expand_more</mat-icon>
    </button>
    <mat-menu #mega="matMenu" class="bw-mega" xPosition="after">
      <!-- Un clic dans le vide garde le menu ouvert ; un clic sur un lien le ferme (sinon il reste affiché sur le catalogue). -->
      <div class="mega" (click)="$event.stopPropagation(); fermerSiLien($event, declencheur)" (keydown)="$event.stopPropagation()">
        <section>
          <h3>Domaines</h3>
          @for (c of categories; track c.nom) {
            <a mat-menu-item [routerLink]="base()" [queryParams]="{ domaine: c.nom }">
              <mat-icon>{{ c.icone }}</mat-icon>
              <span class="lbl"><strong>{{ c.nom }}</strong></span>
            </a>
          }
        </section>
        <section>
          <h3>Niveaux</h3>
          @for (n of niveaux; track n.code) {
            <a mat-menu-item [routerLink]="base()" [queryParams]="{ niveau: n.code }">
              <span class="lbl"><strong>{{ n.libelle }}</strong></span>
            </a>
          }
          <a mat-menu-item [routerLink]="base()" class="all">Tout le catalogue <mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
        </section>
      </div>
    </mat-menu>
  `,
  styles: `
    .trigger { display: inline-flex; align-items: center; gap: 2px; height: 40px; padding: 0 12px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: 600 15px var(--font-body); cursor: pointer; }
    .trigger:hover { border-color: var(--color-text-muted); }
    .trigger mat-icon { font-size: 20px; width: 20px; height: 20px; }
  `,
})
export class ExplorerMenu {
  readonly base = input('/catalogue');
  protected readonly categories = CATEGORIES.filter((c) => c.nom !== MATIERE_AUTRE);
  protected readonly niveaux = NIVEAUX;

  protected fermerSiLien(e: Event, declencheur: MatMenuTrigger): void {
    if ((e.target as HTMLElement).closest('a')) declencheur.closeMenu();
  }
}
