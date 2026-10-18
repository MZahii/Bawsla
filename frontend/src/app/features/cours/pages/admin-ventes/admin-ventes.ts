import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared';
import { Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { Commande } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';

/**
 * Back-office : ventes des cours payants (paiement simulé, GET /api/cours/commandes/ventes).
 * Le serveur filtre : toutes les ventes pour l'ADMIN, celles de ses cours pour un formateur. Les acheteurs n'apparaissent que par leur identifiant.
 */
@Component({
  selector: 'app-admin-ventes',
  imports: [DatePipe, DecimalPipe, EmptyState, Kpi, PageHeader, Skeleton],
  template: `
    <bw-page-header title="Ventes" subtitle="Cours payants : commandes et revenus (paiements simulés)." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Ventes' }]" />
    @if (chargement()) {
      <bw-skeleton height="200px" />
    } @else if (erreur()) {
      <section class="card"><bw-empty-state icon="error" [title]="erreur()!" /></section>
    } @else {
      <div class="grid-3">
        <bw-kpi label="Commandes" [value]="ventes().length" icon="receipt_long" />
        <bw-kpi label="Revenus (TND)" [value]="revenus()" icon="payments" />
        <bw-kpi label="Cours vendus" [value]="coursVendus()" icon="menu_book" />
      </div>
      <section class="card mt">
        @if (ventes().length === 0) {
          <bw-empty-state icon="payments" title="Aucune vente pour l’instant." message="Les achats des cours payants apparaîtront ici." />
        } @else {
          <div class="scroll">
            <table class="liste">
              <thead><tr><th>Date</th><th>Référence</th><th>Cours</th><th>Apprenant</th><th>Carte</th><th class="num">Montant</th></tr></thead>
              <tbody>
                @for (v of ventes(); track v.id) {
                  <tr>
                    <td>{{ v.datePaiement | date: 'd MMM y, HH:mm' }}</td>
                    <td class="ref">{{ v.reference }}</td>
                    <td>{{ v.coursTitre ?? 'Cours supprimé' }}</td>
                    <td>n° {{ v.etudiantId }}</td>
                    <td>{{ v.carte }}</td>
                    <td class="num">{{ v.montant | number: '1.0-2' }} {{ v.devise }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </section>
    }
  `,
  styleUrl: '../achats/achats.scss',
})
export class AdminVentes implements OnInit {
  private readonly service = inject(CoursService);
  protected readonly ventes = signal<Commande[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly revenus = computed(() => Math.round(this.ventes().reduce((s, v) => s + v.montant, 0) * 100) / 100);
  protected readonly coursVendus = computed(() => new Set(this.ventes().map((v) => v.coursId)).size);

  ngOnInit(): void {
    this.service.ventes().subscribe({
      next: (v) => {
        this.ventes.set(v);
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger les ventes'));
        this.chargement.set(false);
      },
    });
  }
}
