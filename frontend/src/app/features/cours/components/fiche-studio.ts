import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, catchError, of, switchMap, take, takeWhile, timer } from 'rxjs';

import { AiBox, AiLoading, BwButton, EmptyState } from '../../../shared';
import { errorMessage } from '../../../shared/models/api-response.model';
import { Skeleton } from '../../../shared/ui';
import { EtapeIA, Fiche, FicheRequest, StatutTraitement, Traitement } from '../models/cours.model';
import { CoursService } from '../services/cours.service';
import { FicheAudio } from './fiche-audio';
import { FicheContenu } from './fiche-contenu';

/** Fiche en cours de correction (les mots-clés sont saisis séparés par des virgules). */
interface BrouillonFiche {
  resume: string;
  motsCles: string;
  notions: { titre: string; definition: string; extraitSource: string }[];
}

/**
 * Fiche de révision générée par l'IA (formateur / ADMIN) : génération en arrière-plan avec suivi des étapes, relecture, correction
 * (le serveur recalcule le score de fidélité), régénération et validation. Extrait tel quel de l'éditeur du studio : mêmes appels HTTP,
 * même suivi (interrogation toutes les 2 s, bornée à 5 minutes, arrêtée à la fin, à l'annulation et à la destruction du composant).
 */
@Component({
  selector: 'app-fiche-studio',
  imports: [FormsModule, MatIconModule, MatSnackBarModule, AiBox, AiLoading, BwButton, EmptyState, Skeleton, FicheAudio, FicheContenu],
  template: `
    @if (attente()) {
      <div class="ia-attente">
        <bw-ai-loading message="La boussole analyse ton cours…" (cancel)="arreterAttente()" />
        <ol class="etapes" aria-label="Avancement de la génération">
          @for (e of etapesIA; track e.etape) {
            <li [class]="'etape ' + statutEtape(e.etape).toLowerCase()">
              <mat-icon aria-hidden="true">{{ iconeEtape(statutEtape(e.etape)) }}</mat-icon> {{ e.libelle }}
            </li>
          }
        </ol>
      </div>
    } @else {
      @if (echecIA(); as echec) {
        <p class="alerte" role="alert">
          <mat-icon aria-hidden="true">error</mat-icon>
          <span><strong>{{ libelleEtape(echec.etape) }} : échec.</strong> {{ echec.erreur }}</span>
        </p>
      }
      @if (fiche(); as f) {
        <bw-ai-box
          [heading]="f.statut === 'VALIDEE' ? 'Fiche de révision (validée)' : 'Fiche de révision (brouillon à relire)'"
          [editable]="!brouillon()"
          [regenerable]="!brouillon()"
          [validatable]="f.statut !== 'VALIDEE' && !brouillon()"
          [busy]="ficheEnvoi() || lancement()"
          (edit)="modifierFiche()"
          (regenerate)="genererFiche()"
          (validate)="validerFiche()"
        >
          @if (brouillon(); as b) {
            <div class="correction">
              <label class="champ">
                <span class="lbl">Résumé</span>
                <textarea class="edit" rows="7" [ngModel]="b.resume" (ngModelChange)="majBrouillon('resume', $event)"></textarea>
              </label>
              <label class="champ">
                <span class="lbl">Mots-clés (séparés par des virgules)</span>
                <input class="edit" [ngModel]="b.motsCles" (ngModelChange)="majBrouillon('motsCles', $event)" />
              </label>
              <p class="lbl">Notions</p>
              @for (n of b.notions; track $index; let i = $index) {
                <fieldset class="notion-edit">
                  <legend>Notion {{ i + 1 }}</legend>
                  <input class="edit" placeholder="Titre" aria-label="Titre de la notion" [ngModel]="n.titre" (ngModelChange)="majNotion(i, 'titre', $event)" />
                  <textarea class="edit" rows="2" placeholder="Définition" aria-label="Définition" [ngModel]="n.definition" (ngModelChange)="majNotion(i, 'definition', $event)"></textarea>
                  <textarea class="edit" rows="2" placeholder="Extrait du cours qui justifie la notion" aria-label="Extrait source" [ngModel]="n.extraitSource" (ngModelChange)="majNotion(i, 'extraitSource', $event)"></textarea>
                  <button type="button" bwButton="fantome" (click)="retirerNotion(i)"><mat-icon>delete</mat-icon> Retirer cette notion</button>
                </fieldset>
              }
              <button type="button" bwButton="fantome" (click)="ajouterNotion()"><mat-icon>add</mat-icon> Ajouter une notion</button>
              <p class="muted small">À l’enregistrement, la fidélité de ta version au cours est recalculée.</p>
              <div class="actions-correction">
                <button type="button" bwButton="fantome" (click)="annulerCorrection()" [disabled]="ficheEnvoi()">Annuler</button>
                <button type="button" bwButton="secondaire" (click)="enregistrerCorrection()" [disabled]="ficheEnvoi()">
                  {{ ficheEnvoi() ? 'Enregistrement…' : 'Enregistrer mes corrections' }}
                </button>
              </div>
            </div>
          } @else {
            <app-fiche-contenu [fiche]="f" [gestionnaire]="true" />
          }
          @if (f.statut === 'VALIDEE') {
            <p class="valid"><mat-icon>verified</mat-icon> Validée : visible par les apprenants dans le lecteur du cours.</p>
          } @else if (f.valideeDisponible) {
            <p class="muted small">Les apprenants voient encore la version validée précédente, jusqu’à ce que tu valides celle-ci.</p>
          }
        </bw-ai-box>
        <app-fiche-audio class="audio-fiche" [coursId]="coursId()" [version]="f.id" [serveur]="f.aAudio" [fournisseur]="f.fournisseurAudio" [texte]="f.resume" />
        @if (!f.aAudio) {
          <p class="muted small">L’audio de cette version est créé à la validation si besoin.</p>
        }
      } @else if (ficheEtat() === 'chargement') {
        <bw-skeleton height="160px" />
      } @else if (ficheEtat() === 'erreur') {
        <bw-empty-state icon="error" title="Impossible de charger la fiche." actionLabel="Réessayer" (action)="chargerFiche()" />
      } @else {
        <div class="ai-empty">
          <mat-icon>explore</mat-icon>
          <p>Bawsla propose une fiche de révision à partir de ton PDF (ou du contenu des chapitres) : un résumé, des mots-clés et les notions importantes, avec l’extrait du cours qui les justifie. Tu la relis avant de la rendre visible aux apprenants.</p>
          <button type="button" bwButton="ia" [bwLoading]="lancement()" [disabled]="lancement()" (click)="genererFiche()">Générer la fiche</button>
        </div>
      }
    }
  `,
  styles: `
    :host { display: block; }
    .lbl { margin: 0 0 var(--space-2); font-weight: 600; }
    .ai-empty { display: grid; justify-items: start; gap: var(--space-3); padding: var(--space-5); border: 1px dashed var(--color-gold-500); border-radius: var(--radius-sm); mat-icon { color: var(--color-gold-700); } p { margin: 0; } }
    .edit { width: 100%; padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: 400 15px/24px var(--font-body); box-sizing: border-box; }
    .valid { display: flex; align-items: center; gap: 6px; color: var(--color-success); font-weight: 600; }
    .alerte { display: flex; align-items: flex-start; gap: var(--space-2); margin: 0 0 var(--space-3); padding: var(--space-3); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-gold-100); color: var(--color-gold-700); mat-icon { flex: none; } }
    .ia-attente { display: grid; gap: var(--space-3); padding: var(--space-3); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-gold-100); }
    .etapes { display: grid; gap: var(--space-2); margin: 0; padding: 0; list-style: none; }
    .etape { display: flex; align-items: center; gap: var(--space-2); color: var(--color-text-muted); mat-icon { font-size: 22px; width: 22px; height: 22px; } }
    .etape.en_cours { color: var(--color-gold-700); font-weight: 600; mat-icon { animation: bw-tourne 2s linear infinite; } }
    .etape.termine { color: var(--color-success); }
    .etape.echec { color: var(--color-error); font-weight: 600; }
    @keyframes bw-tourne { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .etape.en_cours mat-icon { animation: none; } }
    .correction { display: grid; gap: var(--space-3); }
    .champ { display: grid; gap: var(--space-1); }
    .notion-edit { display: grid; gap: var(--space-2); margin: 0; padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); legend { padding: 0 var(--space-2); font-weight: 600; font-size: 14px; } }
    .actions-correction { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); }
    .audio-fiche { margin-top: var(--space-3); }
  `,
})
export class FicheStudio implements OnInit {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly coursId = input.required<number>();

