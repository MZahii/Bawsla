import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, TemplateRef, contentChild, effect, input, signal, untracked } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Observable, Subscription } from 'rxjs';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, AiLoading, BwButton } from '../../../../shared';
import { Skeleton } from '../../../../shared/ui';
import { ContenuIa } from '../../models/ia-generative.model';

const FOURNISSEURS: Record<string, string> = { gemini: 'Gemini', openai: 'OpenAI', groq: 'Groq' };

/**
 * Texte généré par le LLM du module User, à la demande (bouton) pour ménager le quota du fournisseur.
 * Au chargement : le dernier texte gardé, sans appel au LLM. « Générer » / « Régénérer » : nouvel appel, ou le même
 * texte si les données n'ont pas changé. Erreur (LLM indisponible) : message clair, la page reste utilisable.
 *
 * <app-ia-generee titre="Le conseil de ta boussole" [charger]="lireConseil" [generer]="genererConseil" source="…">
 *   <ng-template let-c><p>{{ c.conseil }}</p></ng-template>
 * </app-ia-generee>
 */
@Component({
  selector: 'app-ia-generee',
  imports: [DatePipe, NgTemplateOutlet, MatIconModule, AiBox, AiLoading, BwButton, Skeleton],
  template: `
    @if (etat() === 'chargement') {
      <bw-skeleton height="96px" />
    } @else if (etat() === 'generation') {
      <bw-ai-loading [message]="attente()" (cancel)="annuler()" />
    } @else if (contenu(); as c) {
      <bw-ai-box [heading]="titre()" [regenerable]="true" (regenerate)="lancer()">
        <ng-container *ngTemplateOutlet="corps() ?? null; context: { $implicit: c.contenu }" />
        <span bwAiSource>
          {{ source() }} Texte rédigé par {{ fournisseur(c.fournisseur) }} le {{ c.dateGeneration | date: 'dd/MM/yyyy à HH:mm' }}.
        </span>
      </bw-ai-box>
    } @else {
      <section class="invite">
        <mat-icon aria-hidden="true">auto_awesome</mat-icon>
        <div class="txt"><strong>{{ titre() }}</strong><span class="muted small">{{ invitation() }}</span></div>
        <button type="button" bwButton="ia" (click)="lancer()">{{ libelleBouton() }}</button>
      </section>
    }
    @if (info()) { <p class="note small muted" role="status">{{ info() }}</p> }
    @if (erreur()) { <p class="note small erreur" role="alert">{{ erreur() }}</p> }
  `,
  styles: `
    :host { display: block; }
    .invite {
      display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap;
      padding: var(--space-4) var(--space-5); border: 1px dashed var(--color-gold-500);
      border-radius: var(--radius-sm); background: var(--color-surface);
    }
    .invite mat-icon { color: var(--color-gold-700); }
    .txt { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 200px; }
    .note { margin: var(--space-2) 0 0; }
    .erreur { color: var(--color-error); }
  `,
})
export class IaGeneree {
  readonly titre = input.required<string>();
  readonly invitation = input('');
  readonly libelleBouton = input('Générer');
  readonly attente = input('La boussole rédige…');
  /** Origine des données, affichée sous le texte (« À partir de tes résultats… »). */
  readonly source = input('');
  readonly charger = input.required<() => Observable<ContenuIa<unknown> | null>>();
  readonly generer = input.required<() => Observable<ContenuIa<unknown>>>();
  /** Change de valeur → recharge (par exemple l'apprenant affiché dans la fiche). */
  readonly cle = input<unknown>(null);

  protected readonly corps = contentChild(TemplateRef);
  protected readonly contenu = signal<ContenuIa<unknown> | null>(null);
  protected readonly etat = signal<'chargement' | 'pret' | 'generation'>('chargement');
  protected readonly erreur = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  private appel?: Subscription;

  constructor() {
    effect(() => {
      this.cle();
      const charger = this.charger();
      untracked(() => this.recharger(charger));
    });
  }

  private recharger(charger: () => Observable<ContenuIa<unknown> | null>): void {
    this.appel?.unsubscribe();
    this.etat.set('chargement');
    this.contenu.set(null);
    this.erreur.set(null);
    this.info.set(null);
    this.appel = charger().subscribe({
      next: (c) => {
        this.contenu.set(c);
        this.etat.set('pret');
      },
      error: () => this.etat.set('pret'), // pas de texte gardé lisible : on propose simplement de générer
    });
  }

  protected lancer(): void {
    this.appel?.unsubscribe();
    this.erreur.set(null);
    this.info.set(null);
    this.etat.set('generation');
    this.appel = this.generer()().subscribe({
      next: (c) => {
        this.contenu.set(c);
        this.etat.set('pret');
        if (c.depuisCache) this.info.set('Tes données n’ont pas changé depuis : c’est le même texte, sans nouvel appel à l’IA.');
      },
      error: (err) => {
        this.etat.set('pret');
        this.erreur.set(errorMessage(err, 'L’IA générative est indisponible pour le moment.'));
      },
    });
  }

  protected annuler(): void {
    this.appel?.unsubscribe();
    this.etat.set('pret');
  }

  protected fournisseur(code: string | undefined): string {
    return (code && FOURNISSEURS[code]) || 'l’IA';
  }
}
