import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { AiBox, BwButton } from '../../../shared';
import { DisponibiliteExplication, Explication, LangueExplication, ModeExplication } from '../models/cours.model';
import { CoursService } from '../services/cours.service';
import { VoixNavigateur } from '../services/voix-navigateur';
import { ExplicationLecteurAudio } from './explication-audio';
import { ExplicationSegments } from './explication-segments';

const LIBELLE_LANGUE: Record<LangueExplication, string> = { fr: 'Français', en: 'English' };
const LIBELLE_MODE: Record<ModeExplication, string> = { monologue: 'Monologue', dialogue: 'Dialogue' };

/**
 * Section « Explication par l'IA » du lecteur : l'apprenant choisit la langue et le format parmi les explications
 * VALIDÉES par le formateur, écoute l'audio et suit la transcription (locuteur, page du cours, analogies signalées).
 * Rien n'est affiché tant qu'aucune explication n'est validée. Sans audio serveur, la voix du navigateur sert de secours.
 */
@Component({
  selector: 'app-explication-lecteur',
  imports: [MatIconModule, AiBox, BwButton, ExplicationLecteurAudio, ExplicationSegments],
  template: `
    @if (disponibles().length) {
      <section aria-label="Explication par l’IA">
        <bw-ai-box heading="Explication par l’IA">
          @if (langues().length > 1) {
            <div class="onglets" role="group" aria-label="Langue">
              @for (l of langues(); track l) {
                <button type="button" [class.on]="l === langue()" [attr.aria-pressed]="l === langue()" (click)="choisir(l, mode())">{{ libelleLangue[l] }}</button>
              }
            </div>
          }
          @if (modes().length > 1) {
            <div class="onglets" role="group" aria-label="Format">
              @for (m of modes(); track m) {
                <button type="button" [class.on]="m === mode()" [attr.aria-pressed]="m === mode()" (click)="choisir(langue(), m)">{{ libelleMode[m] }}</button>
              }
            </div>
          }

          @if (explication(); as e) {
            <p class="intro muted small">
              {{ e.mode === 'dialogue' ? 'Une discussion entre le formateur et un apprenant' : 'Le cours réexpliqué à l’oral, comme en vidéo' }}.
              Les images (analogies) sont signalées.
            </p>
            <app-explication-audio
              [coursId]="coursId()"
              [langue]="e.langue"
              [mode]="e.mode"
              [actif]="e.aAudio"
              [cle]="e.id + '|' + (e.dureeMs ?? 0)"
              [fournisseur]="e.fournisseurAudio"
              (temps)="positionner($event)"
            />
            @if (!e.aAudio && voix.disponible) {
              <div class="navigateur">
                <button type="button" bwButton="secondaire" (click)="basculerVoix()" [attr.aria-pressed]="voix.enLecture()">
                  <mat-icon aria-hidden="true">{{ voix.enLecture() ? 'stop_circle' : 'record_voice_over' }}</mat-icon>
                  {{ voix.enLecture() ? 'Arrêter la lecture' : 'Écouter avec la voix du navigateur' }}
                </button>
                <span class="muted small">L’audio n’est pas encore prêt : ton navigateur lit le texte avec sa propre voix.</span>
              </div>
            }
            <h4>Transcription</h4>
            <app-explication-segments [segments]="e.segments" [courant]="courant()" [dialogue]="e.mode === 'dialogue'" />
          } @else if (chargement()) {
            <p class="muted small" role="status">Chargement…</p>
          }
          <span bwAiSource>Source : le cours, expliqué par l’IA et relu par le formateur</span>
        </bw-ai-box>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .onglets { display: inline-flex; margin: 0 var(--space-2) var(--space-3) 0; border: 1px solid var(--color-border); border-radius: 999px; overflow: hidden; }
    .onglets button { min-height: 44px; padding: 0 var(--space-4); border: 0; background: var(--color-surface); color: var(--color-text); font: 600 14px var(--font-body); cursor: pointer; }
    .onglets button.on { background: var(--color-primary); color: var(--color-on-primary); }
    .intro { margin: 0 0 var(--space-3); }
    .navigateur { display: grid; justify-items: start; gap: var(--space-2); margin-top: var(--space-2); }
    h4 { margin: var(--space-4) 0 var(--space-2); font: 600 14px var(--font-body); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
  `,
})
export class ExplicationLecteur implements OnInit, OnDestroy {
  private readonly service = inject(CoursService);
  protected readonly voix = inject(VoixNavigateur);

  readonly coursId = input.required<number>();

  protected readonly libelleLangue = LIBELLE_LANGUE;
  protected readonly libelleMode = LIBELLE_MODE;
  protected readonly disponibles = signal<DisponibiliteExplication[]>([]);
  protected readonly langue = signal<LangueExplication>('fr');
  protected readonly mode = signal<ModeExplication>('monologue');
  protected readonly explication = signal<Explication | null>(null);
  protected readonly chargement = signal(false);
  protected readonly courant = signal(0);

  protected readonly langues = computed(() => [...new Set(this.disponibles().map((d) => d.langue))]);
  /** Formats disponibles dans la langue choisie. */
  protected readonly modes = computed(() => [...new Set(this.disponibles().filter((d) => d.langue === this.langue()).map((d) => d.mode))]);

  ngOnInit(): void {
    this.service.explicationsDisponibles(this.coursId()).subscribe({
      next: (liste) => {
        this.disponibles.set(liste);
        const premiere = liste[0];
        if (premiere) this.choisir(premiere.langue, premiere.mode);
      },
      // La section est facultative : en cas d'erreur, elle reste simplement masquée.
      error: () => undefined,
    });
  }

  ngOnDestroy(): void {
    this.voix.arreter();
  }

  protected choisir(langue: LangueExplication, mode: ModeExplication): void {
    // Si le format demandé n'existe pas dans cette langue, on prend le premier disponible.
    const existe = this.disponibles().some((d) => d.langue === langue && d.mode === mode);
    const reel = existe ? mode : (this.disponibles().find((d) => d.langue === langue)?.mode ?? mode);
    this.voix.arreter();
    this.courant.set(0);
    this.langue.set(langue);
    this.mode.set(reel);
    this.chargement.set(true);
    this.service.explication(this.coursId(), langue, reel).subscribe({
      next: (e) => {
        this.explication.set(e);
        this.chargement.set(false);
      },
      error: () => {
        this.explication.set(null);
        this.chargement.set(false);
      },
    });
  }

  protected positionner(ms: number): void {
    const e = this.explication();
    if (!e || ms < 0) {
      this.courant.set(0);
      return;
    }
    const segment = e.segments.find((s) => s.debutMs !== undefined && s.finMs !== undefined && ms >= s.debutMs && ms <= s.finMs + 400);
    this.courant.set(segment?.ordre ?? 0);
  }

  protected basculerVoix(): void {
    const e = this.explication();
    if (this.voix.enLecture() || !e) {
      this.voix.arreter();
      return;
    }
    this.voix.lire(e.segments.map((s) => s.texte).join(' '), e.langue);
  }
}
