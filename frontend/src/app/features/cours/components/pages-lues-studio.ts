import { Component, DestroyRef, OnDestroy, OnInit, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subscription, catchError, of, switchMap, take, takeWhile, timer } from 'rxjs';

import { BwButton } from '../../../shared';
import { errorMessage } from '../../../shared/models/api-response.model';
import { Dropzone } from '../../../shared/ui';
import { PageLue, PagesLues, Traitement } from '../models/cours.model';
import { CoursService } from '../services/cours.service';

const IMAGE_MAX = 5 * 1024 * 1024;

/** Aperçu d'une image déposée (elle exige le JWT : téléchargée en blob, puis libérée). Une seule requête par page, aucune relance. */
@Component({
  selector: 'app-apercu-page',
  template: `
    @if (url(); as source) {
      <img [src]="source" [alt]="'Image de la page ' + numero()" />
    } @else if (erreur()) {
      <p class="muted small">Aperçu indisponible.</p>
    }
  `,
  styles: `
    :host { display: block; }
    img { display: block; max-width: 160px; max-height: 120px; border: 1px solid var(--color-border); border-radius: var(--radius-sm); object-fit: contain; background: var(--color-surface); }
    p { margin: 0; }
  `,
})
export class ApercuPage implements OnDestroy {
  private readonly service = inject(CoursService);

  readonly coursId = input.required<number>();
  readonly numero = input.required<number>();
  protected readonly url = signal<string | null>(null);
  protected readonly erreur = signal(false);
  private cleChargee: string | null = null;
  private requete?: Subscription;

  constructor() {
    effect(() => {
      const id = this.coursId();
      const numero = this.numero();
      untracked(() => this.charger(`${id}|${numero}`, id, numero)); // hors suivi : écrire url/erreur ne relance pas l'effet
    });
  }

  private charger(cle: string, id: number, numero: number): void {
    if (cle === this.cleChargee) return;
    this.cleChargee = cle;
    this.liberer();
    this.requete = this.service.imagePage(id, numero).subscribe({
      next: (blob) => this.url.set(URL.createObjectURL(blob)),
      error: () => this.erreur.set(true),
    });
  }

  private liberer(): void {
    this.requete?.unsubscribe();
    const actuelle = this.url();
    if (actuelle) URL.revokeObjectURL(actuelle);
    this.url.set(null);
    this.erreur.set(false);
  }

  ngOnDestroy(): void {
    this.liberer();
  }
}

/** Une page lue par le modèle local de secours (« local · Florence-2 »). */
function estLocale(p: PageLue): boolean {
  return !!p.moteur?.startsWith('local');
}

/** Délai maximal d'une page (Gemini 90 s puis modèle local 120 s, cours-service 240 s) et plafond du suivi. */
const DELAI_PAGE_S = 240;
const PLAFOND_SUIVI_S = 45 * 60;

/**
 * Pages lues par l'IA (vision) : PDF scanné ou photo de page, dans l'étape 3 du studio. Le formateur lance la lecture (elle consomme le quota
 * de l'IA, donc jamais automatique), suit l'avancement « 2/4 », puis LIT et CORRIGE le texte lu avant de générer la fiche.
 * Suivi par interrogation toutes les 2 s, borné selon le nombre de pages du lot (240 s par page au plus, 45 min maximum), arrêté à la fin,
 * sur erreur, après le délai et à la destruction du composant. Quand Gemini est indisponible, un modèle local (plus lent) lit les pages.
 */
