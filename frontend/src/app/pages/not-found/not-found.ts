import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { EmptyState } from '../../shared/components/empty-state/empty-state';

@Component({
  selector: 'app-not-found',
  imports: [EmptyState, MatButtonModule, RouterLink],
  template: `
    <app-empty-state icon="explore_off" title="Page introuvable">
      <a mat-stroked-button routerLink="/">Retour à l'accueil</a>
    </app-empty-state>
  `,
})
export class NotFound {}
