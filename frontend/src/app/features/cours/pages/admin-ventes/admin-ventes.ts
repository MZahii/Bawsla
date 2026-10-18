import { Component } from '@angular/core';

import { EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

/**
 * Back-office : ventes des cours payants.
 * cours-service n'a pas encore de commandes ni de paiement en ligne : aucune vente à afficher.
 * À brancher : GET /api/cours/commandes (admin) et statistiques de ventes (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-admin-ventes',
  imports: [EmptyState, PageHeader],
  template: `
    <bw-page-header title="Ventes" subtitle="Cours payants : commandes, revenus et remboursements." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Ventes' }]" />
    <section class="card">
      <bw-empty-state
        icon="payments"
        title="Aucune vente pour l’instant."
        message="Le paiement en ligne n’est pas encore activé : les commandes, revenus et remboursements apparaîtront ici dès qu’il le sera."
      />
    </section>
  `,
})
export class AdminVentes {}
