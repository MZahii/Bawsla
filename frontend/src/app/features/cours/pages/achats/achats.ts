import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { Commande } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';

/** Mes achats : cours payés par l'apprenant (paiement simulé, GET /api/cours/commandes/mes), avec la référence de chaque reçu. */
@Component({
  selector: 'app-achats',
  imports: [DatePipe, DecimalPipe, RouterLink, BwButton, EmptyState, PageHeader, Skeleton],
  template: `
    <bw-page-header title="Mes achats" subtitle="Tes cours payants et leurs reçus. Les cours gratuits n’apparaissent pas ici." />
    @if (chargement()) {
      <bw-skeleton height="200px" />
    } @else if (erreur()) {
      <section class="card"><bw-empty-state icon="error" [title]="erreur()!" /></section>
    } @else if (commandes().length === 0) {
      <section class="card">
        <bw-empty-state icon="receipt_long" title="Aucun achat pour l’instant." message="Les cours gratuits auxquels tu t’inscris se retrouvent dans Mes cours.">
          <a bwButton="principal" routerLink="/app/explorer">Explorer le catalogue</a>
        </bw-empty-state>
      </section>
    } @else {
      <section class="card">
        <div class="scroll">
          <table class="liste">
            <thead><tr><th>Cours</th><th>Date</th><th>Référence</th><th>Carte</th><th class="num">Montant</th></tr></thead>
            <tbody>
              @for (c of commandes(); track c.id) {
                <tr>
                  <td>
                    @if (c.coursTitre) { <a [routerLink]="['/app/cours', c.coursId, 'lecture']">{{ c.coursTitre }}</a> }
                    @else { <span class="muted">Cours supprimé</span> }
                  </td>
                  <td>{{ c.datePaiement | date: 'd MMM y' }}</td>
                  <td class="ref">{{ c.reference }}</td>
                  <td>{{ c.carte }}</td>
                  <td class="num">{{ c.montant | number: '1.0-2' }} {{ c.devise }}</td>
                </tr>
              }
            </tbody>
            <tfoot><tr><td colspan="4">Total</td><td class="num">{{ total() | number: '1.0-2' }} TND</td></tr></tfoot>
          </table>
        </div>
        <p class="muted small card-pad">Paiements simulés : aucune somme n’a été débitée.</p>
      </section>
    }
  `,
  styleUrl: './achats.scss',
})
export class Achats implements OnInit {
  private readonly service = inject(CoursService);
  protected readonly commandes = signal<Commande[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly total = computed(() => this.commandes().reduce((s, c) => s + c.montant, 0));

  ngOnInit(): void {
    this.service.mesCommandes().subscribe({
      next: (c) => {
        this.commandes.set(c);
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger tes achats'));
        this.chargement.set(false);
      },
    });
  }
}
