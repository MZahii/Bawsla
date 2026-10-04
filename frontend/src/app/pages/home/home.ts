import { Component, computed, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { MENU } from '../../core/layout/menu';
import { ROLE_LABELS } from '../../shared/models/user.model';

@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatIconModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly auth = inject(AuthService);

  protected readonly modules = computed(() => {
    const role = this.auth.role();
    return MENU.filter((m) => m.route !== '/' && (!m.roles || (role !== null && m.roles.includes(role))));
  });

  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABELS[role] : '';
  });
}