  protected readonly fiche = signal<Fiche | null>(null);
  protected readonly ficheEtat = signal<'chargement' | 'ok' | 'erreur'>('chargement');
  /** Dernier lancement de l'IA sur ce cours (étapes, durées, erreurs). */
  protected readonly traitement = signal<Traitement | null>(null);
  /** true tant qu'on attend la fin d'une génération (le traitement tourne côté serveur). */
  protected readonly attente = signal(false);
  protected readonly lancement = signal(false);
  protected readonly ficheEnvoi = signal(false);
  protected readonly brouillon = signal<BrouillonFiche | null>(null);
  protected readonly etapesIA: { etape: EtapeIA; libelle: string }[] = [
    { etape: 'TEXTE', libelle: 'Lecture du cours' },
    { etape: 'FICHE', libelle: 'Rédaction de la fiche' },
    { etape: 'AUDIO', libelle: 'Création de l’audio' },
  ];
  /** Message d'une étape en échec dans le dernier lancement (affiché tant qu'aucune nouvelle génération n'a commencé). */
  protected readonly echecIA = computed(() => this.traitement()?.etapes.find((e) => e.statut === 'ECHEC') ?? null);
  private suivi?: Subscription;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.arreterSuivi());
    this.chargerFiche();
    this.reprendreSuivi();
  }

  protected chargerFiche(): void {
    this.ficheEtat.set('chargement');
    this.service.fiche(this.coursId()).subscribe({
      next: (f) => {
        this.fiche.set(f); // null : pas encore de fiche, état vide normal
        this.ficheEtat.set('ok');
      },
      error: (err) => {
        this.ficheEtat.set('erreur');
        this.snack.open(errorMessage(err, 'Impossible de charger la fiche'), 'OK', { duration: 5000 });
      },
    });
  }

  /** Lance la génération (la première fois comme les suivantes : l'historique des fiches est conservé côté serveur). */
  protected genererFiche(): void {
    if (this.lancement() || this.attente()) return;
    this.lancement.set(true);
    this.brouillon.set(null);
    this.service.genererFiche(this.coursId()).subscribe({
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

  /** Au chargement : retrouve un traitement encore en cours (page rechargée) ou le dernier échec. */
  private reprendreSuivi(): void {
    this.service.traitement(this.coursId()).subscribe({
      next: (t) => {
        this.traitement.set(t);
        if (t.enCours) this.suivre();
      },
      error: () => undefined,
    });
  }

  /** Interroge le serveur toutes les 2 s jusqu'à la fin du traitement (au plus 5 min), puis recharge la fiche. */
  private suivre(): void {
    this.arreterSuivi();
    this.attente.set(true);
    this.suivi = timer(0, 2000)
      .pipe(
        switchMap(() => this.service.traitement(this.coursId()).pipe(catchError(() => of(null)))),
        takeWhile((t) => t === null || t.enCours, true),
        take(150),
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
    this.chargerFiche();
    const echec = this.echecIA();
    if (echec) {
      this.snack.open(echec.erreur ?? 'La génération a échoué.', 'OK', { duration: 8000 });
    }
  }

  private arreterSuivi(): void {
    this.suivi?.unsubscribe();
    this.suivi = undefined;
  }

  /** « Annuler » de l'attente : on arrête de surveiller ; le traitement, lui, continue sur le serveur. */
  protected arreterAttente(): void {
    this.arreterSuivi();
    this.attente.set(false);
    this.snack.open('La génération continue en arrière-plan. Reviens sur cette page dans un instant.', 'OK', { duration: 6000 });
  }

  protected statutEtape(etape: EtapeIA): StatutTraitement {
    return this.traitement()?.etapes.find((e) => e.etape === etape)?.statut ?? 'EN_ATTENTE';
  }

  protected libelleEtape(etape: EtapeIA): string {
    return this.etapesIA.find((e) => e.etape === etape)?.libelle ?? etape;
  }

  protected iconeEtape(statut: StatutTraitement): string {
    switch (statut) {
      case 'TERMINE': return 'check_circle';
      case 'EN_COURS': return 'autorenew';
      case 'ECHEC': return 'error';
      case 'ANNULE': return 'block';
      default: return 'radio_button_unchecked';
    }
  }

  // ---- Correction par le formateur

  protected modifierFiche(): void {
    const f = this.fiche();
    if (!f) return;
    this.brouillon.set({
      resume: f.resume,
      motsCles: f.motsCles.join(', '),
      notions: f.notions.map((n) => ({ titre: n.titre, definition: n.definition, extraitSource: n.extraitSource ?? '' })),
    });
  }

  protected annulerCorrection(): void {
    this.brouillon.set(null);
  }

  protected majBrouillon(champ: 'resume' | 'motsCles', valeur: string): void {
    this.brouillon.update((b) => (b ? { ...b, [champ]: valeur } : b));
  }

  protected majNotion(index: number, champ: 'titre' | 'definition' | 'extraitSource', valeur: string): void {
    this.brouillon.update((b) => (b ? { ...b, notions: b.notions.map((n, i) => (i === index ? { ...n, [champ]: valeur } : n)) } : b));
  }

  protected ajouterNotion(): void {
    this.brouillon.update((b) => (b ? { ...b, notions: [...b.notions, { titre: '', definition: '', extraitSource: '' }] } : b));
  }

  protected retirerNotion(index: number): void {
    this.brouillon.update((b) => (b ? { ...b, notions: b.notions.filter((_, i) => i !== index) } : b));
  }

  /** Enregistre la correction : le serveur recalcule le score de fidélité avec le texte du cours. */
  protected enregistrerCorrection(): void {
    const b = this.brouillon();
    if (!b || this.ficheEnvoi()) return;
    const corps: FicheRequest = {
      resume: b.resume.trim(),
      motsCles: b.motsCles.split(/[,\n]/).map((m) => m.trim()).filter(Boolean),
      notions: b.notions
        .filter((n) => n.titre.trim() || n.definition.trim())
        .map((n) => ({ titre: n.titre.trim(), definition: n.definition.trim(), extraitSource: n.extraitSource.trim() })),
    };
    this.ficheEnvoi.set(true);
    this.service.corrigerFiche(this.coursId(), corps).subscribe({
      next: (f) => {
        this.ficheEnvoi.set(false);
        this.fiche.set(f);
        this.brouillon.set(null);
        this.snack.open(`Fiche enregistrée. Fidélité recalculée : ${f.scoreFidelite ?? '—'} / 100`, 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.ficheEnvoi.set(false);
        this.snack.open(errorMessage(err, 'Enregistrement de la fiche impossible'), 'OK', { duration: 7000 });
      },
    });
  }

  /** Rend la fiche visible des apprenants. Si son audio manque, le serveur le produit en arrière-plan : on le suit. */
  protected validerFiche(): void {
    if (this.ficheEnvoi()) return;
    this.ficheEnvoi.set(true);
    this.service.validerFiche(this.coursId()).subscribe({
      next: (f) => {
        this.ficheEnvoi.set(false);
        this.fiche.set(f);
        this.snack.open('Fiche validée : les apprenants peuvent la lire.', 'OK', { duration: 4000 });
        if (!f.aAudio) this.reprendreSuivi();
      },
      error: (err) => {
        this.ficheEnvoi.set(false);
        this.snack.open(errorMessage(err, 'Validation impossible'), 'OK', { duration: 6000 });
      },
    });
  }
}
