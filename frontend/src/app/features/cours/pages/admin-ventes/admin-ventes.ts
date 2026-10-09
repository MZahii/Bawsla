import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { coursParId } from '../../../../core/demo/demo-data';
import { LIBELLE_STATUT_VENTE, VENTES, VENTES_MOIS } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';

/**
 * Back-office : ventes des cours payants (paiement simulé dans l'aperçu). Chiffre d'affaires par mois,
 * dernières commandes, remboursements. La part du formateur est calculée après la commission.
 * À brancher : GET /api/cours/commandes (admin) et statistiques de ventes (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-admin-ventes',
  imports: [MatIconModule, BwButton, BwChart, Kpi, PageHeader],
  template: `
    <bw-page-header title="Ventes" subtitle="Cours payants : commandes, revenus et remboursements. Paiement simulé pour l’instant." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Ventes' }]">
      <button type="button" bwButton="secondaire"><mat-icon>download</mat-icon> Exporter (CSV)</button>
    </bw-page-header>

    <div class="grid-4">
      <bw-kpi label="Revenus d’octobre (DT)" value="1 659" icon="payments" [trend]="mois.montants" />
      <bw-kpi label="Commandes ce mois" value="23" icon="shopping_cart" [delta]="12" deltaLabel="vs septembre (à date)" />
      <bw-kpi label="Panier moyen" value="72 DT" icon="sell" />
      <bw-kpi label="Remboursements" value="1" icon="undo" />
    </div>

    <div class="grid-main mt">
      <section class="card">
        <div class="card-head"><div><h2>Revenus par mois</h2><span class="sub">En dinars, avant commission</span></div></div>
        <div class="card-body"><bw-chart type="bar" [labels]="mois.labels" [series]="[{ label: 'Revenus (DT)', data: mois.montants }]" [height]="260" ariaLabel="Revenus des ventes par mois" /></div>
      </section>
      <section class="card">
        <div class="card-head"><h2>Par cours</h2></div>
        <ul class="per">
          @for (p of parCours; track p.titre) {
            <li><span class="grow"><strong class="small">{{ p.titre }}</strong><span class="muted small">{{ p.prix }} DT, {{ p.ventes }} ventes</span></span><span class="num">{{ p.total }} DT</span></li>
          }
        </ul>
        <p class="muted small foot">Part du formateur : 80 % du prix. Commission Bawsla : 20 %.</p>
      </section>
    </div>

    <section class="card mt">
      <div class="card-head"><h2>Dernières commandes</h2></div>
      <div class="table-wrap">
        <table class="simple">
          <thead><tr><th>Référence</th><th>Date</th><th>Acheteur</th><th>Cours</th><th>Montant</th><th>Statut</th></tr></thead>
          <tbody>
            @for (v of ventes; track v.ref) {
              <tr>
                <td class="num">{{ v.ref }}</td>
                <td class="num muted">{{ v.quand }}</td>
                <td>{{ v.acheteur }}</td>
                <td class="small">{{ titre(v.coursId) }}</td>
                <td class="num">{{ v.montant }} DT</td>
                <td><span class="pill" [class.ok]="v.statut === 'PAYE'" [class.warn]="v.statut === 'REMBOURSE'" [class.ko]="v.statut === 'ECHEC'">{{ statut[v.statut] }}</span></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .grow { display: flex; flex: 1; flex-direction: column; }
    .per { margin: 0; padding: 0; list-style: none; }
    .per li { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-bottom: 1px solid var(--color-border); }
    .foot { margin: 0; padding: var(--space-3) var(--space-5); }
    .simple { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { padding: var(--space-3) var(--space-4); text-align: left; font: 600 13px var(--font-body); color: var(--color-text-muted); background: var(--color-bg); white-space: nowrap; }
    td { padding: 10px var(--space-4); border-top: 1px solid var(--color-border); }
  `,
})
export class AdminVentes {
  protected readonly mois = VENTES_MOIS;
  protected readonly ventes = VENTES;
  protected readonly statut = LIBELLE_STATUT_VENTE;
  protected readonly parCours = [
    { titre: 'Microservices avec Spring Boot', prix: 79, ventes: 41, total: 3239 },
    { titre: 'Concevoir une API REST', prix: 49, ventes: 38, total: 1862 },
  ];
  protected titre(id: number): string {
    return coursParId(id)?.titre ?? '';
  }
}
