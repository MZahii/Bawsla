import { NgTemplateOutlet } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';

import { EmptyState } from '../../../../shared';
import { Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { DiscussionLigne } from '../../components/discussion-ligne';
import { TempsRelatifPipe, messageErreur } from '../../components/outils';
import { Activite, RaisonReputation } from '../../models';
import { ForumService } from '../../services/forum.service';

const RAISONS: Record<RaisonReputation, string> = {
  QUESTION_VOTEE: 'Ta question a reçu un vote',
  REPONSE_VOTEE: 'Ta réponse a reçu un vote',
  REPONSE_ACCEPTEE: 'Ta réponse a été choisie comme meilleure réponse',
  ACCEPTATION: 'Tu as choisi une meilleure réponse',
  APPROBATION_FORMATEUR: 'Ta réponse a été approuvée par le formateur',
  VIGIE_UTILE: 'Tu as jugé utile la réponse de La Vigie',
  VOTE_NEGATIF_RECU: 'Vote négatif reçu',
  VOTE_NEGATIF_DONNE: 'Vote négatif donné',
  MESSAGE_MASQUE: 'Message masqué par la modération',
};

/** « Mon activité » (plan §7.5) : questions, réponses, favoris, suivis, historique des points, badges et progression. */
@Component({
  selector: 'app-forum-activite',
  imports: [NgTemplateOutlet, MatIconModule, MatTabsModule, EmptyState, Kpi, PageHeader, Skeleton, DiscussionLigne, TempsRelatifPipe],
  template: `
    <bw-page-header title="Mon activité" subtitle="Tes contributions au forum, tes points et tes badges." [crumbs]="[{ label: 'Forum', link: '/app/forum' }, { label: 'Mon activité' }]" />
    @if (erreur(); as msg) {
      <bw-empty-state icon="error" title="Activité indisponible" [message]="msg" />
    } @else if (a(); as a) {
      <div class="grid-4">
        <bw-kpi label="Niveau" [value]="a.reputation.niveauLibelle" icon="explore" />
        <bw-kpi label="Points" [value]="a.reputation.points" icon="stars" />
        <bw-kpi label="Questions" [value]="a.questions.length" icon="help" />
        <bw-kpi label="Badges obtenus" [value]="badgesObtenus() + ' / ' + a.reputation.badges.length" icon="military_tech" />
      </div>
      @if (a.reputation.seuilProchainNiveau) {
        <section class="card card-pad mt">
          <p class="small">Encore <strong>{{ a.reputation.seuilProchainNiveau - a.reputation.points }} points</strong> pour le niveau suivant.</p>
          <div class="barre" role="progressbar" [attr.aria-valuenow]="a.reputation.points" [attr.aria-valuemax]="a.reputation.seuilProchainNiveau" aria-label="Progression vers le niveau suivant">
            <span [style.width.%]="(a.reputation.points / a.reputation.seuilProchainNiveau) * 100"></span>
          </div>
        </section>
      }
      <section class="card mt">
        <mat-tab-group animationDuration="0ms" mat-stretch-tabs="false">
          <mat-tab [label]="'Questions (' + a.questions.length + ')'"><ng-container *ngTemplateOutlet="liste; context: { $implicit: a.questions }" /></mat-tab>
          <mat-tab [label]="'Réponses (' + a.reponses.length + ')'"><ng-container *ngTemplateOutlet="liste; context: { $implicit: a.reponses }" /></mat-tab>
          <mat-tab [label]="'Favoris (' + a.favoris.length + ')'"><ng-container *ngTemplateOutlet="liste; context: { $implicit: a.favoris }" /></mat-tab>
          <mat-tab [label]="'Suivis (' + a.suivis.length + ')'"><ng-container *ngTemplateOutlet="liste; context: { $implicit: a.suivis }" /></mat-tab>
          <mat-tab label="Points">
            <ul class="points">
              @for (e of a.reputation.historique; track $index) {
                <li><span class="delta num" [class.neg]="e.delta < 0">{{ e.delta > 0 ? '+' : '' }}{{ e.delta }}</span><span class="grow">{{ raisons[e.raison] }}</span><span class="small muted">{{ e.creeLe | tempsRelatif }}</span></li>
              } @empty { <li class="muted">Pas encore de points : réponds à une question pour commencer.</li> }
            </ul>
          </mat-tab>
          <mat-tab label="Badges">
            <div class="badges">
              @for (b of a.reputation.badges; track b.code) {
                <article class="badge" [class.obtenu]="b.obtenu">
                  <mat-icon aria-hidden="true">{{ b.obtenu ? 'military_tech' : 'lock' }}</mat-icon>
                  <div><strong>{{ b.libelle }}</strong><p class="small muted">{{ b.description }}</p>
                    @if (b.obtenu) { <span class="pill ok">Obtenu {{ b.obtenuLe | tempsRelatif }}</span> }
                    @else { <div class="barre" role="progressbar" [attr.aria-valuenow]="b.progression" [attr.aria-valuemax]="b.objectif" [attr.aria-label]="'Progression ' + b.libelle"><span [style.width.%]="(b.progression / b.objectif) * 100"></span></div><span class="small muted">{{ b.progression }} / {{ b.objectif }}</span> }
                  </div>
                </article>
              }
            </div>
          </mat-tab>
        </mat-tab-group>
      </section>
      <ng-template #liste let-items>
        @for (d of $any(items); track d.id) { <app-discussion-ligne [discussion]="d" /> } @empty { <p class="card-body muted">Rien ici pour l’instant.</p> }
      </ng-template>
    } @else {
      <div class="card card-pad"><bw-skeleton height="80px" /></div>
    }
  `,
  styles: `
    .barre { height: 6px; margin: var(--space-2) 0; border-radius: 3px; background: var(--color-border); overflow: hidden; }
    .barre span { display: block; height: 100%; background: var(--color-bordeaux-600); }
    .points { list-style: none; margin: 0; padding: var(--space-3) var(--space-5); }
    .points li { display: flex; align-items: center; gap: var(--space-3); min-height: 44px; border-bottom: 1px solid var(--color-border); }
    .delta { width: 48px; font-weight: 700; color: var(--color-success); }
    .delta.neg { color: var(--color-error); }
    .grow { flex: 1; }
    .badges { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: var(--space-3); padding: var(--space-4) var(--space-5); }
    .badge { display: flex; gap: var(--space-3); padding: var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); }
    .badge mat-icon { flex: none; color: var(--color-text-muted); }
    .badge.obtenu { border-color: var(--color-bordeaux-600); }
    .badge.obtenu mat-icon { color: var(--color-bordeaux-600); }
    .badge p { margin: 2px 0 var(--space-2); }
  `,
})
export class ForumActivite implements OnInit {
  private readonly forum = inject(ForumService);
  protected readonly raisons = RAISONS;
  protected readonly a = signal<Activite | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly badgesObtenus = computed(() => this.a()?.reputation.badges.filter((b) => b.obtenu).length ?? 0);

  ngOnInit(): void {
    this.forum.activite().subscribe({ next: (a) => this.a.set(a), error: (e) => this.erreur.set(messageErreur(e)) });
  }
}
