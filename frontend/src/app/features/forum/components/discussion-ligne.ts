import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { Avatar } from '../../../shared/ui';
import { DiscussionResume } from '../models';
import { TempsRelatifPipe } from './outils';

/**
 * Carte d'un fil dans une liste : votes, réponses (vert et coche si résolue, orange si sans réponse), vues, titre,
 * extrait sur 2 lignes, cours, étiquettes, pastilles « Vérifiée par le formateur » et « La Vigie », non lu.
 */
@Component({
  selector: 'app-discussion-ligne',
  imports: [RouterLink, MatIconModule, Avatar, TempsRelatifPipe],
  template: `
    @let d = discussion();
    <article class="ligne" [class.epinglee]="d.epinglee" [class.compacte]="compacte()">
      @if (!compacte()) {
        <div class="stats">
          <span><strong class="num">{{ d.score }}</strong> votes</span>
          <span class="rep" [class.resolue]="d.statut === 'RESOLUE'" [class.zero]="d.nbReponses === 0 && d.statut !== 'RESOLUE'">
            @if (d.statut === 'RESOLUE') { <mat-icon aria-hidden="true">check</mat-icon> }
            <strong class="num">{{ d.nbReponses }}</strong> {{ d.nbReponses > 1 ? 'réponses' : 'réponse' }}
          </span>
          <span class="muted">{{ d.nbVues }} vues</span>
        </div>
      }
      <div class="contenu">
        <div class="haut">
          @if (d.nonLue) { <span class="point" aria-label="Non lu"></span> }
          @if (d.epinglee) { <mat-icon class="pin" aria-label="Épinglée">push_pin</mat-icon> }
          @if (d.type === 'ANNONCE') { <span class="pill accent plain">Annonce</span> }
          @if (d.faq) { <span class="pill plain">FAQ</span> }
          <a class="titre" dir="auto" [routerLink]="[base(), d.id]">{{ d.titre }}</a>
        </div>
        @if (!compacte()) { <p class="extrait muted" dir="auto">{{ d.extrait }}</p> }
        <div class="meta">
          @if (d.statut === 'RESOLUE' && compacte()) { <span class="pill ok">Résolue</span> }
          @if (d.approuvee) { <span class="pill accent"><mat-icon aria-hidden="true">verified</mat-icon> Vérifiée par le formateur</span> }
          @if (d.vigie) { <span class="pill ia"><mat-icon aria-hidden="true">explore</mat-icon> La Vigie</span> }
          @for (e of d.etiquettes; track e) {
            <button type="button" class="tag" (click)="etiquette.emit(e)" [attr.aria-label]="'Filtrer par ' + e">{{ e }}</button>
          }
          <span class="grow"></span>
          <bw-avatar [nom]="d.auteur.nom ?? 'Anonyme'" [size]="22" />
          <span class="small">
            <strong>{{ d.auteur.nom ?? 'Anonyme' }}</strong>
            <span class="muted"> · {{ d.coursTitre }} · {{ d.derniereActiviteLe | tempsRelatif }}</span>
          </span>
        </div>
      </div>
    </article>
  `,
  styles: `
    .ligne { display: flex; gap: var(--space-5); padding: var(--space-4) var(--space-5); border-top: 1px solid var(--color-border); }
    .ligne.epinglee { background: var(--color-bg); }
    .ligne.compacte { padding: var(--space-3) var(--space-4); }
    .stats { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; flex: none; width: 104px; font-size: 13px; }
    .rep { display: inline-flex; align-items: center; gap: 3px; padding: 2px 6px; border: 1px solid transparent; border-radius: 4px; }
    .rep.resolue { border-color: var(--color-success); color: var(--color-success); }
    .rep.zero { color: var(--color-warning); }
    .rep mat-icon { font-size: 16px; width: 16px; height: 16px; }
    .contenu { flex: 1; min-width: 0; }
    .haut { display: flex; align-items: center; gap: var(--space-2); }
    .point { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--color-bordeaux-600); }
    .pin { flex: none; font-size: 18px; width: 18px; height: 18px; color: var(--color-bordeaux-600); }
    .titre { font: 600 17px/24px var(--font-heading); color: var(--color-text); text-decoration: none; }
    .titre:hover, .titre:focus-visible { text-decoration: underline; }
    .compacte .titre { font-size: 15px; line-height: 22px; }
    .extrait { margin: 4px 0 var(--space-3); font-size: 15px; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
    .meta { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); margin-top: var(--space-1); }
    .meta .pill mat-icon { font-size: 14px; width: 14px; height: 14px; }
    .grow { flex: 1; }
    .tag { min-height: 28px; padding: 2px 8px; border: 0; border-radius: 4px; background: var(--color-navy-100); color: var(--color-navy-700); font: 600 13px var(--font-body); cursor: pointer; }
    :host-context([data-theme='dark']) .tag { background: var(--color-white-8); color: var(--color-white-80); }
    @media (max-width: 640px) { .ligne { flex-direction: column; gap: var(--space-2); } .stats { flex-direction: row; width: auto; } }
  `,
})
export class DiscussionLigne {
  readonly discussion = input.required<DiscussionResume>();
  readonly base = input('/app/forum');
  readonly compacte = input(false);
  readonly etiquette = output<string>();
}
