import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { Skeleton } from '../../../shared/ui';
import { DiscussionResume } from '../models';
import { ForumService } from '../services/forum.service';
import { TempsRelatifPipe } from './outils';

/**
 * Aperçu du forum pour les accueils (plan §7.8), exporté par le module pour que les pages communes n'aient qu'une
 * balise à insérer : apprenant, les fils actifs ; formateur, les questions sans réponse de ses cours.
 */
@Component({
  selector: 'app-forum-apercu',
  imports: [RouterLink, MatIconModule, Skeleton, TempsRelatifPipe],
  template: `
    <section class="card">
      <div class="card-head">
        <h2>{{ espace() === 'studio' ? 'Questions sans réponse' : 'Sur le forum' }}</h2>
        <a [routerLink]="base()" class="lnk small">Tout le forum</a>
      </div>
      @for (d of discussions(); track d.id) {
        <a class="fil" [routerLink]="[base(), d.id]">
          <mat-icon aria-hidden="true" [class.ok]="d.statut === 'RESOLUE'">{{ d.statut === 'RESOLUE' ? 'check_circle' : d.nbReponses ? 'forum' : 'help' }}</mat-icon>
          <span class="t"><strong dir="auto">{{ d.titre }}</strong><span class="small muted">{{ d.coursTitre }} · {{ d.nbReponses }} réponse{{ d.nbReponses > 1 ? 's' : '' }} · {{ d.derniereActiviteLe | tempsRelatif }}</span></span>
          @if (d.nonLue) { <span class="point" aria-label="Non lu"></span> }
        </a>
      } @empty {
        @if (chargement()) {
          <div class="card-body"><bw-skeleton /><br /><bw-skeleton width="70%" /></div>
        } @else {
          <p class="card-body small muted">{{ espace() === 'studio' ? 'Aucune question en attente dans tes cours.' : 'Pas encore de discussion : pose la première question !' }}</p>
        }
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .lnk { color: var(--color-bordeaux-600); font-weight: 600; }
    .fil { display: flex; align-items: center; gap: var(--space-3); min-height: 56px; padding: var(--space-3) var(--space-5); border-top: 1px solid var(--color-border); color: var(--color-text); text-decoration: none; }
    .fil:hover, .fil:focus-visible { background: var(--color-bg); }
    .fil mat-icon { flex: none; color: var(--color-text-muted); }
    .fil mat-icon.ok { color: var(--color-success); }
    .t { display: flex; flex-direction: column; flex: 1; min-width: 0; }
    .t strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-weight: 600; }
    .point { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--color-bordeaux-600); }
  `,
})
export class ForumApercu implements OnInit {
  readonly espace = input<'app' | 'studio'>('app');
  readonly nombre = input(3);
  private readonly forum = inject(ForumService);
  protected readonly discussions = signal<DiscussionResume[]>([]);
  protected readonly chargement = signal(true);
  protected readonly base = () => (this.espace() === 'studio' ? '/studio/forum' : '/app/forum');

  ngOnInit(): void {
    const filtres = this.espace() === 'studio' ? { statut: 'SANS_REPONSE' as const, tri: 'recentes' as const } : { tri: 'activite' as const };
    this.forum.lister({ ...filtres, taille: this.nombre() }).subscribe({
      next: (p) => {
        this.discussions.set(p.contenu);
        this.chargement.set(false);
      },
      error: () => this.chargement.set(false),
    });
  }
}
