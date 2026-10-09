import { Component, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Lecteur (maquette) : affiche, bouton lecture, barre de progression et commandes.
 * À brancher : remplacer par une balise <video> quand le cours fournit une URL.
 */
@Component({
  selector: 'bw-video-player',
  imports: [MatIconModule],
  template: `
    <div class="screen" [style.background-image]="'url(' + poster() + ')'">
      <button type="button" class="play" (click)="lecture.set(!lecture())" [attr.aria-label]="lecture() ? 'Pause' : 'Lecture'">
        <mat-icon>{{ lecture() ? 'pause' : 'play_arrow' }}</mat-icon>
      </button>
      <div class="bar">
        <button type="button" (click)="lecture.set(!lecture())" [attr.aria-label]="lecture() ? 'Pause' : 'Lecture'"><mat-icon>{{ lecture() ? 'pause' : 'play_arrow' }}</mat-icon></button>
        <span class="time">{{ position() }}</span>
        <span class="track"><span class="done" [style.width.%]="progres()"></span></span>
        <span class="time">{{ duree() }}</span>
        <button type="button" aria-label="Sous-titres"><mat-icon>closed_caption</mat-icon></button>
        <button type="button" aria-label="Vitesse de lecture"><mat-icon>speed</mat-icon></button>
        <button type="button" aria-label="Plein écran"><mat-icon>fullscreen</mat-icon></button>
      </div>
    </div>
  `,
  styles: `
    :host { display: block; }
    .screen { position: relative; aspect-ratio: 16 / 9; border-radius: var(--radius-sm); overflow: hidden; background: var(--color-navy-900) center / cover no-repeat; }
    .screen::after { content: ''; position: absolute; inset: 0; background: var(--color-navy-900); opacity: 0.35; }
    .play { position: absolute; z-index: 1; left: 50%; top: 50%; transform: translate(-50%, -50%); display: grid; place-items: center; width: 68px; height: 68px; border: 0; border-radius: 50%; background: var(--color-white); color: var(--color-navy-900); cursor: pointer; }
    .play mat-icon { font-size: 36px; width: 36px; height: 36px; }
    .bar { position: absolute; z-index: 1; left: 0; right: 0; bottom: 0; display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); background: var(--color-navy-900); color: var(--color-white); }
    .bar button { display: grid; place-items: center; width: 36px; height: 36px; border: 0; border-radius: 6px; background: none; color: inherit; cursor: pointer; }
    .time { font-size: 13px; font-variant-numeric: tabular-nums; }
    .track { flex: 1; height: 4px; border-radius: 2px; background: var(--color-navy-500); overflow: hidden; }
    .done { display: block; height: 100%; background: var(--color-white); }
  `,
})
export class VideoPlayer {
  readonly poster = input('');
  readonly position = input('04:12');
  readonly duree = input('12:30');
  readonly progres = input(34);
  protected readonly lecture = signal(false);
}