@Component({
  selector: 'app-pages-lues-studio',
  imports: [FormsModule, MatIconModule, MatSnackBarModule, BwButton, Dropzone, ApercuPage],
  template: `
    <section class="pages" aria-labelledby="pages-lues-h">
      <h3 id="pages-lues-h"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Pages lues par l’IA (vision)</h3>
      <p class="muted small">
        Un PDF scanné ou une photo de page n’a pas de texte : l’IA le lit pour que la fiche, l’explication et le résumé puissent s’en servir.
        Chaque page est envoyée à l’IA (Gemini) telle quelle ; le texte lu, lui, est nettoyé des e-mails et téléphones.
        Si Gemini est indisponible, un modèle local plus lent peut prendre le relais, sans rien envoyer à l’extérieur.
      </p>

      @if (donnees(); as d) {
        @if (d.aLire > 0 && !attente()) {
          <p class="alerte" role="status">
            <mat-icon aria-hidden="true">image_search</mat-icon>
            <span>
              <strong>{{ d.aLire }} page{{ d.aLire > 1 ? 's' : '' }} sans texte.</strong>
              @if (d.aLire > d.plafond) { {{ d.plafond }} pages sont lues par lancement : relance ensuite pour les suivantes. }
            </span>
          </p>
          <div class="actions">
            <button type="button" bwButton="ia" [disabled]="lancement()" (click)="lire()">
              <mat-icon aria-hidden="true">visibility</mat-icon> Lire {{ d.aLire > d.plafond ? d.plafond : d.aLire }} page{{ d.aLire > 1 ? 's' : '' }} avec l’IA
            </button>
          </div>
        }
      }

      @if (attente()) {
        <div class="suivi" role="status" aria-live="polite">
          <mat-icon class="tourne" aria-hidden="true">autorenew</mat-icon>
          <span>
            @if (lectureLocale()) { Lecture locale en cours (plus lente)… } @else { Lecture des pages }
            {{ avancement() }}
          </span>
          <button type="button" bwButton="fantome" (click)="arreterAttente()">Masquer le suivi</button>
        </div>
      } @else if (reste(); as r) {
        <p class="alerte" role="status"><mat-icon aria-hidden="true">info</mat-icon> <span>{{ r }}</span></p>
      } @else if (echec(); as e) {
        <p class="erreur" role="alert"><mat-icon aria-hidden="true">error</mat-icon> <span>{{ e.erreur }} Les pages déjà lues sont conservées ; relance pour lire les autres.</span></p>
      }

      <bw-dropzone accept="image/png,image/jpeg,.png,.jpg,.jpeg" hint="Ajouter une page en image : PNG ou JPG, 5 Mo maximum. Elle devient la page suivante du cours." (files)="deposer($event)" />
      @if (erreurDepot()) { <p class="erreur" role="alert">{{ erreurDepot() }}</p> }
      @if (depot()) { <p class="muted small" role="status">Envoi de l’image…</p> }

      @if (donnees()?.pages?.length) {
        <ul class="liste" aria-label="Pages lues par l’IA">
          @for (p of donnees()!.pages; track p.numero) {
            <li class="page">
              <div class="entete">
                <strong>Page {{ p.numero }}</strong>
                @if (p.source === 'VISION' && !p.sansTexte) { <span class="pill ia plain"><mat-icon aria-hidden="true">visibility</mat-icon> {{ badgeMoteur(p) }}</span> }
                @if (p.aLire) { <span class="pill plain">À lire</span> }
                @if (p.source === 'VISION' && p.sansTexte && !p.aLire) { <span class="pill ko">Illisible</span> }
                @if (p.modifie) { <span class="pill ok">Corrigé</span> }
              </div>
              <div class="corps">
                @if (p.aImage) { <app-apercu-page [coursId]="coursId()" [numero]="p.numero" /> }
                <label class="champ">
                  <span class="sr-only">Texte de la page {{ p.numero }}</span>
                  <textarea
                    class="edit"
                    rows="6"
                    dir="auto"
                    [attr.aria-label]="'Texte lu de la page ' + p.numero"
                    [disabled]="p.aLire && p.sansTexte && attente()"
                    [ngModel]="texteAffiche(p)"
                    (ngModelChange)="saisir(p.numero, $event)"
                    placeholder="{{ p.aLire ? 'Pas encore lue. Lance la lecture, ou saisis le texte à la main.' : 'Aucun texte lisible. Tu peux le saisir à la main.' }}"
                  ></textarea>
                </label>
              </div>
              <div class="actions">
                @if (p.aImage) {
                  <button type="button" bwButton="fantome" (click)="supprimer(p)"><mat-icon aria-hidden="true">delete</mat-icon> Supprimer l’image</button>
                }
                <button type="button" bwButton="secondaire" [disabled]="!modifiee(p) || enregistrement() === p.numero" (click)="enregistrer(p)">
                  {{ enregistrement() === p.numero ? 'Enregistrement…' : 'Enregistrer la correction' }}
                </button>
              </div>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .pages { display: grid; gap: var(--space-3); margin-top: var(--space-5); padding: var(--space-4); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-surface); }
    h3 { display: flex; align-items: center; gap: var(--space-2); margin: 0; font: 600 18px var(--font-heading); }
    p { margin: 0; }
    .boussole { color: var(--color-gold-700); }
    .alerte { display: flex; align-items: flex-start; gap: var(--space-2); padding: var(--space-3); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-gold-100); color: var(--color-gold-700); mat-icon { flex: none; } }
    .erreur { display: flex; align-items: flex-start; gap: var(--space-2); color: var(--color-error); font-weight: 600; font-size: 14px; mat-icon { flex: none; } }
    .actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); }
    .suivi { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; padding: var(--space-3); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-gold-100); color: var(--color-gold-700); font-weight: 600; }
    .tourne { animation: bw-tourne 2s linear infinite; }
    @keyframes bw-tourne { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .tourne { animation: none; } }
    .liste { display: grid; gap: var(--space-3); margin: 0; padding: 0; list-style: none; }
    .page { display: grid; gap: var(--space-2); padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
    .entete { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-2); }
    .entete .pill mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .corps { display: flex; align-items: flex-start; gap: var(--space-3); flex-wrap: wrap; }
    .champ { flex: 1; min-width: 220px; }
    .edit { width: 100%; padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); font: 400 15px/24px var(--font-body); box-sizing: border-box; resize: vertical; }
    .edit:focus-visible { outline: 3px solid var(--color-gold-500); outline-offset: 2px; }
    .sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
  `,
})
export class PagesLuesStudio implements OnInit, OnDestroy {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly coursId = input.required<number>();
  /** Change quand le PDF du cours change (nouvel envoi) : la liste des pages est relue. */
  readonly cle = input<string | number>('');

