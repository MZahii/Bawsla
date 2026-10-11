import { Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { Fiche } from '../models/cours.model';

/**
 * Contenu d'une fiche de révision : résumé, mots-clés, notions avec leur extrait source.
 * Pour le formateur (`gestionnaire`), il ajoute le contrôle de fidélité : score, alertes et notions suspectes en orange.
 * À placer dans un <bw-ai-box> (tout contenu généré par l'IA y est signalé).
 */
@Component({
  selector: 'app-fiche-contenu',
  imports: [MatIconModule],
  template: `
    <div class="fiche">
      <section>
        <h4>Résumé</h4>
        <p class="resume">{{ fiche().resume }}</p>
      </section>

      @if (fiche().motsCles.length) {
        <section>
          <h4>Mots-clés</h4>
          <ul class="chips">
            @for (m of fiche().motsCles; track m) {
              <li class="pill ia plain">{{ m }}</li>
            }
          </ul>
        </section>
      }

      @if (fiche().notions.length) {
        <section>
          <h4>Notions à retenir</h4>
          <ul class="notions">
            @for (n of fiche().notions; track n.id) {
              <li [class.suspecte]="gestionnaire() && n.justifiee === false">
                <strong>{{ n.titre }}</strong>
                <p>{{ n.definition }}</p>
                @if (n.extraitSource) {
                  <blockquote><span class="lbl">Source dans le cours</span> « {{ n.extraitSource }} »</blockquote>
                }
                @if (gestionnaire() && n.justifiee === false) {
                  <p class="alerte"><mat-icon aria-hidden="true">warning</mat-icon> Extrait introuvable dans le texte du cours : vérifie cette notion avant de valider.</p>
                }
              </li>
            }
          </ul>
        </section>
      }

      @if (gestionnaire() && fiche().scoreFidelite !== undefined) {
        <section class="controle">
          <h4>Fidélité au cours</h4>
          <div class="score" role="meter" aria-valuemin="0" aria-valuemax="100" [attr.aria-valuenow]="fiche().scoreFidelite" [attr.aria-label]="'Fidélité au cours : ' + fiche().scoreFidelite + ' sur 100'">
            <span class="barre"><span [class]="'rempli ' + niveau()" [style.width.%]="fiche().scoreFidelite"></span></span>
            <span class="valeur num">{{ fiche().scoreFidelite }} / 100</span>
            <span [class]="'pill ' + niveau()">{{ libelle() }}</span>
          </div>
          @if (fiche().alertes?.length) {
            <ul class="alertes">
              @for (a of fiche().alertes; track a) {
                <li><mat-icon aria-hidden="true">warning</mat-icon>{{ a }}</li>
              }
            </ul>
          } @else {
            <p class="muted small">Aucune alerte : les extraits, les mots-clés et les chiffres figurent bien dans le cours.</p>
          }
          <p class="muted small meta">
            Prompt {{ fiche().versionPrompt }} · modèle {{ fiche().modele }}
            @if (fiche().modifieeParFormateur) { · corrigée par le formateur }
          </p>
        </section>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .fiche { display: grid; gap: var(--space-4); }
    h4 { margin: 0 0 var(--space-2); font: 600 14px var(--font-body); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.04em; }
    p { margin: 0; }
    .resume { white-space: pre-line; line-height: 24px; }
    .chips, .notions, .alertes { margin: 0; padding: 0; list-style: none; }
    .chips { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .notions { display: grid; gap: var(--space-3); }
    .notions li { padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .notions li strong { display: block; margin-bottom: 2px; }
    .notions li.suspecte { border-color: var(--color-warning); background: color-mix(in srgb, var(--color-warning) 8%, var(--color-surface)); }
    blockquote { margin: var(--space-2) 0 0; padding-left: var(--space-3); border-left: 3px solid var(--color-gold-500); color: var(--color-text-muted); font-size: 14px; font-style: italic; }
    blockquote .lbl { display: block; font-style: normal; font-size: 12px; font-weight: 600; }
    .alerte, .alertes li { display: flex; align-items: flex-start; gap: var(--space-2); color: var(--color-warning); font-size: 14px; font-weight: 600; }
    .alerte { margin-top: var(--space-2); }
    .alerte mat-icon, .alertes mat-icon { flex: none; font-size: 20px; width: 20px; height: 20px; }
    .alertes { display: grid; gap: var(--space-2); margin-top: var(--space-3); }
    .score { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .barre { flex: 1; min-width: 120px; height: 8px; border-radius: 4px; background: var(--color-neutral-soft); overflow: hidden; }
    .rempli { display: block; height: 100%; }
    .rempli.ok { background: var(--color-success); }
    .rempli.warn { background: var(--color-warning); }
    .rempli.ko { background: var(--color-error); }
    .valeur { font-weight: 700; }
    .meta { margin-top: var(--space-3); }
  `,
})
export class FicheContenu {
  readonly fiche = input.required<Fiche>();
  /** Formateur ou ADMIN : affiche le contrôle de fidélité. */
  readonly gestionnaire = input(false);

  /** ok : 80 et plus ; warn : de 60 à 79 ; ko : moins de 60 (le même seuil que l'alerte « Fidélité faible »). */
  protected readonly niveau = computed<'ok' | 'warn' | 'ko'>(() => {
    const s = this.fiche().scoreFidelite ?? 0;
    return s >= 80 ? 'ok' : s >= 60 ? 'warn' : 'ko';
  });
  protected readonly libelle = computed(() => ({ ok: 'Fidèle', warn: 'À vérifier', ko: 'Fidélité faible' })[this.niveau()]);
}
