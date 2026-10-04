import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

import { EmptyState } from '../../shared/components/empty-state/empty-state';

@Component({
  selector: 'app-forbidden',
  imports: [EmptyState, MatButtonModule, RouterLink],
  template: `
    <app-empty-state icon="block" title="Accès refusé" message="Votre rôle ne permet pas d'accéder à cette page.">
      <a mat-stroked-button routerLink="/">Retour à l'accueil</a>
    </app-empty-state>
  `,
})
export class Forbidden {}
