import { Component, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { RouterLink } from '@angular/router';

import { COURS } from '../../demo/demo-data';
import { CATEGORIES, NIVEAUX, meta } from '../../demo/demo-plus';

/**
 * Bouton « Explorer » et son grand menu (domaines, niveaux, cours populaires), inspiré de Coursera.
 * base : '/catalogue' (visiteur) ou '/app/explorer' (étudiant connecté).
 */
@Component({
  selector: 'app-explorer-menu',
  imports: [RouterLink, MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <button type="button" class="trigger" [matMenuTriggerFor]="mega">
      Explorer <mat-icon aria-hidden="true">expand_more</mat-icon>
    </button>
    <mat-menu #mega="matMenu" class="bw-mega" xPosition="after">
      <div class="mega" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
        <section>
          <h3>Domaines</h3>
          @for (c of categories; track c.nom) {
            <a mat-menu-item [routerLink]="base()" [queryParams]="{ domaine: c.nom }">
              <mat-icon>{{ c.icone }}</mat-icon>
              <span class="lbl"><strong>{{ c.nom }}</strong><small>{{ c.description }}</small></span>
            </a>
          }
        </section>
        <section>
          <h3>Niveaux</h3>
          @for (n of niveaux; track n.code) {
            <a mat-menu-item [routerLink]="base()" [queryParams]="{ niveau: n.code }">
              <span class="lbl"><strong>{{ n.libelle }}</strong><small>{{ n.description }}</small></span>
            </a>
          }
          <a mat-menu-item [routerLink]="base()" class="all">Tout le catalogue <mat-icon iconPositionEnd>arrow_forward</mat-icon></a>
        </section>
        <section class="pop">
          <h3>Les plus suivis</h3>
          @for (c of populaires; track c.id) {
            <a mat-menu-item [routerLink]="[base(), c.id]">
              <img [src]="img(c.id)" alt="" />
              <span class="lbl"><strong>{{ c.titre }}</strong><small>{{ c.enseignant }}</small></span>
            </a>
          }
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
  protected readonly categories = CATEGORIES;
  protected readonly niveaux = NIVEAUX;
  protected readonly populaires = [...COURS].sort((a, b) => meta(b.id).inscrits - meta(a.id).inscrits).slice(0, 3);
  protected img(id: number): string {
    return meta(id).image;
  }
}
