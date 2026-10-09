import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { BwButton } from '../../directives/bw-button';

/**
 * Encart "Généré par l'IA" (DESIGN.md, section 7). Tout contenu produit par l'IA passe par lui.
 *
 * <bw-ai-box [editable]="true" [regenerable]="true" [validatable]="true"
 *            (edit)="..." (regenerate)="..." (validate)="...">
 *   <p>Résumé généré…</p>
 *   <span bwAiSource>Source : <a routerLink="/cours/3">Bases de données</a>, chapitre 2</span>
 * </bw-ai-box>
 */
@Component({
  selector: 'bw-ai-box',
  imports: [MatIconModule, BwButton],
  template: `
    <section class="box" [attr.aria-label]="label()">
      <header class="head">
        <mat-icon aria-hidden="true">explore</mat-icon>
        <span class="label">{{ label() }}</span>
        @if (heading()) {
          <span class="heading">· {{ heading() }}</span>
        }
      </header>
      <div class="content"><ng-content /></div>
      <div class="source"><ng-content select="[bwAiSource]" /></div>
      @if (editable() || regenerable() || validatable()) {
        <div class="actions">
          @if (editable()) {
            <button type="button" bwButton="fantome" (click)="edit.emit()">
              <mat-icon aria-hidden="true">edit</mat-icon> Modifier
            </button>
          }
          @if (regenerable()) {
            <button type="button" bwButton="secondaire" [bwLoading]="busy()" [disabled]="busy()" (click)="regenerate.emit()">
              <mat-icon aria-hidden="true">refresh</mat-icon> Régénérer
            </button>
          }
          @if (validatable()) {
            <button type="button" bwButton="principal" [disabled]="busy()" (click)="validate.emit()">
              <mat-icon aria-hidden="true">check</mat-icon> Valider
            </button>
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .box {
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-left: 3px solid var(--color-gold-500);
      border-radius: var(--radius-sm);
      padding: var(--space-4) var(--space-5);
      color: var(--color-text);
    }
    .head {
      display: flex;
      align-items: center;
      gap: var(--space-2);
      margin-bottom: var(--space-3);
      color: var(--color-gold-700);
      font-size: 12px;
      line-height: 16px;
      font-weight: 500;
    }
    .head mat-icon { font-size: 20px; width: 20px; height: 20px; }
    .label { font-weight: 600; }
    .heading { color: var(--color-text-muted); }
    .source {
      margin-top: var(--space-3);
      font-size: 14px;
      line-height: 20px;
      color: var(--color-text-muted);
    }
    .source:empty { display: none; }
    .actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--space-2);
      margin-top: var(--space-4);
    }
  `,
})
export class AiBox {
  /** Libellé obligatoire de transparence. */
  readonly label = input("Généré par l'IA");
  /** Titre optionnel ("Résumé", "Remédiation", "Ta boussole te recommande"…). */
  readonly heading = input<string>();
  readonly editable = input(false);
  readonly regenerable = input(false);
  readonly validatable = input(false);
  /** Génération en cours : désactive Régénérer / Valider. */
  readonly busy = input(false);

  readonly edit = output<void>();
  readonly regenerate = output<void>();
  readonly validate = output<void>();
}
