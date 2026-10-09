import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { coursParId } from '../../../../core/demo/demo-data';
import { LIBELLE_STATUT_VENTE, VENTES } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

/**
 * Mes achats : historique des cours payés et reçus.
 * À brancher : GET /api/cours/commandes/me.
 */
@Component({
  selector: 'app-achats',
  imports: [RouterLink, MatIconModule, BwButton, PageHeader],
  template: `
    <bw-page-header title="Mes achats" subtitle="Tes cours payants et leurs reçus. La plupart des cours sont gratuits : ils n’apparaissent pas ici." />
    <section class="card">
      <div class="table-wrap">
        <table class="simple">
          <thead><tr><th>Date</th><th>Cours</th><th>Référence</th><th>Montant</th><th>Statut</th><th><span class="bw-visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            @for (a of achats; track a.ref) {
              <tr>
                <td>{{ a.quand.split(' ')[0] }}/2026</td>
                <td><a [routerLink]="['/app/explorer', a.coursId]">{{ titre(a.coursId) }}</a></td>
                <td class="num">{{ a.ref }}</td>
                <td class="num">{{ a.montant }} DT</td>
                <td><span class="pill ok">{{ statut[a.statut] }}</span></td>
                <td><button type="button" bwButton="fantome"><mat-icon>receipt_long</mat-icon> Reçu</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .simple { width: 100%; border-collapse: collapse; font-size: 15px; }
    th { padding: var(--space-3) var(--space-4); text-align: left; font: 600 13px var(--font-body); color: var(--color-text-muted); background: var(--color-bg); }
    td { padding: var(--space-3) var(--space-4); border-top: 1px solid var(--color-border); }
    td a { color: var(--color-text); font-weight: 600; }
  `,
})
export class Achats {
  protected readonly achats = VENTES.filter((v) => v.acheteur === 'Malek Ouji');
  protected readonly statut = LIBELLE_STATUT_VENTE;
  protected titre(id: number): string {
    return coursParId(id)?.titre ?? '';
  }
}