  protected readonly donnees = signal<PagesLues | null>(null);
  protected readonly traitement = signal<Traitement | null>(null);
  protected readonly attente = signal(false);
  protected readonly lancement = signal(false);
  protected readonly depot = signal(false);
  protected readonly erreurDepot = signal<string | null>(null);
  protected readonly enregistrement = signal<number | null>(null);
  /** Textes en cours de saisie, par numéro de page (absent : texte du serveur). */
  protected readonly brouillons = signal<Record<number, string>>({});

  protected readonly etape = computed(() => this.traitement()?.etapes.find((e) => e.etape === 'LECTURE_IMAGES') ?? null);
  protected readonly avancement = computed(() => {
    const e = this.etape();
    return e?.total ? `${e.avancement ?? 0}/${e.total}` : '…';
  });
  /** Message de fin d'un lot arrêté volontairement (« 3 pages restantes : clique à nouveau sur Lire avec l'IA »). */
  protected readonly reste = computed(() => {
    const e = this.etape();
    return !this.attente() && e?.statut === 'TERMINE' && e.erreur ? e.erreur : null;
  });
  /** Pages déjà lues en local avant ce lancement : seules les nouvelles déclenchent le message de lecture locale. */
  private localesAvant = new Set<number>();
  /** Une page de ce lot est revenue du modèle local : les suivantes le seront aussi (plus lent). */
  protected readonly lectureLocale = computed(
    () => this.attente() && (this.donnees()?.pages ?? []).some((p) => estLocale(p) && !this.localesAvant.has(p.numero)),
  );
  protected readonly echec = computed(() => {
    const e = this.etape();
    return !this.attente() && e?.statut === 'ECHEC' ? e : null;
  });

  private suivi?: Subscription;
  private cleChargee: string | null = null;

