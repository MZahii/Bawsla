import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Observable, Subscription, catchError, forkJoin, map, of, switchMap, take, takeWhile, tap, timer } from 'rxjs';

import { Badge } from '../../../../shared';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { formatDate } from '../../models/cours.constantes';
import { LangueExplication, ModeExplication } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';
import { DonneesPanneau, StudioIaPanneau } from './studio-ia-panneau';
import {
  Generation,
  ICONE_GENERATION,
  LIBELLE_STATUT_GENERATION,
  TypeGeneration,
  lignesExplication,
  lignesFiche,
  lignesResume,
  texteMoteur,
  trier,
} from './studio-ia.modele';

interface Tuile {
  cle: string;
  icone: string;
  titre: string;
  description: string;
  /** Type de génération ouvert par la tuile ; absent = tuile « bientôt ». */
  type?: TypeGeneration;
}

const TUILES: Tuile[] = [
  { cle: 'resume', icone: 'summarize', titre: 'Résumé', description: 'Résumé par un modèle local, sans clé API', type: 'resume' },
  { cle: 'fiche', icone: 'fact_check', titre: 'Fiche de révision', description: 'Résumé, mots-clés et notions, avec leur source dans le cours', type: 'fiche' },
  { cle: 'explication', icone: 'record_voice_over', titre: 'Explication orale', description: 'Le cours réexpliqué à l’oral, en français ou en anglais', type: 'explication' },
  { cle: 'video', icone: 'movie', titre: 'Vidéo', description: 'Une capsule vidéo à partir du cours' },
  { cle: 'flashcards', icone: 'style', titre: 'Flashcards', description: 'Des cartes pour mémoriser les notions' },
  { cle: 'presentation', icone: 'slideshow', titre: 'Présentation', description: 'Un diaporama pour présenter le cours' },
];

const COMBINAISONS: { langue: LangueExplication; mode: ModeExplication }[] = [
  { langue: 'fr', mode: 'monologue' },
  { langue: 'fr', mode: 'dialogue' },
  { langue: 'en', mode: 'monologue' },
  { langue: 'en', mode: 'dialogue' },
];

/**
 * « Studio IA » du formateur (étape 3 de l'éditeur) : tuiles des générations possibles, puis « Mes générations ».
 * Un clic ouvre un volet latéral qui réutilise les composants existants (résumé local, fiche, explication). La liste est construite à partir
 * des GET existants ; elle se rafraîchit à l'ouverture, à la fermeture du volet et, tant qu'un traitement tourne, toutes les 4 s
 * (borné à 5 minutes, arrêté à la fin et à la destruction du composant).
 */
