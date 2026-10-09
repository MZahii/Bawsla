import { Component, signal } from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { NOTIFICATIONS } from '../../demo/demo-plus';

/** Cloche des notifications avec compteur et panneau. À brancher : flux de notifications du backend. */
@Component({
  selector: 'app-notif-menu',
  imports: [MatMenuModule, MatIconModule, MatBadgeModule],
  template: `
    <button type="button" class="bell" [matMenuTriggerFor]="m" [attr.aria-label]="'Notifications, ' + nonLues() + ' non lues'">
      <mat-icon [matBadge]="nonLues() || null" matBadgeSize="small" matBadgeColor="warn" aria-hidden="false">notifications</mat-icon>
    </button>
    <mat-menu #m="matMenu" xPosition="before" class="bw-notif">
      <div class="panel" (click)="$event.stopPropagation()" (keydown)="$event.stopPropagation()">
        <div class="ph">
          <strong>Notifications</strong>
          <button type="button" (click)="toutLire()">Tout marquer comme lu</button>
        </div>
        @for (n of items(); track n.texte) {
          <div class="it" [class.unread]="!n.lue">
            <span class="ic"><mat-icon>{{ n.icone }}</mat-icon></span>
            <span class="tx">{{ n.texte }}<small>{{ n.quand }}</small></span>
          </div>
        }
      </div>
    </mat-menu>
  `,
  styles: `
    .bell { display: grid; place-items: center; width: 40px; height: 40px; border: 0; border-radius: 50%; background: none; color: inherit; cursor: pointer; }
    .bell:hover { background: var(--hover, var(--color-neutral-soft)); }
  `,
})
export class NotifMenu {
  protected readonly items = signal(NOTIFICATIONS.map((n) => ({ ...n })));
  protected nonLues(): number {
    return this.items().filter((n) => !n.lue).length;
  }
  protected toutLire(): void {
    this.items.update((l) => l.map((n) => ({ ...n, lue: true })));
  }
}
