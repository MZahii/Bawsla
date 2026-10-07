import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { BwButton } from '../../shared/directives/bw-button';

@Component({
  selector: 'app-not-found',
  imports: [EmptyState, BwButton, RouterLink],
  template: `
    <bw-empty-state icon="explore_off" title="Page introuvable" message="La boussole ne trouve pas ce chemin.">
      <a bwButton="secondaire" routerLink="/">Retour à l'accueil</a>
    </bw-empty-state>
  `,
})
export class NotFound {}
