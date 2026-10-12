import { Component, OnDestroy, computed, effect, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { BwButton } from '../../../shared';
import { CoursService } from '../services/cours.service';
import { LangueVoix, VoixNavigateur } from '../services/voix-navigateur';

/** Nom affiché de chaque fournisseur vocal (le serveur renvoie son identifiant). */
const NOMS_FOURNISSEURS: Record<string, string> = {
  elevenlabs: 'ElevenLabs',
  gemini: 'Gemini',
  gtts: 'Google Traduction (gTTS)',
  edge: 'Microsoft Edge',
};

/**
 * Lecteur audio du résumé d'un cours. L'audio exige le JWT : on le télécharge avec HttpClient puis on le donne
 * à <audio> sous forme d'URL locale (blob:). Il se recharge quand `version` change (nouvelle fiche, nouvel audio).
 * Sans audio serveur, un bouton lit le texte avec la voix du navigateur (Web Speech API).
 * <app-fiche-audio [coursId]="id" [version]="fiche.id" [serveur]="fiche.aAudio" [fournisseur]="fiche.fournisseurAudio" [texte]="fiche.resume" />
 */
@Component({
  selector: 'app-fiche-audio',
  imports: [MatIconModule, BwButton],
  template: `
    @if (url(); as source) {
      <div class="lecteur">
        <mat-icon aria-hidden="true">headphones</mat-icon>
        <audio controls preload="metadata" [src]="source" aria-label="Écouter le résumé"></audio>
      </div>
      @if (nomFournisseur(); as nom) {
        <p class="mention muted small">Voix générée avec {{ nom }}</p>
      }
    } @else if (etat() === 'chargement') {
      <p class="muted small" role="status">Chargement de l’audio…</p>
    } @else if (voix.disponible && texte()) {
      <div class="navigateur">
        <button type="button" bwButton="secondaire" (click)="basculerVoix()" [attr.aria-pressed]="voix.enLecture()">
          <mat-icon aria-hidden="true">{{ voix.enLecture() ? 'stop_circle' : 'record_voice_over' }}</mat-icon>
          {{ voix.enLecture() ? 'Arrêter la lecture' : 'Écouter avec la voix du navigateur' }}
        </button>
        <p class="muted small">Il n’y a pas encore d’audio pour ce résumé : ton navigateur le lit avec sa propre voix.</p>
      </div>
    } @else {
      <p class="muted small" role="status"><mat-icon aria-hidden="true">headset_off</mat-icon> L’audio de ce résumé n’est pas disponible.</p>
    }
  `,
  styles: `
    :host { display: block; }
    .lecteur { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); mat-icon { flex: none; color: var(--color-gold-700); } }
    audio { flex: 1; min-width: 0; height: 40px; }
    .mention { margin: var(--space-1) 0 0 var(--space-4); }
    .navigateur { display: grid; justify-items: start; gap: var(--space-2); p { margin: 0; } }
    p { display: flex; align-items: center; gap: var(--space-2); margin: 0; mat-icon { font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class FicheAudio implements OnDestroy {
  private readonly service = inject(CoursService);
  protected readonly voix = inject(VoixNavigateur);

  readonly coursId = input.required<number>();
  /** Change quand l'audio a pu changer (identifiant de la fiche affichée). */
  readonly version = input<number | null>(null);
  /** Le serveur a-t-il un audio pour cette version ? false : on ne le demande pas et on propose la voix du navigateur. */
  readonly serveur = input(true);
  /** Identifiant du fournisseur vocal (elevenlabs, gemini, gtts, edge), pour la mention « Voix générée avec… ». */
  readonly fournisseur = input<string | null | undefined>(null);
  /** Texte à lire avec la voix du navigateur quand il n'y a pas d'audio serveur. */
  readonly texte = input('');
  readonly langue = input<LangueVoix>('fr');

  protected readonly url = signal<string | null>(null);
  protected readonly etat = signal<'chargement' | 'ok' | 'absent'>('chargement');
  protected readonly nomFournisseur = computed(() => {
    const f = this.fournisseur();
    return f ? (NOMS_FOURNISSEURS[f] ?? f) : null;
  });

  constructor() {
    effect(() => {
      const id = this.coursId();
      this.version();
      if (this.serveur()) {
        this.charger(id);
      } else {
        this.liberer();
        this.etat.set('absent');
      }
    });
  }

  protected basculerVoix(): void {
    if (this.voix.enLecture()) this.voix.arreter();
    else this.voix.lire(this.texte(), this.langue());
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
    this.voix.arreter();
  }
}
