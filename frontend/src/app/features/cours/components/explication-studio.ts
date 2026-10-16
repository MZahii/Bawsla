import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, of, Subscription, switchMap, take, takeWhile, timer } from 'rxjs';

import { AiBox, BwButton } from '../../../shared';
import { errorMessage } from '../../../shared/models/api-response.model';
import { Explication, LangueExplication, ModeExplication, Traitement } from '../models/cours.model';
import { CoursService } from '../services/cours.service';
import { ExplicationLecteurAudio } from './explication-audio';
import { ExplicationSegments, LIBELLES_SEGMENT } from './explication-segments';

const LIBELLES_ETAPES: Record<string, string> = {
  TEXTE: 'Lecture du cours',
  SCRIPT: 'Écriture de l’explication',
  TRADUCTION: 'Traduction du script français validé',
  AUDIO: 'Mise en voix',
};

/**
 * Studio de l'explication orale par l'IA (formateur / ADMIN) : l'IA RÉEXPLIQUE le cours comme un enseignant, en français
 * (monologue ou dialogue) ; l'anglais est la traduction du français validé. Le formateur relit le script segment par
 * segment, écoute un aperçu, corrige, régénère, puis valide. Il ne peut valider qu'après avoir pu écouter un aperçu.
 */
@Component({
  selector: 'app-explication-studio',
  imports: [FormsModule, MatIconModule, MatSnackBarModule, AiBox, BwButton, ExplicationLecteurAudio, ExplicationSegments],
  template: `
    <div class="commandes">
      <div class="onglets" role="group" aria-label="Langue">
        <button type="button" [attr.aria-pressed]="langue() === 'fr'" [class.on]="langue() === 'fr'" (click)="choisir('fr', mode())">Français</button>
        <button type="button" [attr.aria-pressed]="langue() === 'en'" [class.on]="langue() === 'en'" (click)="choisir('en', mode())">English</button>
      </div>
      <div class="onglets" role="group" aria-label="Format">
        <button type="button" [attr.aria-pressed]="mode() === 'monologue'" [class.on]="mode() === 'monologue'" (click)="choisir(langue(), 'monologue')">Monologue</button>
        <button type="button" [attr.aria-pressed]="mode() === 'dialogue'" [class.on]="mode() === 'dialogue'" (click)="choisir(langue(), 'dialogue')">Dialogue</button>
      </div>
      @if (langue() === 'fr') {
        <label class="duree">Durée visée
          <input type="number" min="2" max="6" [ngModel]="duree()" (ngModelChange)="duree.set($event)" aria-label="Durée visée en minutes" /> min
        </label>
      }
    </div>

    @if (etat() === 'chargement') {
      <p class="muted small" role="status">Chargement…</p>
    }

    @if (attente()) {
      <div class="suivi" role="status" aria-live="polite">
        <strong>L’IA travaille…</strong>
        <ol>
          @for (e of traitement()?.etapes ?? []; track e.etape) {
            <li [class]="e.statut.toLowerCase()">
              <mat-icon aria-hidden="true">{{ icone(e.statut) }}</mat-icon>
              {{ libelleEtape(e.etape) }}
            </li>
          }
        </ol>
        <p class="muted small">Cela peut prendre une à deux minutes. Tu peux quitter cette page, le travail continue sur le serveur.</p>
      </div>
    } @else if (echec(); as echec) {
      <p class="alerte" role="alert">
        <mat-icon aria-hidden="true">error</mat-icon>
        <span><strong>{{ libelleEtape(echec.etape) }} : échec.</strong> {{ echec.erreur }}</span>
      </p>
    }

    @if (explication(); as e) {
      <bw-ai-box
        [heading]="titre(e)"
        [editable]="!brouillon() && !attente()"
        [regenerable]="!brouillon() && !attente()"
        [validatable]="e.statut !== 'VALIDEE' && !!e.aPuEcouter && !brouillon() && !attente()"
        [busy]="envoi() || attente()"
        (edit)="modifier()"
        (regenerate)="generer()"
        (validate)="valider()"
      >
        @if (e.notionsChoisiesParIa) {
          <p class="info" role="note">
            <mat-icon aria-hidden="true">info</mat-icon>
            <span><strong>Notions choisies par l’IA.</strong> Aucune fiche de révision validée ne fournit le plan : l’IA a choisi elle-même les notions à expliquer.</span>
          </p>
        }
        @if (e.aRetraduire) {
          <p class="info" role="note">
            <mat-icon aria-hidden="true">translate</mat-icon>
            <span><strong>À retraduire.</strong> Le français a été revalidé depuis : cette traduction ne correspond plus. Lance une nouvelle traduction.</span>
          </p>
        }

        @if (brouillon(); as b) {
          <ol class="edition">
            @for (s of e.segments; track s.ordre; let i = $index) {
              <li>
                <span class="type">{{ typeLibelle(s.type) }} · segment {{ s.ordre }}</span>
                <textarea class="edit" rows="3" [attr.aria-label]="'Texte du segment ' + s.ordre" dir="auto" [ngModel]="b[i].texte" (ngModelChange)="majSegment(i, 'texte', $event)"></textarea>
                @if (s.extraitSource !== undefined || b[i].extraitSource) {
                  <textarea class="edit" rows="2" [attr.aria-label]="'Extrait du cours pour le segment ' + s.ordre" placeholder="Extrait du cours qui justifie ce passage" dir="auto" [ngModel]="b[i].extraitSource" (ngModelChange)="majSegment(i, 'extraitSource', $event)"></textarea>
                }
              </li>
            }
          </ol>
          <p class="muted small">À l’enregistrement, la fidélité au cours est recalculée (sans appeler l’IA).</p>
          <div class="actions">
            <button type="button" bwButton="fantome" (click)="annuler()" [disabled]="envoi()">Annuler</button>
            <button type="button" bwButton="secondaire" (click)="enregistrer()" [disabled]="envoi()">{{ envoi() ? 'Enregistrement…' : 'Enregistrer mes corrections' }}</button>
          </div>
        } @else {
          <app-explication-segments [segments]="e.segments" [courant]="courant()" [dialogue]="e.mode === 'dialogue'" [gestionnaire]="true" />
        }

        @if (e.scoreFidelite !== undefined) {
          <section class="controle">
            <h4>Fidélité au cours <span class="muted small">(visible par toi seul)</span></h4>
            <div class="score" role="meter" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="e.scoreFidelite" [attr.aria-label]="'Fidélité au cours : ' + e.scoreFidelite + ' sur 100'">
              <span class="barre"><span [class]="'rempli ' + niveau()" [style.width.%]="e.scoreFidelite"></span></span>
              <span class="valeur num">{{ e.scoreFidelite }} / 100</span>
              <span [class]="'pill ' + niveau()">{{ libelleNiveau() }}</span>
            </div>
            @if (e.alertes?.length) {
              <ul class="alertes">
                @for (a of e.alertes; track a) {
                  <li><mat-icon aria-hidden="true">warning</mat-icon>{{ a }}</li>
                }
              </ul>
            }
            <p class="muted small meta">
              {{ e.nbMots }} mots · environ {{ minutes(e.dureeEstimeeSecondes) }} min · prompt {{ e.versionPrompt }} · modèle {{ e.modele }}
              @if (e.modifieeParFormateur) { · corrigée par le formateur }
            </p>
          </section>
        }

        @if (e.statut === 'VALIDEE') {
          <p class="valid"><mat-icon>verified</mat-icon> Validée : visible par les apprenants dans le lecteur du cours.</p>
        } @else if (e.valideeDisponible) {
          <p class="muted small">Les apprenants voient encore la version validée précédente, jusqu’à ce que tu valides celle-ci.</p>
        }
      </bw-ai-box>

      <section class="apercu" aria-label="Aperçu audio">
        <app-explication-audio
          [coursId]="coursId()"
          [langue]="e.langue"
          [mode]="e.mode"
          [actif]="e.aAudio"
          [cle]="e.id + '|' + (e.audioAJour ? 1 : 0) + '|' + (e.dureeMs ?? 0) + '|' + e.statut"
          [fournisseur]="e.fournisseurAudio"
          (temps)="positionner($event)"
        />
        @if (e.aAudio && e.audioAJour === false) {
          <p class="info" role="note"><mat-icon aria-hidden="true">info</mat-icon> Le texte a changé depuis cet aperçu. L’audio sera refait à la validation.</p>
        }
        <div class="actions">
          <button type="button" bwButton="ia" (click)="apercu()" [disabled]="attente() || envoi() || !!brouillon()">
            <mat-icon aria-hidden="true">headphones</mat-icon>
            {{ e.aAudio && e.audioAJour ? 'Refaire l’aperçu' : 'Écouter un aperçu' }}
          </button>
        </div>
        @if (e.statut !== 'VALIDEE' && !e.aPuEcouter) {
          <p class="muted small">Écoute d’abord un aperçu : tu pourras alors valider cette explication.</p>
        }
      </section>
    } @else if (etat() === 'ok' && !attente()) {
      <div class="vide">
        <mat-icon>record_voice_over</mat-icon>
        @if (langue() === 'fr') {
          <p>Bawsla peut réexpliquer ton cours à l’oral, comme un enseignant en vidéo : une accroche, puis pour chaque notion une explication, une analogie, un exemple, une question de vérification et un récapitulatif. Tu relis le script et tu écoutes un aperçu avant de le rendre visible.</p>
        } @else {
          <p>La version anglaise est la traduction de l’explication française <strong>validée</strong> (même découpage). Valide d’abord le français dans ce format.</p>
        }
        <button type="button" bwButton="ia" (click)="generer()" [disabled]="lancement()">
          {{ langue() === 'fr' ? 'Générer l’explication' : 'Traduire en anglais' }}
        </button>
      </div>
    }
  `,
  styles: `
    :host { display: grid; gap: var(--space-4); }
    .commandes { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); }
    .onglets { display: inline-flex; border: 1px solid var(--color-border); border-radius: 999px; overflow: hidden; }
    .onglets button { min-height: 44px; padding: 0 var(--space-4); border: 0; background: var(--color-surface); color: var(--color-text); font: 600 14px var(--font-body); cursor: pointer; }
    .onglets button.on { background: var(--color-primary); color: var(--color-on-primary); }
    .duree { display: inline-flex; align-items: center; gap: var(--space-2); font-size: 14px; }
    .duree input { width: 64px; min-height: 44px; padding: 0 var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); }
    .suivi { padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .suivi ol { margin: var(--space-2) 0; padding: 0; list-style: none; display: grid; gap: var(--space-1); }
    .suivi li { display: flex; align-items: center; gap: var(--space-2); }
    .suivi li.termine { color: var(--color-success); }
    .suivi li.echec { color: var(--color-error); }
    .suivi li.en_attente { color: var(--color-text-muted); }
    .suivi mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .alerte, .alertes li { display: flex; align-items: flex-start; gap: var(--space-2); color: var(--color-warning); font-size: 14px; font-weight: 600; }
    .alerte { margin: 0; }
    .alerte mat-icon, .alertes mat-icon, .info mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; }
    .alertes { margin: var(--space-3) 0 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
    .info { display: flex; align-items: flex-start; gap: var(--space-2); margin: 0 0 var(--space-3); padding: var(--space-3); border-radius: var(--radius-sm); background: var(--color-neutral-soft); font-size: 14px; }
    .info mat-icon { color: var(--color-info, var(--color-text-muted)); }
    .edition { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
    .edition li { display: grid; gap: var(--space-2); }
    .type { font: 600 13px var(--font-body); text-transform: uppercase; letter-spacing: 0.04em; color: var(--color-text-muted); }
    .edit { width: 100%; padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: inherit; resize: vertical; box-sizing: border-box; }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); margin-top: var(--space-3); }
    .controle { margin-top: var(--space-4); }
    h4 { margin: 0 0 var(--space-2); font: 600 14px var(--font-body); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
    .score { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .barre { flex: 1; min-width: 120px; height: 8px; border-radius: 4px; background: var(--color-neutral-soft); overflow: hidden; }
    .rempli { display: block; height: 100%; }
    .rempli.ok { background: var(--color-success); }
    .rempli.warn { background: var(--color-warning); }
    .rempli.ko { background: var(--color-error); }
    .valeur { font-weight: 700; }
    .meta { margin: var(--space-3) 0 0; }
    .valid { display: flex; align-items: center; gap: var(--space-2); margin: var(--space-3) 0 0; color: var(--color-success); font-weight: 600; }
    .apercu { display: grid; gap: var(--space-2); }
    .apercu .actions { justify-content: flex-start; margin-top: 0; }
    .vide { display: grid; justify-items: start; gap: var(--space-3); padding: var(--space-4); border: 1px dashed var(--color-border); border-radius: var(--radius-sm); p { margin: 0; } }
    p { margin: 0; }
  `,
})
export class ExplicationStudio implements OnInit, OnDestroy {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);

  readonly coursId = input.required<number>();
  /** Langue et format sélectionnés à l'ouverture (volet du Studio IA, menu « Régénérer »). */
  readonly langueInitiale = input<LangueExplication>('fr');
  readonly modeInitial = input<ModeExplication>('monologue');

  protected readonly langue = signal<LangueExplication>('fr');
  protected readonly mode = signal<ModeExplication>('monologue');
  protected readonly duree = signal(4);

  protected readonly etat = signal<'chargement' | 'ok' | 'erreur'>('chargement');
  protected readonly explication = signal<Explication | null>(null);
  protected readonly traitement = signal<Traitement | null>(null);
  protected readonly attente = signal(false);
  protected readonly lancement = signal(false);
  protected readonly envoi = signal(false);
  protected readonly brouillon = signal<{ texte: string; extraitSource: string }[] | null>(null);
  /** Ordre du segment en cours de lecture dans l'aperçu (0 : aucun). */
  protected readonly courant = signal(0);

  protected readonly echec = computed(() => {
    const t = this.traitement();
    return !this.attente() && t ? t.etapes.find((e) => e.statut === 'ECHEC') : undefined;
  });
  protected readonly niveau = computed<'ok' | 'warn' | 'ko'>(() => {
    const s = this.explication()?.scoreFidelite ?? 0;
    return s >= 80 ? 'ok' : s >= 60 ? 'warn' : 'ko';
  });
  protected readonly libelleNiveau = computed(() => ({ ok: 'Fidèle', warn: 'À vérifier', ko: 'Fidélité faible' })[this.niveau()]);

  private suivi?: Subscription;

  ngOnInit(): void {
    this.langue.set(this.langueInitiale());
    this.mode.set(this.modeInitial());
    this.charger();
    // Un traitement lancé avant l'arrivée sur la page continue : on le reprend.
    this.service.traitementExplication(this.coursId(), this.langue(), this.mode()).subscribe({
      next: (t) => {
        if (t.enCours) {
          this.traitement.set(t);
          this.suivre();
        }
      },
      error: () => undefined,
    });
  }

  ngOnDestroy(): void {
    this.arreterSuivi();
  }

  protected choisir(langue: LangueExplication, mode: ModeExplication): void {
    if (langue === this.langue() && mode === this.mode()) return;
    this.arreterSuivi();
    this.attente.set(false);
    this.brouillon.set(null);
    this.traitement.set(null);
    this.explication.set(null);
    this.courant.set(0);
    this.langue.set(langue);
    this.mode.set(mode);
    this.ngOnInit();
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.service.explication(this.coursId(), this.langue(), this.mode()).subscribe({
      next: (e) => {
        this.explication.set(e);
        this.etat.set('ok');
      },
      error: (err) => {
        this.etat.set('erreur');
        this.snack.open(errorMessage(err, 'Impossible de charger l’explication'), 'OK', { duration: 5000 });
      },
    });
  }

  protected generer(): void {
    if (this.lancement()) return;
    this.lancement.set(true);
    this.service.genererExplication(this.coursId(), this.langue(), this.mode(), this.duree()).subscribe({
      next: (t) => {
        this.lancement.set(false);
        this.traitement.set(t);
        this.suivre();
      },
      error: (err) => {
        this.lancement.set(false);
        this.snack.open(errorMessage(err, 'Impossible de lancer la génération'), 'OK', { duration: 7000 });
      },
    });
  }

  protected apercu(): void {
    if (this.attente()) return;
    this.service.apercuExplication(this.coursId(), this.langue(), this.mode()).subscribe({
      next: (t) => {
        this.traitement.set(t);
        this.suivre();
      },
      error: (err) => this.snack.open(errorMessage(err, 'Impossible de préparer l’aperçu'), 'OK', { duration: 7000 }),
    });
  }

  protected valider(): void {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.service.validerExplication(this.coursId(), this.langue(), this.mode()).subscribe({
      next: (e) => {
        this.envoi.set(false);
        this.explication.set(e);
        if (e.audioAJour === false) {
          // Le texte a changé depuis l'aperçu : le serveur refait l'audio en arrière-plan.
          this.snack.open('Explication validée. L’audio est en cours de préparation.', 'OK', { duration: 5000 });
          this.apercuSuivi();
        } else {
          this.snack.open('Explication validée : les apprenants peuvent la suivre.', 'OK', { duration: 4000 });
        }
      },
      error: (err) => {
        this.envoi.set(false);
        this.snack.open(errorMessage(err, 'Validation impossible'), 'OK', { duration: 7000 });
      },
    });
  }

  protected modifier(): void {
    const e = this.explication();
    if (!e) return;
    this.brouillon.set(e.segments.map((s) => ({ texte: s.texte, extraitSource: s.extraitSource ?? '' })));
  }

  protected majSegment(i: number, champ: 'texte' | 'extraitSource', valeur: string): void {
    this.brouillon.update((b) => (b ? b.map((s, k) => (k === i ? { ...s, [champ]: valeur } : s)) : b));
  }

  protected annuler(): void {
    this.brouillon.set(null);
  }

  protected enregistrer(): void {
    const e = this.explication();
    const b = this.brouillon();
    if (!e || !b || this.envoi()) return;
    this.envoi.set(true);
    const segments = e.segments.map((s, i) => ({ ordre: s.ordre, texte: b[i].texte, extraitSource: b[i].extraitSource || undefined }));
    this.service.corrigerExplication(this.coursId(), this.langue(), this.mode(), { segments }).subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.explication.set(r);
        this.brouillon.set(null);
        this.snack.open(`Corrections enregistrées. Fidélité recalculée : ${r.scoreFidelite ?? '—'} / 100`, 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.envoi.set(false);
        this.snack.open(errorMessage(err, 'Enregistrement impossible'), 'OK', { duration: 7000 });
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

  protected titre(e: Explication): string {
    const mode = e.mode === 'dialogue' ? 'dialogue' : 'monologue';
    return e.statut === 'VALIDEE' ? `Explication orale, ${mode} (validée)` : `Explication orale, ${mode} (brouillon à relire)`;
  }

  protected typeLibelle(type: string): string {
    return LIBELLES_SEGMENT[type as keyof typeof LIBELLES_SEGMENT] ?? type;
  }

  protected libelleEtape(etape: string): string {
    return LIBELLES_ETAPES[etape] ?? etape;
  }

  protected icone(statut: string): string {
    return statut === 'TERMINE' ? 'check_circle' : statut === 'ECHEC' ? 'error' : statut === 'EN_COURS' ? 'autorenew' : 'schedule';
  }

  protected minutes(secondes?: number): number {
    return Math.max(1, Math.round((secondes ?? 0) / 60));
  }

  /** Reprend le suivi sans relancer de traitement (l'audio a été lancé par la validation). */
  private apercuSuivi(): void {
    this.suivre();
  }

  /** Interroge le serveur toutes les 2 s jusqu'à la fin du traitement (au plus 8 min), puis recharge l'explication. */
  private suivre(): void {
    this.arreterSuivi();
    this.attente.set(true);
    this.suivi = timer(0, 2000)
      .pipe(
        switchMap(() => this.service.traitementExplication(this.coursId(), this.langue(), this.mode()).pipe(catchError(() => of(null)))),
        takeWhile((t) => t === null || t.enCours, true),
        take(240),
      )
      .subscribe({
        next: (t) => {
          if (!t) return;
          this.traitement.set(t);
          if (!t.enCours) this.finDeTraitement();
        },
        complete: () => this.attente.set(false),
      });
  }

  private finDeTraitement(): void {
    this.attente.set(false);
    this.charger();
    const echec = this.traitement()?.etapes.find((e) => e.statut === 'ECHEC');
    if (echec) this.snack.open(echec.erreur ?? 'Le traitement a échoué.', 'OK', { duration: 8000 });
  }

  private arreterSuivi(): void {
    this.suivi?.unsubscribe();
    this.suivi = undefined;
  }
}
