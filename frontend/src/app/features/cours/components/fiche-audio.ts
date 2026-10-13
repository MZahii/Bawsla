import { Component, OnDestroy, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Subscription } from 'rxjs';

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
 * à <audio> sous forme d'URL locale (blob:).
 *
 * - L'audio n'est demandé que si la fiche indique qu'il existe (`serveur`), et une seule fois par audio :
 *   la clé (cours, fiche, existence, fournisseur) ne change pas => aucune nouvelle requête.
 * - Un changement d'audio annule la requête en cours et libère l'ancienne URL.
 * - En cas d'erreur (404, 503…), on n'insiste pas : « audio indisponible » et, si possible, la voix du navigateur.
 *
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
    } @else {
      <p class="muted small" role="status">
        <mat-icon aria-hidden="true">headset_off</mat-icon>
        {{ etat() === 'erreur' ? 'L’audio est indisponible pour le moment.' : 'Il n’y a pas encore d’audio pour ce résumé.' }}
      </p>
      @if (voix.disponible && texte()) {
        <div class="navigateur">
          <button type="button" bwButton="secondaire" (click)="basculerVoix()" [attr.aria-pressed]="voix.enLecture()">
            <mat-icon aria-hidden="true">{{ voix.enLecture() ? 'stop_circle' : 'record_voice_over' }}</mat-icon>
            {{ voix.enLecture() ? 'Arrêter la lecture' : 'Écouter avec la voix du navigateur' }}
          </button>
          <p class="muted small">Ton navigateur lit alors le résumé avec sa propre voix.</p>
        </div>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .lecteur { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-surface); mat-icon { flex: none; color: var(--color-gold-700); } }
    audio { flex: 1; min-width: 0; height: 40px; }
    .mention { margin: var(--space-1) 0 0 var(--space-4); }
    .navigateur { display: grid; justify-items: start; gap: var(--space-2); margin-top: var(--space-2); p { margin: 0; } }
    p { display: flex; align-items: center; gap: var(--space-2); margin: 0; mat-icon { font-size: 20px; width: 20px; height: 20px; } }
  `,
})
export class FicheAudio implements OnDestroy {
  private readonly service = inject(CoursService);
  protected readonly voix = inject(VoixNavigateur);

  readonly coursId = input.required<number>();
  /** Identifiant de la fiche affichée : une autre fiche peut avoir un autre audio. */
  readonly version = input<number | null>(null);
  /** Le serveur a-t-il un audio pour cette version ? false : on ne le demande pas et on propose la voix du navigateur. */
  readonly serveur = input(true);
  /** Identifiant du fournisseur vocal (elevenlabs, gemini, gtts, edge), pour la mention « Voix générée avec… ». */
  readonly fournisseur = input<string | null | undefined>(null);
  /** Texte à lire avec la voix du navigateur quand il n'y a pas d'audio serveur. */
  readonly texte = input('');
  readonly langue = input<LangueVoix>('fr');

  protected readonly url = signal<string | null>(null);
  protected readonly etat = signal<'chargement' | 'ok' | 'absent' | 'erreur'>('absent');
  protected readonly nomFournisseur = computed(() => {
    const f = this.fournisseur();
    return f ? (NOMS_FOURNISSEURS[f] ?? f) : null;
  });

  /** Audio déjà chargé (ou en cours de chargement), identifié par sa clé. Jamais lu dans l'effet. */
  private cleChargee: string | null = null;
  private requete?: Subscription;

  constructor() {
    effect(() => {
      // L'effet ne dépend QUE de ces entrées. Tout ce que fait le chargement (écrire url, etat, annuler la requête…)
      // est exécuté hors suivi : sinon l'effet relirait les signaux qu'il vient d'écrire et boucle à l'infini.
      const id = this.coursId();
      const aAudio = this.serveur();
      const cle = `${id}|${this.version() ?? ''}|${aAudio}|${this.fournisseur() ?? ''}`;
      untracked(() => this.synchroniser(cle, id, aAudio));
    });
  }

  protected basculerVoix(): void {
    if (this.voix.enLecture()) this.voix.arreter();
    else this.voix.lire(this.texte(), this.langue());
  }

  private synchroniser(cle: string, id: number, aAudio: boolean): void {
    if (cle === this.cleChargee) return; // même audio : pas de nouvelle requête
    this.cleChargee = cle;
    this.annuler();
    if (!aAudio) {
      this.etat.set('absent');
      return;
    }
    this.etat.set('chargement');
    this.requete = this.service.audio(id).subscribe({
      next: (blob) => {
        this.url.set(URL.createObjectURL(blob));
        this.etat.set('ok');
      },
      // Pas de nouvel essai automatique : l'utilisateur garde la voix du navigateur.
      error: () => this.etat.set('erreur'),
    });
  }

  /** Annule la requête en cours et libère l'URL du blob précédent. */
  private annuler(): void {
    this.requete?.unsubscribe();
    this.requete = undefined;
    const actuelle = this.url();
    if (actuelle) URL.revokeObjectURL(actuelle);
    this.url.set(null);
  }

  ngOnDestroy(): void {
    this.annuler();
    this.voix.arreter();
  }
}
