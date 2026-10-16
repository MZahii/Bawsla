import { Component, OnDestroy, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';

import { CoursService } from '../services/cours.service';

const NOMS_FOURNISSEURS: Record<string, string> = {
  elevenlabs: 'ElevenLabs',
  gemini: 'Gemini',
  openai: 'OpenAI',
  gtts: 'Google Traduction (gTTS)',
  edge: 'Microsoft Edge',
};

/**
 * Lecteur audio du résumé automatique local. L'audio exige le JWT : il est téléchargé avec HttpClient puis donné à <audio>
 * sous forme d'URL locale (blob:). Il n'est demandé que si `actif` est vrai, une seule fois par `cle` ; une autre `cle`
 * (nouvelle version) annule la requête, libère l'ancienne URL et recharge. Aucun nouvel essai automatique en cas d'erreur.
 */
@Component({
  selector: 'app-resume-local-audio',
  imports: [MatIconModule],
  template: `
    @if (url(); as source) {
      <div class="lecteur">
        <mat-icon aria-hidden="true">headphones</mat-icon>
        <audio controls preload="metadata" [src]="source" aria-label="Écouter le résumé automatique"></audio>
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
export class ResumeLocalAudio implements OnDestroy {
  private readonly service = inject(CoursService);

  readonly coursId = input.required<number>();
  /** Le serveur a-t-il un audio ? false : rien n'est demandé. */
  readonly actif = input(false);
  /** Change quand l'audio change (nouvelle version) : déclenche un nouveau téléchargement. */
  readonly cle = input<string | number>('');
  readonly fournisseur = input<string | null | undefined>(null);

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
      // L'effet ne dépend QUE de ces entrées ; le chargement (écrire url/etat, annuler) est hors suivi : pas de boucle.
      const id = this.coursId();
      const actif = this.actif();
      const cle = `${id}|${actif}|${this.cle()}`;
      untracked(() => this.synchroniser(cle, id, actif));
    });
  }

  private synchroniser(cle: string, id: number, actif: boolean): void {
    if (cle === this.cleChargee) return;
    this.cleChargee = cle;
    this.annuler();
    if (!actif) {
      this.etat.set('absent');
      return;
    }
    this.etat.set('chargement');
    this.requete = this.service.audioResumeLocal(id).subscribe({
      next: (blob) => {
        this.url.set(URL.createObjectURL(blob));
        this.etat.set('ok');
      },
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
