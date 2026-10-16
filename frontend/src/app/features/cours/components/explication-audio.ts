import { Component, OnDestroy, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';

import { LangueExplication, ModeExplication } from '../models/cours.model';
import { CoursService } from '../services/cours.service';

const NOMS_FOURNISSEURS: Record<string, string> = {
  elevenlabs: 'ElevenLabs',
  gemini: 'Gemini',
  gtts: 'Google Traduction (gTTS)',
  edge: 'Microsoft Edge',
};

/**
 * Lecteur audio d'une explication orale. L'audio exige le JWT : il est téléchargé avec HttpClient puis donné à <audio>
 * sous forme d'URL locale (blob:). Il n'est demandé que si `actif` est vrai, et une seule fois par `cle` : changer
 * `cle` (nouvelle version, nouvel aperçu) annule la requête en cours, libère l'ancienne URL et recharge.
 * Émet `temps` (millisecondes) pendant la lecture, pour surligner le segment en cours.
 */
@Component({
  selector: 'app-explication-audio',
  imports: [MatIconModule],
  template: `
    @if (url(); as source) {
      <div class="lecteur">
        <mat-icon aria-hidden="true">headphones</mat-icon>
        <audio
          controls
          preload="metadata"
          [src]="source"
          aria-label="Écouter l’explication"
          (timeupdate)="avancer($event)"
          (seeked)="avancer($event)"
          (ended)="temps.emit(-1)"
        ></audio>
      </div>
      @if (nomFournisseur(); as nom) {
        <p class="mention muted small">Voix générée avec {{ nom }}</p>
      }
    } @else if (etat() === 'chargement') {
      <p class="muted small" role="status">Chargement de l’audio…</p>
    } @else if (etat() === 'erreur') {
      <p class="muted small" role="status"><mat-icon aria-hidden="true">headset_off</mat-icon> L’audio est indisponible pour le moment.</p>
    }
  `,
  styles: `
    :host { display: block; }
    .lecteur { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); mat-icon { flex: none; color: var(--color-gold-700); } }
    audio { flex: 1; min-width: 0; height: 40px; }
    .mention { margin: var(--space-1) 0 0 var(--space-4); }
    p { display: flex; align-items: center; gap: var(--space-2); margin: 0; mat-icon { font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class ExplicationLecteurAudio implements OnDestroy {
  private readonly service = inject(CoursService);

  readonly coursId = input.required<number>();
  readonly langue = input.required<LangueExplication>();
  readonly mode = input.required<ModeExplication>();
  /** Le serveur a-t-il un audio à lire ? false : rien n'est demandé. */
  readonly actif = input(false);
  /** Change quand l'audio change (nouvelle version, nouvel aperçu) : déclenche un nouveau téléchargement. */
  readonly cle = input<string | number>('');
  readonly fournisseur = input<string | null | undefined>(null);
  /** Position de lecture en millisecondes (-1 : lecture terminée). */
  readonly temps = output<number>();

  protected readonly url = signal<string | null>(null);
  protected readonly etat = signal<'chargement' | 'ok' | 'absent' | 'erreur'>('absent');
  protected readonly nomFournisseur = computed(() => {
    const f = this.fournisseur();
    return f ? (NOMS_FOURNISSEURS[f] ?? f) : null;
  });

  /** Audio déjà chargé (ou en cours), identifié par sa clé. Jamais lu dans l'effet. */
  private cleChargee: string | null = null;
  private requete?: Subscription;

  constructor() {
    effect(() => {
      // L'effet ne dépend QUE de ces entrées ; ce que fait le chargement (écrire url/etat, annuler) est hors suivi,
      // sinon l'effet relirait ce qu'il écrit et boucle à l'infini.
      const id = this.coursId();
      const langue = this.langue();
      const mode = this.mode();
      const actif = this.actif();
      const cle = `${id}|${langue}|${mode}|${actif}|${this.cle()}`;
      untracked(() => this.synchroniser(cle, id, langue, mode, actif));
    });
  }

  protected avancer(evenement: Event): void {
    this.temps.emit(Math.round((evenement.target as HTMLAudioElement).currentTime * 1000));
  }

  private synchroniser(cle: string, id: number, langue: LangueExplication, mode: ModeExplication, actif: boolean): void {
    if (cle === this.cleChargee) return;
    this.cleChargee = cle;
    this.annuler();
    if (!actif) {
      this.etat.set('absent');
      return;
    }
    this.etat.set('chargement');
    this.requete = this.service.audioExplication(id, langue, mode).subscribe({
      next: (blob) => {
        this.url.set(URL.createObjectURL(blob));
        this.etat.set('ok');
      },
      // Pas de nouvel essai automatique.
      error: () => this.etat.set('erreur'),
    });
  }

  private annuler(): void {
    this.requete?.unsubscribe();
    this.requete = undefined;
    const actuelle = this.url();
    if (actuelle) URL.revokeObjectURL(actuelle);
    this.url.set(null);
  }

  ngOnDestroy(): void {
    this.annuler();
  }
}
