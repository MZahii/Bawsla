import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { COURS, coursParId } from '../../../../core/demo/demo-data';
import { CERTIFICATS, meta } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

/**
 * Mes certificats : ceux obtenus, et les cours en route vers un certificat.
 * À brancher : QuizService.mesCertificats().
 */
@Component({
  selector: 'app-certificats',
  imports: [RouterLink, MatIconModule, BwButton, EmptyState, PageHeader],
  template: `
    <bw-page-header title="Mes certificats" subtitle="Un certificat par cours terminé et examen final réussi. Chacun a un numéro que tout le monde peut vérifier." />

    @if (certificats.length) {
      <div class="grid">
        @for (c of certificats; track c.numero) {
          <a class="card cert" [routerLink]="['/app/certificats', c.numero]">
            <img [src]="image(c.coursId)" alt="" />
            <div class="b">
              <span class="k"><mat-icon aria-hidden="true">workspace_premium</mat-icon> Certificat</span>
              <h2>{{ titre(c.coursId) }}</h2>
              <p class="muted small">Obtenu le {{ c.date }}, score {{ c.score }} %</p>
              <p class="muted small num">N° {{ c.numero }}</p>
            </div>
          </a>
        }
      </div>
    } @else {
      <bw-empty-state icon="workspace_premium" title="Pas encore de certificat" message="Termine un cours et réussis son examen final pour obtenir ton premier certificat." />
    }

    <h2 class="sec">En route vers un certificat</h2>
    <ul class="card list">
      @for (c of enCours; track c.id) {
        <li>
          <span class="t"><strong>{{ c.titre }}</strong>
            <span class="muted small">{{ c.progression === 100 ? 'Tous les chapitres sont terminés : l’examen final est disponible.' : c.progression + ' % du cours terminé' }}</span>
          </span>
          @if (c.progression === 100) {
            <a bwButton="principal" [routerLink]="['/app/evaluations/examen', c.id]">Passer l’examen</a>
          } @else {
            <a bwButton="fantome" [routerLink]="['/app/cours', c.id, 'lecture']">Continuer</a>
          }
        </li>
      }
    </ul>
  `,
  styles: `
    .grid { display: grid; gap: var(--space-5); grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); }
    .cert { display: flex; flex-direction: column; overflow: hidden; color: inherit; text-decoration: none; border-top: 3px solid var(--color-gold-500); }
    .cert:hover h2 { text-decoration: underline; }
    .cert img { width: 100%; aspect-ratio: 16 / 7; object-fit: cover; }
    .b { padding: var(--space-4); }
    .b p { margin: 2px 0 0; }
    .k { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; font-weight: 700; color: var(--color-gold-700); }
    .k mat-icon { font-size: 18px; width: 18px; height: 18px; }
    h2 { margin: var(--space-1) 0 var(--space-2); font: 600 17px/24px var(--font-heading); }
    .sec { margin: var(--space-7) 0 var(--space-3); font: 600 20px var(--font-heading); }
    .list { margin: 0; padding: 0; list-style: none; }
    .list li { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-bottom: 1px solid var(--color-border); }
    .list li:last-child { border-bottom: 0; }
    .t { display: flex; flex: 1; flex-direction: column; }
  `,
})
export class Certificats {
  protected readonly certificats = CERTIFICATS;
  protected readonly enCours = COURS.filter((c) => c.inscrit && !CERTIFICATS.some((x) => x.coursId === c.id));
  protected titre(id: number): string {
    return coursParId(id)?.titre ?? '';
  }
  protected image(id: number): string {
    return meta(id).image;
  }
}