  constructor() {
    effect(() => {
      const cle = `${this.coursId()}|${this.cle()}`;
      // Relit les pages quand le PDF change ; le chargement est hors suivi, sinon l'effet relirait ce qu'il écrit.
      untracked(() => {
        if (this.cleChargee !== null && cle !== this.cleChargee) this.charger();
        this.cleChargee = cle;
      });
    });
  }

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.arreter());
    this.charger();
    // Une lecture lancée avant l'arrivée sur la page continue : on la reprend.
    this.service.traitementPages(this.coursId()).subscribe({
      next: (t) => {
        this.traitement.set(t);
        if (t.enCours) this.surveiller();
      },
      error: () => undefined,
    });
  }

  ngOnDestroy(): void {
    this.arreter();
  }

  protected charger(): void {
    this.service.pagesLues(this.coursId()).subscribe({
      next: (d) => {
        this.donnees.set(d);
        // Un brouillon devenu identique au texte du serveur n'a plus de raison d'exister.
        this.brouillons.update((b) => Object.fromEntries(Object.entries(b).filter(([n]) => d.pages.some((p) => p.numero === Number(n)))));
      },
      error: (err) => this.snack.open(errorMessage(err, 'Impossible de charger les pages'), 'OK', { duration: 5000 }),
    });
  }

  protected lire(): void {
    if (this.lancement() || this.attente()) return;
    this.lancement.set(true);
    this.localesAvant = new Set((this.donnees()?.pages ?? []).filter(estLocale).map((p) => p.numero));
    this.service.lirePages(this.coursId()).subscribe({
      next: (t) => {
        this.lancement.set(false);
        this.traitement.set(t);
        this.surveiller();
      },
      error: (err) => {
        this.lancement.set(false);
        this.snack.open(errorMessage(err, 'Impossible de lancer la lecture'), 'OK', { duration: 7000 });
      },
    });
  }

  protected deposer(fichiers: File[]): void {
    const f = fichiers[0];
    this.erreurDepot.set(null);
    if (!f) return;
    const nom = f.name.toLowerCase();
    if (!['image/png', 'image/jpeg'].includes(f.type) && !/\.(png|jpe?g)$/.test(nom)) {
      this.erreurDepot.set('Format non pris en charge : PNG ou JPG uniquement.');
      return;
    }
    if (f.size > IMAGE_MAX) {
      this.erreurDepot.set('L’image dépasse 5 Mo.');
      return;
    }
    this.depot.set(true);
    this.service.deposerImage(this.coursId(), f).subscribe({
      next: () => {
        this.depot.set(false);
        this.snack.open('Image ajoutée : lance la lecture pour que l’IA en transcrive le texte.', 'OK', { duration: 5000 });
        this.charger();
      },
      error: (err) => {
        this.depot.set(false);
        this.erreurDepot.set(errorMessage(err, 'Envoi de l’image impossible'));
      },
    });
  }

  protected badgeMoteur(p: PageLue): string {
    if (p.moteur?.startsWith('gemini')) return 'Lu par l’IA · Gemini';
    return estLocale(p) ? 'Lu par l’IA · modèle local' : 'Lu par l’IA (vision)';
  }

  protected texteAffiche(p: PageLue): string {
    return this.brouillons()[p.numero] ?? p.texte;
  }

  protected saisir(numero: number, texte: string): void {
    this.brouillons.update((b) => ({ ...b, [numero]: texte }));
  }

  protected modifiee(p: PageLue): boolean {
    const b = this.brouillons()[p.numero];
    return b !== undefined && b.trim() !== p.texte.trim();
  }

  protected enregistrer(p: PageLue): void {
    const texte = this.brouillons()[p.numero];
    if (texte === undefined || this.enregistrement() !== null) return;
    this.enregistrement.set(p.numero);
    this.service.corrigerPage(this.coursId(), p.numero, texte).subscribe({
      next: () => {
        this.enregistrement.set(null);
        this.brouillons.update((b) => Object.fromEntries(Object.entries(b).filter(([n]) => Number(n) !== p.numero)));
        this.snack.open(`Page ${p.numero} enregistrée.`, 'OK', { duration: 3000 });
        this.charger();
      },
      error: (err) => {
        this.enregistrement.set(null);
        this.snack.open(errorMessage(err, 'Enregistrement impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  protected supprimer(p: PageLue): void {
    if (!window.confirm(`Supprimer l’image de la page ${p.numero} ? Les pages suivantes seront renumérotées.`)) return;
    this.service.supprimerPage(this.coursId(), p.numero).subscribe({
      next: () => {
        this.brouillons.set({});
        this.snack.open('Image supprimée.', 'OK', { duration: 3000 });
        this.charger();
      },
      error: (err) => this.snack.open(errorMessage(err, 'Suppression impossible'), 'OK', { duration: 6000 }),
    });
  }

  protected arreterAttente(): void {
    this.arreter();
    this.attente.set(false);
    this.snack.open('La lecture continue en arrière-plan. Reviens sur cette page dans un instant.', 'OK', { duration: 6000 });
  }

  /** Interroge le serveur toutes les 2 s jusqu'à la fin de la lecture, puis recharge les pages (et à chaque page lue, pour le moteur). */
  private surveiller(): void {
    this.arreter();
    this.attente.set(true);
    let dernier = -1;
    // 240 s par page au plus (Gemini puis le modèle local), 45 minutes au maximum : jamais d'attente sans fin.
    const secondes = Math.min(PLAFOND_SUIVI_S, (this.etape()?.total ?? 30) * DELAI_PAGE_S + 60);
    this.suivi = timer(0, 2000)
      .pipe(
        switchMap(() => this.service.traitementPages(this.coursId()).pipe(catchError(() => of(null)))),
        takeWhile((t) => t === null || t.enCours, true),
        take(Math.ceil(secondes / 2)),
      )
      .subscribe({
        next: (t) => {
          if (!t) return;
          this.traitement.set(t);
          const fait = t.etapes.find((e) => e.etape === 'LECTURE_IMAGES')?.avancement ?? 0;
          if (t.enCours && fait !== dernier) {
            dernier = fait;
            if (fait > 0) this.charger(); // la page qui vient d'être lue donne son moteur (message « lecture locale »)
          }
          if (!t.enCours) this.fin();
        },
        complete: () => this.attente.set(false),
      });
  }

  private fin(): void {
    this.attente.set(false);
    this.charger();
    const echec = this.etape();
    if (echec?.statut === 'ECHEC') this.snack.open(echec.erreur ?? 'La lecture a échoué.', 'OK', { duration: 8000 });
  }

  private arreter(): void {
    this.suivi?.unsubscribe();
    this.suivi = undefined;
  }
}
