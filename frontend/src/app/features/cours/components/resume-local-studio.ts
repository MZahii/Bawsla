import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, of, Subscription, switchMap, take, takeWhile, timer } from 'rxjs';

import { AiBox, BwButton } from '../../../shared';
import { errorMessage } from '../../../shared/models/api-response.model';
import { LongueurResume, ModeResume, ResumeLocal, Traitement } from '../models/cours.model';
import { CoursService } from '../services/cours.service';
import { ResumeLocalAudio } from './resume-local-audio';

const LIBELLES_ETAPES: Record<string, string> = {
  TEXTE: 'Lecture du cours',
  RESUME: 'Résumé par le modèle local',
  AUDIO: 'Mise en voix',
};

const LIBELLES_MODE: Record<ModeResume, string> = { auto: 'Auto', abstractif: 'Reformulé', extractif: 'Phrases clés' };
const LIBELLES_LONGUEUR: Record<LongueurResume, string> = { courte: 'Courte', moyenne: 'Moyenne', longue: 'Longue' };

/**
 * Studio du résumé automatique LOCAL (formateur / ADMIN) : un modèle Hugging Face tourne sur le serveur, sans clé API ni IA générative
 * distante. DISTINCT du « Résumé IA » (fiche de révision). Le formateur choisit la longueur et le mode (Auto : reformulé, remplacé par les
 * phrases clés du cours s'il est peu fidèle), génère, relit phrase par phrase avec sa source, écoute l'audio puis valide.
 */
