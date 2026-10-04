import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';

import { ROLE_LABELS } from '../../shared/models/user.model';
import { AuthService } from '../auth/auth.service';
import { MENU } from './menu';

@Component({
  selector: 'app-layout',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive,
    MatSidenavModule, MatToolbarModule, MatListModule, MatIconModule, MatButtonModule, MatTooltipModule,
  ],
  templateUrl: './layout.html',
  styleUrl: './layout.scss',
})
export class Layout {
  protected readonly auth = inject(AuthService);

  protected readonly isHandset = toSignal(
    inject(BreakpointObserver).observe('(max-width: 768px)').pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  protected readonly menu = computed(() => {
    const role = this.auth.role();
    return MENU.filter((item) => !item.roles || (role !== null && item.roles.includes(role)));
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '';
  });
}
