import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { BwButton } from '../../shared/directives/bw-button';

@Component({
  selector: 'app-forbidden',
  imports: [EmptyState, BwButton, RouterLink],
  template: `
    <bw-empty-state icon="block" title="Accès refusé" message="Ton rôle ne permet pas d'accéder à cette page.">
      <a bwButton="secondaire" routerLink="/">Retour à l'accueil</a>
    </bw-empty-state>
  `,
})
export class Forbidden {}