@Component({
  selector: 'app-resume-local-studio',
  imports: [FormsModule, MatIconModule, MatSnackBarModule, AiBox, BwButton, ResumeLocalAudio],
  template: `
    <div class="commandes">
      <div class="onglets" role="group" aria-label="Longueur">
        @for (l of longueurs; track l) {
          <button type="button" [class.on]="longueur() === l" [attr.aria-pressed]="longueur() === l" [disabled]="attente()" (click)="longueur.set(l)">{{ libellesLongueur[l] }}</button>
        }
      </div>
      <div class="onglets" role="group" aria-label="Mode">
        @for (m of modes; track m) {
          <button type="button" [class.on]="mode() === m" [attr.aria-pressed]="mode() === m" [disabled]="attente()" (click)="mode.set(m)">{{ libellesMode[m] }}</button>
        }
      </div>
      @if (!resume()) {
        <button type="button" bwButton="ia" [disabled]="attente() || lancement()" (click)="generer()">Générer le résumé automatique</button>
      }
    </div>
    <p class="muted small">
      {{ aideMode() }}
    </p>

    @if (attente()) {
      <div class="suivi" role="status" aria-live="polite">
        <strong>Le modèle local travaille…</strong>
        <ol>
          @for (e of traitement()?.etapes ?? []; track e.etape) {
            <li [class]="e.statut.toLowerCase()">
              <mat-icon aria-hidden="true">{{ icone(e.statut) }}</mat-icon>
              {{ libelleEtape(e.etape) }}
            </li>
          }
        </ol>
        <p class="muted small">Premier lancement : téléchargement du modèle, quelques minutes. Les suivants prennent de quelques secondes à une minute. Tu peux quitter cette page, le travail continue.</p>
      </div>
    } @else if (echec(); as echec) {
      <p class="alerte" role="alert">
        <mat-icon aria-hidden="true">error</mat-icon>
        <span><strong>{{ libelleEtape(echec.etape) }} : échec.</strong> {{ echec.erreur }}</span>
      </p>
    }

    @if (resume(); as r) {
      <bw-ai-box
        label="Généré par un modèle local"
        [heading]="r.statut === 'VALIDE' ? 'Résumé automatique (validé)' : 'Résumé automatique (brouillon à relire)'"
        [regenerable]="!attente()"
        [validatable]="r.statut !== 'VALIDE' && !attente()"
        [busy]="envoi() || attente()"
        (regenerate)="generer()"
        (validate)="valider()"
      >
        <div class="entete">
          <span class="pill ia plain">Langue détectée : {{ r.langue === 'fr' ? 'FR' : 'EN' }}</span>
          <span class="pill plain">{{ r.modeUtilise === 'extractif' ? 'Phrases clés du cours' : 'Résumé reformulé' }}</span>
          @if (r.modele) {
            <span class="muted small">Modèle : {{ r.modele }}</span>
          }
        </div>

        <ol class="phrases">
          @for (p of r.phrases; track p.ordre) {
            <li [class.suspecte]="p.justifiee === false">
              <p class="texte" dir="auto">{{ p.texte }}</p>
              <div class="source">
                @if (p.page) {
                  <span class="muted small">Source : page {{ p.page }}</span>
                }
                @if (p.justifiee === false) {
                  <span class="alerte-ligne"><mat-icon aria-hidden="true">warning</mat-icon> Ne ressemble à aucune phrase du cours : vérifie-la.</span>
                }
              </div>
              @if (r.modeUtilise === 'abstractif' && p.extraitSource) {
                <blockquote><span class="lbl">Phrase du cours la plus proche</span> « {{ p.extraitSource }} »</blockquote>
              }
            </li>
          }
        </ol>

        @if (r.scoreFidelite !== undefined) {
          <section class="controle">
            <h4>Fidélité au cours <span class="muted small">(visible par toi seul)</span></h4>
            <div class="score" role="meter" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="r.scoreFidelite" [attr.aria-label]="'Fidélité au cours : ' + r.scoreFidelite + ' sur 100'">
              <span class="barre"><span [class]="'rempli ' + niveau()" [style.width.%]="r.scoreFidelite"></span></span>
              <span class="valeur num">{{ r.scoreFidelite }} / 100</span>
              <span [class]="'pill ' + niveau()">{{ libelleNiveau() }}</span>
            </div>
            @if (r.scoreReformule !== undefined && r.modeUtilise === 'extractif') {
              <p class="muted small">Le résumé reformulé avait obtenu {{ r.scoreReformule }} / 100 : il a été remplacé par les phrases clés du cours.</p>
            }
            @if (r.alertes?.length) {
              <ul class="alertes">
                @for (a of r.alertes; track a) {
                  <li><mat-icon aria-hidden="true">warning</mat-icon>{{ a }}</li>
                }
              </ul>
            }
            @if (r.dureeCalculMs) {
              <p class="muted small meta">Calculé en {{ secondes(r.dureeCalculMs) }} s.</p>
            }
          </section>
        }

        @if (r.statut === 'VALIDE') {
          <p class="valid"><mat-icon>verified</mat-icon> Validé : visible par les apprenants dans le lecteur du cours.</p>
        } @else if (r.valideeDisponible) {
          <p class="muted small">Les apprenants voient encore la version validée précédente, jusqu’à ce que tu valides celle-ci.</p>
        }
      </bw-ai-box>
      <app-resume-local-audio [coursId]="coursId()" [actif]="r.aAudio" [cle]="r.id + '|' + (r.dureeAudio ?? 0)" [fournisseur]="r.fournisseurAudio" />
      @if (!r.aAudio && !attente()) {
        <p class="muted small">Pas encore d’audio pour ce résumé : il est produit à la validation si besoin.</p>
      }
    } @else if (etat() === 'chargement') {
      <p class="muted small" role="status">Chargement…</p>
    } @else if (etat() === 'ok' && !attente()) {
      <div class="vide">
        <mat-icon>auto_stories</mat-icon>
        <p>Un modèle de résumé installé sur le serveur produit un résumé du cours, en français ou en anglais selon sa langue, sans clé API. S’il reformule de façon peu fidèle, Bawsla le remplace par les phrases clés du cours. Chaque phrase est rapprochée du cours avec sa page.</p>
      </div>
    }
  `,
  styles: `
    :host { display: grid; gap: var(--space-4); }
    .commandes { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-3); }
    .onglets { display: inline-flex; border: 1px solid var(--color-border); border-radius: 999px; overflow: hidden; }
    .onglets button { min-height: 44px; padding: 0 var(--space-4); border: 0; background: var(--color-surface); color: var(--color-text); font: 600 14px var(--font-body); cursor: pointer; }
    .onglets button.on { background: var(--color-primary); color: var(--color-on-primary); }
    .onglets button:disabled { opacity: 0.6; cursor: default; }
    .suivi { padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .suivi ol { margin: var(--space-2) 0; padding: 0; list-style: none; display: grid; gap: var(--space-1); }
    .suivi li { display: flex; align-items: center; gap: var(--space-2); }
    .suivi li.termine { color: var(--color-success); }
    .suivi li.echec { color: var(--color-error); }
    .suivi li.en_attente, .suivi li.annule { color: var(--color-text-muted); }
    .suivi mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .alerte, .alertes li { display: flex; align-items: flex-start; gap: var(--space-2); color: var(--color-warning); font-size: 14px; font-weight: 600; }
    .alerte { margin: 0; }
    .alerte mat-icon, .alertes mat-icon, .alerte-ligne mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; }
    .alertes { margin: var(--space-3) 0 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
    .entete { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2); margin-bottom: var(--space-3); }
    .phrases { margin: 0; padding: 0; list-style: none; display: grid; gap: var(--space-3); }
    .phrases li { padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .phrases li.suspecte { border-color: var(--color-warning); background: color-mix(in srgb, var(--color-warning) 8%, var(--color-surface)); }
    .texte { margin: 0; line-height: 24px; }
    .source { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); margin-top: var(--space-2); }
    .alerte-ligne { display: inline-flex; align-items: center; gap: var(--space-1); color: var(--color-warning); font-size: 14px; font-weight: 600; }
    blockquote { margin: var(--space-2) 0 0; padding-left: var(--space-3); border-left: 3px solid var(--color-gold-500); color: var(--color-text-muted); font-size: 14px; font-style: italic; }
    blockquote .lbl { display: block; font-style: normal; font-size: 12px; font-weight: 600; }
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
    .vide { display: grid; justify-items: start; gap: var(--space-3); padding: var(--space-4); border: 1px dashed var(--color-border); border-radius: var(--radius-sm); p { margin: 0; } }
    p { margin: 0; }
  `,
})
export class ResumeLocalStudio implements OnInit, OnDestroy {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);

  readonly coursId = input.required<number>();

  protected readonly longueurs: LongueurResume[] = ['courte', 'moyenne', 'longue'];
  protected readonly modes: ModeResume[] = ['auto', 'abstractif', 'extractif'];
  protected readonly libellesLongueur = LIBELLES_LONGUEUR;
  protected readonly libellesMode = LIBELLES_MODE;

  protected readonly longueur = signal<LongueurResume>('moyenne');
  protected readonly mode = signal<ModeResume>('auto');
  protected readonly etat = signal<'chargement' | 'ok' | 'erreur'>('chargement');
  protected readonly resume = signal<ResumeLocal | null>(null);
  protected readonly traitement = signal<Traitement | null>(null);
  protected readonly attente = signal(false);
  protected readonly lancement = signal(false);
  protected readonly envoi = signal(false);

  protected readonly echec = computed(() => {
    const t = this.traitement();
    return !this.attente() && t ? t.etapes.find((e) => e.statut === 'ECHEC') : undefined;
  });
  protected readonly niveau = computed<'ok' | 'warn' | 'ko'>(() => {
    const s = this.resume()?.scoreFidelite ?? 0;
    return s >= 80 ? 'ok' : s >= 50 ? 'warn' : 'ko';
  });
  protected readonly libelleNiveau = computed(() => ({ ok: 'Fidèle', warn: 'À vérifier', ko: 'Fidélité faible' })[this.niveau()]);
  protected readonly aideMode = computed(
    () =>
      ({
        auto: 'Auto : le modèle reformule le cours ; si le résultat est peu fidèle, il est remplacé par les phrases clés du cours.',
        abstractif: 'Reformulé : le modèle réécrit le cours avec ses mots. Relis-le : il peut se tromper.',
        extractif: 'Phrases clés : les phrases les plus importantes du cours, recopiées sans modification.',
      })[this.mode()],
  );

  private suivi?: Subscription;

  ngOnInit(): void {
    this.charger();
    // Un traitement lancé avant l'arrivée sur la page continue : on le reprend.
    this.service.traitementResumeLocal(this.coursId()).subscribe({
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

  protected charger(): void {
    this.etat.set('chargement');
    this.service.resumeLocal(this.coursId()).subscribe({
      next: (r) => {
        this.resume.set(r);
        this.etat.set('ok');
      },
      error: (err) => {
        this.etat.set('erreur');
        this.snack.open(errorMessage(err, 'Impossible de charger le résumé automatique'), 'OK', { duration: 5000 });
      },
    });
  }

  protected generer(): void {
    if (this.lancement() || this.attente()) return;
    this.lancement.set(true);
    this.service.genererResumeLocal(this.coursId(), this.longueur(), this.mode()).subscribe({
      next: (t) => {
        this.lancement.set(false);
        this.traitement.set(t);
        this.suivre();
      },
      error: (err) => {
        this.lancement.set(false);
        this.snack.open(errorMessage(err, 'Impossible de lancer le résumé automatique'), 'OK', { duration: 7000 });
      },
    });
  }

  protected valider(): void {
    if (this.envoi()) return;
    this.envoi.set(true);
    this.service.validerResumeLocal(this.coursId()).subscribe({
      next: (r) => {
        this.envoi.set(false);
        this.resume.set(r);
        if (r.aAudio) {
          this.snack.open('Résumé validé : les apprenants peuvent le lire.', 'OK', { duration: 4000 });
        } else {
          this.snack.open('Résumé validé. L’audio est en cours de préparation.', 'OK', { duration: 5000 });
          this.suivre();
        }
      },
      error: (err) => {
        this.envoi.set(false);
        this.snack.open(errorMessage(err, 'Validation impossible'), 'OK', { duration: 7000 });
      },
    });
  }

  protected libelleEtape(etape: string): string {
    return LIBELLES_ETAPES[etape] ?? etape;
  }

  protected icone(statut: string): string {
    return statut === 'TERMINE' ? 'check_circle' : statut === 'ECHEC' ? 'error' : statut === 'EN_COURS' ? 'autorenew' : 'schedule';
  }

  protected secondes(ms: number): string {
    return (ms / 1000).toFixed(ms < 10_000 ? 1 : 0);
  }

  /** Interroge le serveur toutes les 2 s jusqu'à la fin du traitement (au plus 10 min : le premier chargement du modèle est long). */
  private suivre(): void {
    this.arreterSuivi();
    this.attente.set(true);
    this.suivi = timer(0, 2000)
      .pipe(
        switchMap(() => this.service.traitementResumeLocal(this.coursId()).pipe(catchError(() => of(null)))),
        takeWhile((t) => t === null || t.enCours, true),
        take(300),
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
