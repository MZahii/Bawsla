import { Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { Fiche } from '../../models/cours.model';
import { annonce, borner, construireCartes, sensGlissement } from './fiche-cartes.modele';

/**
 * « Vue fiches » de la fiche de révision : des cartes navigables, en lecture seule. Carte 1 : résumé et mots-clés ; puis une carte par
 * notion (recto : son nom ; verso : définition et source). Mêmes données que la vue liste, aucun appel HTTP.
 *
 * - Navigation : flèches ‹ ›, touches ← → (Début et Fin aussi), glissement tactile, points cliquables. Jamais de boucle.
 * - Retournement : bouton « Retourner la carte », ou Espace / Entrée quand la zone a le focus. Sans animation si l'utilisateur
 *   demande moins de mouvement (prefers-reduced-motion).
 * - À placer dans un <bw-ai-box> (encart « Généré par l'IA »). `gestionnaire` : signale au formateur seul une notion suspecte.
 */
@Component({
  selector: 'app-fiche-cartes',
  imports: [MatIconModule],
  template: `
    <section class="cartes" role="region" aria-roledescription="carrousel" aria-label="Fiche de révision en cartes">
      <div
        class="scene"
        role="group"
        tabindex="0"
        [attr.aria-label]="'Carte ' + (index() + 1) + ' sur ' + total() + '. Flèches gauche et droite pour naviguer, Espace pour retourner.'"
        (keydown)="touche($event)"
        (pointerdown)="debut($event)"
        (pointerup)="fin($event)"
        (pointercancel)="annuler()"
      >
        @if (carte(); as c) {
          <div class="carte" [class.retournee]="retournee()">
            @if (c.type === 'resume') {
              <div class="face">
                <p class="etiquette"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Résumé du cours</p>
                <p class="resume">{{ c.resume }}</p>
                @if (c.motsCles.length) {
                  <ul class="mots" aria-label="Mots-clés">
                    @for (m of c.motsCles; track m) {
                      <li class="pill ia plain">{{ m }}</li>
                    }
                  </ul>
                }
              </div>
            } @else {
              <div class="face recto" [attr.aria-hidden]="retournee()" [attr.inert]="retournee() ? '' : null">
                <p class="etiquette"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Notion {{ c.rang }}</p>
                <p class="nom">{{ c.titre }}</p>
                <p class="indice muted small">Retourne la carte pour voir la définition.</p>
              </div>
              <div class="face verso" [attr.aria-hidden]="!retournee()" [attr.inert]="!retournee() ? '' : null">
                <p class="etiquette"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> {{ c.titre }}</p>
                <h4>Définition</h4>
                <p class="definition">{{ c.definition }}</p>
                @if (c.extrait) {
                  <blockquote><span class="lbl">Source dans le cours</span> « {{ c.extrait }} »</blockquote>
                }
                @if (c.suspecte) {
                  <p class="alerte"><mat-icon aria-hidden="true">warning</mat-icon> Extrait introuvable dans le texte du cours : vérifie cette notion avant de valider.</p>
                }
              </div>
            }
          </div>
        }
      </div>

      <div class="barre">
        <button type="button" class="fleche" aria-label="Carte précédente" [disabled]="index() === 0" (click)="aller(index() - 1)">
          <mat-icon aria-hidden="true">chevron_left</mat-icon>
        </button>
        <span class="compteur num" aria-hidden="true">{{ index() + 1 }} / {{ total() }}</span>
        <button type="button" class="fleche" aria-label="Carte suivante" [disabled]="index() === total() - 1" (click)="aller(index() + 1)">
          <mat-icon aria-hidden="true">chevron_right</mat-icon>
        </button>
      </div>

      <div class="points" role="group" aria-label="Aller à une carte">
        @for (c of cartes(); track c.cle; let i = $index) {
          <button
            type="button"
            class="point"
            [class.actif]="i === index()"
            [attr.aria-label]="'Aller à la carte ' + (i + 1) + ' : ' + c.titre"
            [attr.aria-current]="i === index() ? 'true' : null"
            (click)="aller(i)"
          ><span></span></button>
        }
      </div>

      <div class="actions">
        <button type="button" class="retourner" [disabled]="!retournable()" [attr.aria-pressed]="retournee()" (click)="retourner()">
          <mat-icon aria-hidden="true">flip</mat-icon> Retourner la carte
        </button>
      </div>

      <p class="sr-only" aria-live="polite" aria-atomic="true">{{ annonceCourante() }}</p>
    </section>
  `,
  styles: `
    :host { display: block; }
    .cartes { display: grid; gap: var(--space-3); justify-items: center; }
    .scene { width: 100%; max-width: 560px; perspective: 1200px; touch-action: pan-y; border-radius: 12px; }
    .scene:focus-visible, button:focus-visible { outline: 3px solid var(--color-gold-500); outline-offset: 3px; }
    .carte { display: grid; transition: transform 0.5s; transform-style: preserve-3d; }
    .carte.retournee { transform: rotateY(180deg); }
    .face { grid-area: 1 / 1; display: grid; align-content: start; gap: var(--space-3); min-height: 260px; padding: var(--space-5); border: 1px solid var(--color-border); border-top: 4px solid var(--color-gold-500); border-radius: 12px; background: var(--color-surface); color: var(--color-text); box-shadow: var(--shadow-md); backface-visibility: hidden; -webkit-backface-visibility: hidden; box-sizing: border-box; }
    .verso { transform: rotateY(180deg); background: var(--color-gold-100); }
    .recto { align-content: center; text-align: center; justify-items: center; }
    .etiquette { display: flex; align-items: center; gap: var(--space-2); margin: 0; font: 600 13px var(--font-body); text-transform: uppercase; letter-spacing: 0.04em; color: var(--color-text-muted); }
    .boussole { color: var(--color-gold-700); font-size: 20px; width: 20px; height: 20px; }
    .nom { margin: 0; font: 600 26px/34px var(--font-heading); }
    .indice { margin: 0; }
    .resume, .definition { margin: 0; line-height: 24px; white-space: pre-line; }
    h4 { margin: 0; font: 600 14px var(--font-body); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
    .mots { display: flex; flex-wrap: wrap; gap: var(--space-2); margin: 0; padding: 0; list-style: none; }
    blockquote { margin: 0; padding-left: var(--space-3); border-left: 3px solid var(--color-gold-500); color: var(--color-text-muted); font-size: 14px; font-style: italic; }
    blockquote .lbl { display: block; font-style: normal; font-size: 12px; font-weight: 600; }
    .alerte { display: flex; align-items: flex-start; gap: var(--space-2); margin: 0; color: var(--color-warning); font-size: 14px; font-weight: 600; mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; } }
    .barre { display: flex; align-items: center; justify-content: center; gap: var(--space-4); }
    .compteur { min-width: 64px; text-align: center; font-weight: 700; }
    .fleche { display: grid; place-items: center; width: 44px; height: 44px; border: 1px solid var(--color-border); border-radius: 50%; background: var(--color-surface); color: var(--color-text); cursor: pointer; }
    .fleche:disabled { opacity: 0.4; cursor: default; }
    .points { display: flex; flex-wrap: wrap; justify-content: center; max-width: 560px; }
    .point { display: grid; place-items: center; width: 44px; height: 44px; padding: 0; border: 0; background: transparent; cursor: pointer; }
    .point span { width: 12px; height: 12px; border: 2px solid var(--color-gold-700); border-radius: 50%; background: transparent; }
    .point.actif span { background: var(--color-gold-700); }
    .retourner { display: inline-flex; align-items: center; gap: var(--space-2); min-height: 44px; padding: 0 var(--space-4); border: 1px solid var(--color-gold-500); border-radius: 999px; background: var(--color-gold-100); color: var(--color-gold-700); font: 600 14px var(--font-body); cursor: pointer; }
    .retourner:disabled { opacity: 0.5; cursor: default; }
    .sr-only { position: absolute; width: 1px; height: 1px; margin: -1px; padding: 0; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
    @media (prefers-reduced-motion: reduce) { .carte { transition: none; } }
  `,
})
export class FicheCartes {
  readonly fiche = input.required<Fiche>();
  /** Formateur ou ADMIN : signale une notion dont l'extrait est introuvable (jamais montré à l'apprenant). */
  readonly gestionnaire = input(false);

  protected readonly cartes = computed(() => construireCartes(this.fiche(), this.gestionnaire()));
  protected readonly total = computed(() => this.cartes().length);
  protected readonly index = signal(0);
  protected readonly retournee = signal(false);
  protected readonly carte = computed(() => this.cartes()[borner(this.index(), this.total())]);
  /** Seules les cartes de notion ont un verso. */
  protected readonly retournable = computed(() => this.carte()?.type === 'notion');
  protected readonly annonceCourante = computed(() => annonce(borner(this.index(), this.total()), this.total(), this.carte()?.titre ?? ''));

  private departX = 0;
  private departY = 0;
  private glissement = false;

  protected aller(cible: number): void {
    const suivant = borner(cible, this.total());
    if (suivant === this.index()) return;
    this.index.set(suivant);
    this.retournee.set(false);
  }

  protected retourner(): void {
    if (this.retournable()) this.retournee.update((v) => !v);
  }

  protected touche(evenement: KeyboardEvent): void {
    // Sur un bouton (flèche, point, « Retourner »), Espace et Entrée gardent leur comportement natif : pas de double action.
    if ((evenement.target as HTMLElement).tagName === 'BUTTON') return;
    switch (evenement.key) {
      case 'ArrowLeft': this.aller(this.index() - 1); break;
      case 'ArrowRight': this.aller(this.index() + 1); break;
      case 'Home': this.aller(0); break;
      case 'End': this.aller(this.total() - 1); break;
      case ' ':
      case 'Enter': this.retourner(); break;
      default: return;
    }
    evenement.preventDefault();
  }

  protected debut(e: PointerEvent): void {
    this.glissement = e.pointerType !== 'mouse'; // glissement tactile (ou stylet) ; la souris utilise les flèches
    this.departX = e.clientX;
    this.departY = e.clientY;
  }

  protected fin(e: PointerEvent): void {
    if (!this.glissement) return;
    this.glissement = false;
    const sens = sensGlissement(e.clientX - this.departX, e.clientY - this.departY);
    if (sens !== 0) this.aller(this.index() + sens);
  }

  protected annuler(): void {
    this.glissement = false;
  }
}
