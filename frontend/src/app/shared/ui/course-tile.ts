import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { Avatar } from './avatar';
import { Rating } from './rating';

export interface CourseTileData {
  id: number;
  titre: string;
  description?: string;
  categorie: string;
  niveau: string;
  enseignant: string;
  image: string;
  note?: number;
  avis?: number;
  duree?: string;
  chapitres?: number;
  progression?: number;
  /** « Gratuit » ou « 79 DT ». Masqué quand l'apprenant a commencé le cours. */
  prix?: string;
}

/**
 * Carte de cours avec photo (catalogue, accueil). Toute la carte est un lien.
 * variant="grid" (vignette en haut) ou "row" (vignette à gauche, pour les listes).
 * <bw-course-tile [cours]="c" [link]="['/app/explorer', c.id]" />
 */
@Component({
  selector: 'bw-course-tile',
  imports: [RouterLink, MatIconModule, Avatar, Rating],
  template: `
    <a [class]="'tile ' + variant()" [routerLink]="link()">
      <div class="media">
        <img [src]="cours().image" alt="" loading="lazy" />
        <span class="cat">{{ cours().categorie }}</span>
      </div>
      <div class="body">
        <span class="by"><bw-avatar [nom]="cours().enseignant" [size]="22" /> {{ cours().enseignant }}</span>
        <h3>{{ cours().titre }}</h3>
        @if (variant() === 'row' && cours().description) {
          <p class="desc">{{ cours().description }}</p>
        }
        @if (cours().note) {
          <bw-rating [value]="cours().note!" [count]="cours().avis ?? null" />
        }
        <span class="meta">
          <span>{{ cours().niveau }}</span>
          @if (cours().chapitres) {
            <span>{{ cours().chapitres }} chapitres</span>
          }
          @if (cours().duree) {
            <span>{{ cours().duree }}</span>
          }
        </span>
        @if (cours().prix && !avance()) {
          <span class="price" [class.free]="cours().prix === 'Gratuit'">{{ cours().prix }}</span>
        }
        @if (avance()) {
          <span class="prog">
            <span class="bar"><span [style.width.%]="cours().progression"></span></span>
            <span>{{ cours().progression }} %</span>
          </span>
        }
      </div>
    </a>
  `,
  styles: `
    :host { display: block; }
    .tile { display: flex; flex-direction: column; height: 100%; overflow: hidden; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); text-decoration: none; transition: border-color 0.15s; }
    .tile:hover { border-color: var(--color-text-muted); }
    .tile:hover h3 { text-decoration: underline; }
    .media { position: relative; aspect-ratio: 16 / 9; overflow: hidden; background: var(--color-navy-700); }
    img { position: absolute; inset: 0; display: block; width: 100%; height: 100%; object-fit: cover; }
    .cat { position: absolute; left: 10px; top: 10px; padding: 2px 8px; border-radius: 4px; background: var(--color-surface); font-size: 12px; font-weight: 600; color: var(--color-text); }
    .body { display: flex; flex: 1; flex-direction: column; gap: 6px; padding: var(--space-3) var(--space-4) var(--space-4); }
    .by { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--color-text-muted); }
    h3 { margin: 0; font: 600 16px/22px var(--font-heading); }
    .desc { margin: 0; font-size: 14px; color: var(--color-text-muted); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .meta { display: flex; flex-wrap: wrap; gap: 4px 12px; margin-top: auto; font-size: 13px; color: var(--color-text-muted); }
    .prog { display: flex; align-items: center; gap: var(--space-2); font-size: 12px; font-weight: 600; color: var(--color-text-muted); }
    .bar { flex: 1; height: 4px; border-radius: 2px; background: var(--color-neutral-soft); overflow: hidden; }
    .bar span { display: block; height: 100%; background: var(--color-bordeaux-600); }
    .price { font: 700 15px var(--font-body); color: var(--color-text); }
    .price.free { color: var(--color-success); }
    .row { flex-direction: row; }
    .row .media { flex: 0 0 220px; aspect-ratio: auto; }
    @media (max-width: 599px) { .row { flex-direction: column; } .row .media { flex: none; aspect-ratio: 16 / 9; } }
  `,
})
export class CourseTile {
  readonly cours = input.required<CourseTileData>();
  readonly link = input<unknown[] | string>([]);
  readonly variant = input<'grid' | 'row'>('grid');
  protected readonly avance = computed(() => (this.cours().progression ?? 0) > 0);
}
