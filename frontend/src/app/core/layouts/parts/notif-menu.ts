import { Component, DestroyRef, inject, signal } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Router } from '@angular/router';

import { NOTIFICATIONS, tempsRelatif } from '../../../features/forum/components/outils';
import { NotificationForum } from '../../../features/forum/models';
import { ForumService } from '../../../features/forum/services/forum.service';

/** Cloche des notifications avec compteur et panneau : notifications du forum, compteur relu toutes les 30 s. */
@Component({
  selector: 'app-notif-menu',
  imports: [MatMenuModule, MatIconModule, MatBadgeModule],
  template: `
    <button type="button" class="bell" [matMenuTriggerFor]="m" (menuOpened)="charger()" [attr.aria-label]="'Notifications, ' + nonLues() + ' non lues'">
      <mat-icon [matBadge]="nonLues() || null" matBadgeSize="small" matBadgeColor="warn" aria-hidden="false">notifications</mat-icon>
    </button>
    <mat-menu #m="matMenu" xPosition="before" class="bw-notif">
      <div class="panel" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
        <div class="ph">
          <strong>Notifications</strong>
          <button type="button" (click)="toutLire()">Tout marquer comme lu</button>
        </div>
        @for (n of items(); track n.id) {
          <button type="button" class="it" [class.unread]="!n.lue" (click)="ouvrir(n)">
            <span class="ic"><mat-icon>{{ libelles[n.type].icone }}</mat-icon></span>
            <span class="tx">{{ (n.acteur?.nom ? n.acteur!.nom + ' ' : '') + libelles[n.type].texte }}<small>{{ n.discussionTitre ?? n.extrait ?? '' }} · {{ quand(n.creeLe) }}</small></span>
          </button>
        } @empty {
          <p class="it"><span class="tx"><small>Aucune notification pour l’instant.</small></span></p>
        }
      </div>
    </mat-menu>
  `,
  styles: `
    .bell { display: grid; place-items: center; width: 40px; height: 40px; border: 0; border-radius: 50%; background: none; color: inherit; cursor: pointer; }
    .bell:hover { background: var(--hover, var(--color-neutral-soft)); }
    button.it { width: 100%; border: 0; background: none; color: inherit; font: inherit; text-align: left; cursor: pointer; }
  `,
})
export class NotifMenu {
  private readonly forum = inject(ForumService);
  private readonly router = inject(Router);
  protected readonly libelles = NOTIFICATIONS;
  protected readonly items = signal<NotificationForum[]>([]);
  protected readonly nonLues = signal(0);

  constructor() {
    this.compter();
    const minuterie = setInterval(() => this.compter(), 30000);
    inject(DestroyRef).onDestroy(() => clearInterval(minuterie));
  }

  protected charger(): void {
    this.forum.notifications(false, 0).subscribe({ next: (p) => this.items.set(p.contenu.slice(0, 8)), error: () => this.items.set([]) });
  }

  protected toutLire(): void {
    this.forum.marquerLues().subscribe({
      next: (c) => {
        this.nonLues.set(c.nonLues);
        this.items.update((l) => l.map((n) => ({ ...n, lue: true })));
      },
    });
  }

  protected ouvrir(n: NotificationForum): void {
    if (!n.lue) {
      this.forum.marquerLues([n.id]).subscribe({ next: (c) => this.nonLues.set(c.nonLues) });
    }
    const espace = this.router.url.startsWith('/studio') ? '/studio/forum' : this.router.url.startsWith('/admin') ? '/admin/forum' : '/app/forum';
    this.router.navigate(n.discussionId ? [espace, n.discussionId] : [espace], { fragment: n.reponseId ? `r-${n.reponseId}` : undefined });
  }

  protected quand(iso: string): string {
    return tempsRelatif(iso);
  }

  private compter(): void {
    this.forum.compteur().subscribe({ next: (c) => this.nonLues.set(c.nonLues), error: () => this.nonLues.set(0) });
  }
}
