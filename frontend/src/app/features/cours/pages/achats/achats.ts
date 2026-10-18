import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

/**
 * Mes achats : historique des cours payés et reçus.
 * cours-service n'a pas encore de commandes ni de paiement en ligne : la page l'indique honnêtement.
 * À brancher : GET /api/cours/commandes/me (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-achats',
  imports: [RouterLink, BwButton, EmptyState, PageHeader],
  template: `
    <bw-page-header title="Mes achats" subtitle="Tes cours payants et leurs reçus. Les cours gratuits n’apparaissent pas ici." />
    <section class="card">
      <bw-empty-state
        icon="receipt_long"
        title="Aucun achat pour l’instant : le paiement en ligne n’est pas encore activé."
        message="Les cours auxquels tu t’inscris se retrouvent dans Mes cours."
      >
        <a bwButton="principal" routerLink="/app/cours">Voir mes cours</a>
      </bw-empty-state>
    </section>
  `,
})
export class Achats {}
