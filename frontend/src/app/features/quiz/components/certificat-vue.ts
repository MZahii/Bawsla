import { DatePipe } from '@angular/common';
import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { Logo } from '../../../shared';
import { Certificat } from '../models/quiz.model';

/**
 * Rendu d'un certificat (page de l'apprenant, vérification publique, impression en PDF).
 * Le nom du titulaire n'est fourni que sur la page de l'apprenant connecté : la vérification publique
 * ne renvoie aucune donnée personnelle. Couleurs du thème uniquement ; la bordure dorée reprend la boussole du logo.
 */
@Component({
  selector: 'app-certificat-vue',
  imports: [DatePipe, MatIconModule, Logo],
  template: `
    <article class="cert" aria-label="Certificat de réussite">
      <header>
        <bw-logo variant="horizontal" [size]="40" />
        <span class="num">N° {{ certificat().numero }}</span>
      </header>
      <p class="kind">Certificat de réussite</p>
      @if (titulaire(); as nom) {
        <p class="intro">Ce certificat atteste que</p>
        <p class="name">{{ nom }}</p>
        <p class="intro">a réussi l’examen final du cours</p>
      } @else {
        <p class="intro">Ce certificat atteste la réussite de l’examen final du cours</p>
      }
      <p class="course">{{ titreCours() }}</p>
      <dl>
        <div><dt>Date</dt><dd>{{ certificat().dateEmission | date: 'dd/MM/yyyy' }}</dd></div>
        <div><dt>Score à l’examen</dt><dd>{{ certificat().score }} %</dd></div>
        <div><dt>Examen</dt><dd>{{ certificat().titreExamen }}</dd></div>
      </dl>
      <footer>
        <mat-icon aria-hidden="true">verified</mat-icon>
        Vérifiable sur la page « Vérifier un certificat » avec le numéro {{ certificat().numero }}
      </footer>
    </article>
  `,
  styles: `
    :host { display: block; }
    .cert { max-width: 860px; margin: 0 auto; padding: var(--space-7) var(--space-6); border: 1px solid var(--color-border); outline: 3px solid var(--color-gold-500); outline-offset: -12px; border-radius: var(--radius-sm); background: var(--color-surface); color: var(--color-text); text-align: center; }
    header { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; }
    .num { font-size: 13px; color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .kind { margin: var(--space-6) 0 var(--space-4); font: 600 14px var(--font-body); letter-spacing: 0.12em; text-transform: uppercase; color: var(--color-gold-700); }
    .intro { margin: 0; color: var(--color-text-muted); }
    .name { margin: var(--space-2) 0 var(--space-3); font: 600 40px/48px var(--font-heading); }
    .course { margin: var(--space-2) 0 var(--space-6); font: 600 24px/32px var(--font-heading); color: var(--color-bordeaux-600); }
    dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: var(--space-3); margin: 0; padding: var(--space-4) 0; border-block: 1px solid var(--color-border); }
    dt { font-size: 13px; color: var(--color-text-muted); }
    dd { margin: 2px 0 0; font-weight: 600; }
    footer { display: flex; align-items: center; justify-content: center; gap: 6px; margin-top: var(--space-4); font-size: 13px; color: var(--color-success); }
    @media (max-width: 599px) { .cert { padding: var(--space-6) var(--space-4); } .name { font-size: 30px; line-height: 38px; } }
  `,
})
export class CertificatVue {
  readonly certificat = input.required<Certificat>();
  readonly titreCours = input('');
  readonly titulaire = input<string | null>(null);
}