@Component({
  selector: 'app-studio-ia',
  imports: [MatIconModule, MatMenuModule, MatSnackBarModule, Badge],
  template: `
    <section class="studio" aria-labelledby="studio-ia-h">
      <h3 id="studio-ia-h"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Studio IA</h3>
      <p class="muted small">Génère à partir du PDF ou des chapitres du cours. Tu relis chaque résultat avant de le rendre visible aux apprenants.</p>

      <div class="tuiles">
        @for (t of tuiles; track t.cle) {
          <button
            type="button"
            class="tuile"
            [class.inactive]="!t.type"
            [attr.aria-disabled]="!t.type"
            [attr.aria-label]="t.type ? t.titre + ' : ' + t.description + '. Ouvrir les options' : t.titre + ' : bientôt disponible'"
            (click)="ouvrirTuile(t)"
          >
            <span class="icone"><mat-icon aria-hidden="true">{{ t.icone }}</mat-icon></span>
            <span class="texte">
              <strong>{{ t.titre }}</strong>
              <span class="muted small">{{ t.description }}</span>
            </span>
            @if (!t.type) {
              <bw-badge variant="categorie" label="bientôt" />
            } @else {
              <mat-icon class="boussole" aria-hidden="true">explore</mat-icon>
            }
          </button>
        }
      </div>

      <h4 id="generations-h">Mes générations</h4>
      @if (etat() === 'chargement') {
        <p class="muted small" role="status">Chargement…</p>
      } @else if (generations().length === 0) {
        <p class="vide muted">Rien n’a encore été généré pour ce cours. Choisis une tuile ci-dessus pour commencer.</p>
      } @else {
        <ul class="liste" aria-labelledby="generations-h">
          @for (g of generations(); track g.cle) {
            <li class="ligne">
              <mat-icon class="type" aria-hidden="true">{{ icones[g.type] }}</mat-icon>
              <div class="titre">
                <strong>{{ g.titre }}</strong>
                <span class="muted small">{{ g.detail }}</span>
                @if (g.erreur) {
                  <span class="erreur small">{{ g.erreur }}</span>
                }
              </div>
              <span [class]="'pill ' + classeStatut(g)">
                @if (g.statut === 'EN_COURS') { <mat-icon class="tourne" aria-hidden="true">autorenew</mat-icon> }
                {{ libelles[g.statut] }}
              </span>
              <span class="moteur muted small">{{ moteur(g) }}</span>
              <span class="date muted small">{{ date(g) }}</span>
              <button type="button" class="menu" [matMenuTriggerFor]="menu" [attr.aria-label]="'Actions : ' + g.titre + ', ' + g.detail">
                <mat-icon aria-hidden="true">more_vert</mat-icon>
              </button>
              <mat-menu #menu="matMenu">
                <button mat-menu-item (click)="ouvrir(g, false)"><mat-icon aria-hidden="true">open_in_new</mat-icon> Ouvrir</button>
                <button mat-menu-item (click)="ouvrir(g, true)"><mat-icon aria-hidden="true">refresh</mat-icon> Régénérer</button>
                <button mat-menu-item [disabled]="!g.validable" [attr.title]="g.motif ?? null" (click)="valider(g)">
                  <mat-icon aria-hidden="true">check</mat-icon> Valider
                </button>
              </mat-menu>
            </li>
          }
        </ul>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .studio { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--color-gold-500); border-radius: var(--radius-md, var(--radius-sm)); background: var(--color-surface); }
    h3 { display: flex; align-items: center; gap: var(--space-2); margin: 0; font: 600 20px var(--font-heading); }
    h4 { margin: var(--space-3) 0 0; font: 600 14px var(--font-body); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
    p { margin: 0; }
    .boussole { color: var(--color-gold-700); }
    .tuiles { display: grid; gap: var(--space-3); grid-template-columns: 1fr; @media (min-width: 700px) { grid-template-columns: 1fr 1fr; } }
    .tuile { display: flex; align-items: flex-start; gap: var(--space-3); min-height: 88px; padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); text-align: left; font: inherit; cursor: pointer; }
    .tuile:hover:not(.inactive) { border-color: var(--color-gold-500); background: var(--color-gold-100); }
    .tuile:focus-visible, .menu:focus-visible { outline: 3px solid var(--color-gold-500); outline-offset: 2px; }
    .tuile.inactive { cursor: default; opacity: 0.7; }
    .tuile .icone { display: grid; place-items: center; flex: none; width: 44px; height: 44px; border-radius: var(--radius-sm); background: var(--color-gold-100); color: var(--color-gold-700); }
    .tuile .texte { display: grid; flex: 1; gap: 2px; min-width: 0; }
    .liste { display: grid; margin: 0; padding: 0; list-style: none; border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
    .ligne { display: grid; align-items: center; gap: var(--space-3); grid-template-columns: auto minmax(0, 1fr) auto auto; padding: var(--space-3); border-top: 1px solid var(--color-border); }
    .ligne:first-child { border-top: 0; }
    .ligne .type { color: var(--color-gold-700); }
    .ligne .titre { display: grid; gap: 2px; min-width: 0; }
    .ligne .erreur { color: var(--color-error); font-weight: 600; }
    .ligne .moteur, .ligne .date { grid-column: 2 / 3; }
    .ligne .menu { grid-row: 1; grid-column: 4; display: grid; place-items: center; width: 44px; height: 44px; border: 0; border-radius: 50%; background: transparent; color: var(--color-text); cursor: pointer; }
    @media (min-width: 900px) {
      .ligne { grid-template-columns: auto minmax(0, 1fr) auto 200px 90px 44px; }
      .ligne .moteur, .ligne .date { grid-column: auto; }
      .ligne .menu { grid-row: auto; grid-column: auto; }
    }
    .pill .tourne { font-size: 16px; width: 16px; height: 16px; animation: bw-tourne 2s linear infinite; }
    @keyframes bw-tourne { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .pill .tourne { animation: none; } }
    .vide { padding: var(--space-4); border: 1px dashed var(--color-border); border-radius: var(--radius-sm); }
  `,
})
export class StudioIa implements OnInit {
  private readonly service = inject(CoursService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly destroyRef = inject(DestroyRef);

  readonly coursId = input.required<number>();

  protected readonly tuiles = TUILES;
  protected readonly icones = ICONE_GENERATION;
  protected readonly libelles = LIBELLE_STATUT_GENERATION;
  protected readonly etat = signal<'chargement' | 'ok'>('chargement');
  protected readonly generations = signal<Generation[]>([]);

  private suivi?: Subscription;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => this.arreter());
    this.rafraichir();
  }

  protected ouvrirTuile(t: Tuile): void {
    if (t.type) this.ouvrirPanneau({ type: t.type, coursId: this.coursId() });
  }

  /** « Ouvrir » affiche le résultat dans le volet ; « Régénérer » ouvre le même volet avec les mêmes réglages (le bouton reste à cliquer). */
  protected ouvrir(g: Generation, regenerer: boolean): void {
    this.ouvrirPanneau({ type: g.type, coursId: this.coursId(), langue: g.langue, mode: g.mode, regenerer });
  }

  protected valider(g: Generation): void {
    if (!g.validable) return;
    const id = this.coursId();
    const appel: Observable<unknown> =
      g.type === 'fiche'
        ? this.service.validerFiche(id)
        : g.type === 'resume'
          ? this.service.validerResumeLocal(id)
          : this.service.validerExplication(id, g.langue ?? 'fr', g.mode ?? 'monologue');
    appel.subscribe({
      next: () => {
        this.snack.open(`${g.titre} validé : les apprenants peuvent le consulter.`, 'OK', { duration: 4000 });
        this.rafraichir();
      },
      error: (err) => this.snack.open(errorMessage(err, 'Validation impossible'), 'OK', { duration: 6000 }),
    });
  }

  protected classeStatut(g: Generation): string {
    return { EN_COURS: 'ia', GENERE: 'plain', VALIDE: 'ok', ECHEC: 'ko' }[g.statut];
  }

  protected moteur(g: Generation): string {
    return texteMoteur(g.moteur);
  }

  protected date(g: Generation): string {
    return g.date ? formatDate(g.date) : '';
  }

  private ouvrirPanneau(donnees: DonneesPanneau): void {
    const etroit = typeof window !== 'undefined' && window.innerWidth < 700;
    const ref = this.dialog.open(StudioIaPanneau, {
      data: donnees,
      width: etroit ? '100vw' : '760px',
      maxWidth: '100vw',
      height: '100vh',
      maxHeight: '100vh',
      position: { right: '0', top: '0' },
      ariaLabelledBy: 'studio-ia-titre',
      autoFocus: 'dialog',
      restoreFocus: true,
    });
    ref.afterClosed().subscribe(() => this.rafraichir());
  }

  // ------------------------------------------------------------------ liste « Mes générations »

  private rafraichir(): void {
    this.arreter();
    this.suivi = this.charger().subscribe((liste) => {
      this.generations.set(liste);
      this.etat.set('ok');
      if (liste.some((g) => g.statut === 'EN_COURS')) this.surveiller();
    });
  }

  /** Tant qu'un traitement tourne : relit la liste toutes les 4 s, au plus 5 minutes, et s'arrête dès que plus rien ne tourne. */
  private surveiller(): void {
    this.arreter();
    this.suivi = timer(4000, 4000)
      .pipe(
        switchMap(() => this.charger()),
        tap((liste) => this.generations.set(liste)),
        takeWhile((liste) => liste.some((g) => g.statut === 'EN_COURS'), true),
        take(75),
      )
      .subscribe();
  }

  private arreter(): void {
    this.suivi?.unsubscribe();
    this.suivi = undefined;
  }

  /** Fiche, explications disponibles, résumé local et leurs traitements : uniquement des GET existants. */
  private charger(): Observable<Generation[]> {
    const id = this.coursId();
    const sans = <T>(o: Observable<T>, defaut: T) => o.pipe(catchError(() => of(defaut)));
    return forkJoin({
      fiche: sans(this.service.fiche(id), null),
      traitementFiche: sans(this.service.traitement(id), null),
      disponibles: sans(this.service.explicationsDisponibles(id), []),
      traitementsExplication: forkJoin(COMBINAISONS.map((c) => sans(this.service.traitementExplication(id, c.langue, c.mode), null))),
      resume: sans(this.service.resumeLocal(id), null),
      traitementResume: sans(this.service.traitementResumeLocal(id), null),
    }).pipe(
      switchMap((base) => {
        const dispo = base.disponibles;
        const explications$ = dispo.length
          ? forkJoin(dispo.map((d) => sans(this.service.explication(id, d.langue, d.mode), null)))
          : of([]);
        return explications$.pipe(map((explications) => ({ base, explications, dispo })));
      }),
      map(({ base, explications, dispo }) => {
        const liste: Generation[] = [...lignesFiche(base.fiche, base.traitementFiche), ...lignesResume(base.resume, base.traitementResume)];
        COMBINAISONS.forEach((c, i) => {
          const indice = dispo.findIndex((d) => d.langue === c.langue && d.mode === c.mode);
          const resultat = indice >= 0 ? explications[indice] : null;
          liste.push(...lignesExplication(c.langue, c.mode, resultat, base.traitementsExplication[i]));
        });
        return trier(liste);
      }),
    );
  }
}
