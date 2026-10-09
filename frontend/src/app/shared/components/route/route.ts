import { Component, computed, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

export interface RouteStep {
  titre: string;
  fait: boolean;
  /** Étape manquée (question ratée) : dessinée en anneau d'erreur. */
  rate?: boolean;
  /** Information secondaire (ex. « 35 min »). */
  detail?: string;
}

/**
 * Itinéraire : les étapes d'un parcours (chapitres d'un cours, questions d'un quiz) reliées
 * comme les escales d'une route, avec la position courante.
 *
 * Vertical (liste cliquable, page de cours) :
 *   <bw-route [steps]="chapitres" [current]="2" (stepSelect)="ouvrir($event)" />
 * Horizontal compact (carte « Reprendre », barre de progression du quiz) :
 *   <bw-route [steps]="chapitres" [current]="2" orientation="horizontal" />
 */
@Component({
  selector: 'bw-route',
  imports: [MatIconModule],
  templateUrl: './route.html',
  styleUrl: './route.scss',
  host: { '[class.horizontal]': "orientation() === 'horizontal'" },
})
export class Route {
  readonly steps = input.required<RouteStep[]>();
  /** Index de l'étape en cours (« Tu es ici »). */
  readonly current = input(0);
  readonly orientation = input<'vertical' | 'horizontal'>('vertical');
  /** Étapes cliquables en mode vertical. */
  readonly interactive = input(true);
  readonly label = input('Itinéraire');
  readonly stepSelect = output<number>();

  protected readonly done = computed(() => this.steps().filter((s) => s.fait).length);
}
