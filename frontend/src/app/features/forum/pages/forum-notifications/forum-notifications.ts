import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { Router } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { NOTIFICATIONS, TempsRelatifPipe, messageErreur } from '../../components/outils';
import { NotificationForum } from '../../models';
import { ForumService } from '../../services/forum.service';

/** Notifications du forum (plan §7.5) : groupées par jour, filtre « non lues », « Tout marquer comme lu ». */
@Component({
  selector: 'app-forum-notifications',
  imports: [MatIconModule, MatSlideToggleModule, BwButton, EmptyState, PageHeader, Skeleton, TempsRelatifPipe],
  template: `
    <bw-page-header title="Notifications" subtitle="Réponses, mentions, décisions et réponses de La Vigie." [crumbs]="[{ label: 'Forum', link: base }, { label: 'Notifications' }]">
      <button type="button" bwButton="secondaire" (click)="toutLire()"><mat-icon aria-hidden="true">done_all</mat-icon> Tout marquer comme lu</button>
    </bw-page-header>
    <section class="card">
      <div class="card-head"><mat-slide-toggle [checked]="nonLues()" (change)="filtrer($event.checked)">Non lues seulement</mat-slide-toggle></div>
      @if (erreur(); as msg) { <p class="card-body muted">{{ msg }}</p> }
      @for (g of groupes(); track g.jour) {
        <h2 class="jour">{{ g.jour }}</h2>
        @for (n of g.items; track n.id) {
          <button type="button" class="notif" [class.non-lue]="!n.lue" (click)="ouvrir(n)">
            <span class="ic" [class.ia]="n.type === 'VIGIE_PRETE'"><mat-icon aria-hidden="true">{{ libelles[n.type].icone }}</mat-icon></span>
            <span class="tx">
              <span>@if (n.acteur?.nom) {<strong>{{ n.acteur!.nom }}</strong> }{{ libelles[n.type].texte }}@if (n.discussionTitre) { : <em dir="auto">{{ n.discussionTitre }}</em>}</span>
              @if (n.extrait) { <span class="small muted" dir="auto">{{ n.extrait }}</span> }
            </span>
            <span class="small muted quand">{{ n.creeLe | tempsRelatif }}</span>
          </button>
        }
      } @empty {
        @if (chargement()) { <div class="card-body"><bw-skeleton /><br /><bw-skeleton width="70%" /></div> }
        @else { <bw-empty-state icon="notifications" title="Aucune notification" message="Suis une discussion pour être prévenu des nouvelles réponses." /> }
      }
      @if (suivante()) { <div class="plus"><button type="button" bwButton="secondaire" (click)="charger(page() + 1)">Charger plus</button></div> }
    </section>
  `,
  styles: `
    .jour { margin: 0; padding: var(--space-3) var(--space-5) var(--space-2); font: 600 13px var(--font-body); color: var(--color-text-muted); }
    .notif { display: flex; align-items: flex-start; gap: var(--space-3); width: 100%; min-height: 56px; padding: var(--space-3) var(--space-5); border: 0; border-top: 1px solid var(--color-border); background: none; color: var(--color-text); font: 400 15px var(--font-body); text-align: left; cursor: pointer; }
    .notif:hover { background: var(--color-bg); }
    .notif.non-lue { background: var(--color-bg); }
    .notif.non-lue .tx > span:first-child { font-weight: 600; }
    .ic { display: grid; place-items: center; flex: none; width: 36px; height: 36px; border-radius: 50%; background: var(--color-navy-100); color: var(--color-navy-700); }
    .ic.ia { background: var(--color-gold-100); color: var(--color-gold-700); }
    .ic mat-icon { font-size: 18px; width: 18px; height: 18px; }
    .tx { display: flex; flex-direction: column; gap: 2px; flex: 1; min-width: 0; }
    .quand { flex: none; }
    .plus { display: flex; justify-content: center; padding: var(--space-4); border-top: 1px solid var(--color-border); }
  `,
})
export class ForumNotifications implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly router = inject(Router);
  protected readonly base = this.router.url.startsWith('/studio') ? '/studio/forum' : '/app/forum';
  protected readonly libelles = NOTIFICATIONS;
  protected readonly items = signal<NotificationForum[]>([]);
  protected readonly nonLues = signal(false);
  protected readonly page = signal(0);
  protected readonly suivante = signal(false);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);

  protected readonly groupes = computed(() => {
    const groupes = new Map<string, NotificationForum[]>();
    const aujourdhui = new Date().toDateString();
    const hier = new Date(Date.now() - 86400000).toDateString();
    for (const n of this.items()) {
      const d = new Date(n.creeLe);
      const jour = d.toDateString() === aujourdhui ? 'Aujourd’hui' : d.toDateString() === hier ? 'Hier'
        : d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
      groupes.set(jour, [...(groupes.get(jour) ?? []), n]);
    }
    return [...groupes.entries()].map(([jour, items]) => ({ jour, items }));
  });

  ngOnInit(): void {
    this.charger(0);
  }

  protected charger(page: number): void {
    this.chargement.set(true);
    this.forum.notifications(this.nonLues(), page).subscribe({
      next: (p) => {
        this.items.set(page === 0 ? p.contenu : [...this.items(), ...p.contenu]);
        this.page.set(page);
        this.suivante.set(p.suivante);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.chargement.set(false);
      },
    });
  }

  protected filtrer(nonLues: boolean): void {
    this.nonLues.set(nonLues);
    this.charger(0);
  }

  protected toutLire(): void {
    this.forum.marquerLues().subscribe(() => this.items.set(this.items().map((n) => ({ ...n, lue: true }))));
  }

  protected ouvrir(n: NotificationForum): void {
    if (!n.lue) {
      this.forum.marquerLues([n.id]).subscribe();
    }
    if (n.discussionId) {
      this.router.navigate([this.base, n.discussionId], { fragment: n.reponseId ? `r-${n.reponseId}` : undefined });
    } else {
      this.items.set(this.items().map((x) => (x.id === n.id ? { ...x, lue: true } : x)));
    }
  }
}
