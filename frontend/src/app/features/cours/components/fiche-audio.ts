import { Component, OnDestroy, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { CoursService } from '../services/cours.service';

/**
 * Lecteur audio du résumé d'un cours. L'audio exige le JWT : on le télécharge avec HttpClient puis on le donne
 * à <audio> sous forme d'URL locale (blob:). Il se recharge quand `version` change (nouvelle fiche, nouvel audio).
 * <app-fiche-audio [coursId]="id" [version]="fiche.id" />
 */
@Component({
  selector: 'app-fiche-audio',
  imports: [MatIconModule],
  template: `
    @if (url(); as source) {
      <div class="lecteur">
        <mat-icon aria-hidden="true">headphones</mat-icon>
        <audio controls preload="metadata" [src]="source" aria-label="Écouter le résumé"></audio>
      </div>
    } @else if (etat() === 'chargement') {
      <p class="muted small" role="status">Chargement de l’audio…</p>
    } @else {
      <p class="muted small" role="status"><mat-icon aria-hidden="true">headset_off</mat-icon> L’audio de ce résumé n’est pas disponible.</p>
    }
  `,
  styles: `
    :host { display: block; }
    .lecteur { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); mat-icon { flex: none; color: var(--color-gold-700); } }
    audio { flex: 1; min-width: 0; height: 40px; }
    p { display: flex; align-items: center; gap: var(--space-2); margin: 0; mat-icon { font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class FicheAudio implements OnDestroy {
  private readonly service = inject(CoursService);

  readonly coursId = input.required<number>();
  /** Change quand l'audio a pu changer (identifiant de la fiche affichée). */
  readonly version = input<number | null>(null);

  protected readonly url = signal<string | null>(null);
  protected readonly etat = signal<'chargement' | 'ok' | 'absent'>('chargement');

  constructor() {
    effect(() => {
      const id = this.coursId();
      this.version();
      this.charger(id);
    });
  }

  private charger(id: number): void {
    this.liberer();
    this.etat.set('chargement');
    this.service.audio(id).subscribe({
      next: (blob) => {
        this.url.set(URL.createObjectURL(blob));
        this.etat.set('ok');
      },
      error: () => this.etat.set('absent'),
    });
  }

  private liberer(): void {
    const actuelle = this.url();
    if (actuelle) URL.revokeObjectURL(actuelle);
    this.url.set(null);
  }

  ngOnDestroy(): void {
    this.liberer();
  }
}
