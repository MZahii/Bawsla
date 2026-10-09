import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { coursParId } from '../../../../core/demo/demo-data';
import { CERTIFICATS_EMIS, Certificat } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { Kpi, PageHeader } from '../../../../shared/ui';

/**
 * Back-office : certificats délivrés. Recherche par numéro ou par nom, vérification, révocation
 * (fraude à l'examen) avec trace dans le journal d'audit.
 * À brancher : GET /api/quiz/certificats (admin), POST /api/quiz/certificats/{numero}/revoquer.
 */
@Component({
  selector: 'app-admin-certificats',
  imports: [FormsModule, RouterLink, MatIconModule, MatMenuModule, BwButton, Kpi, PageHeader],
  template: `
    <bw-page-header title="Certificats" subtitle="Tous les certificats délivrés après réussite d’un examen final." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Certificats' }]">
      <a bwButton="secondaire" routerLink="/verifier"><mat-icon>open_in_new</mat-icon> Page de vérification publique</a>
    </bw-page-header>

    <div class="grid-4">
      <bw-kpi label="Certificats délivrés" value="412" icon="workspace_premium" [delta]="9" deltaLabel="ce mois" [trend]="[280, 301, 322, 351, 380, 412]" />
      <bw-kpi label="Taux de réussite à l’examen" value="78 %" icon="task_alt" />
      <bw-kpi label="Score moyen" value="83 %" icon="percent" />
      <bw-kpi label="Révoqués" [value]="revoques().length + 1" icon="block" />
    </div>

    <section class="card mt">
      <div class="card-head">
        <label class="field-search grow">
          <mat-icon aria-hidden="true">search</mat-icon>
          <input [ngModel]="q()" (ngModelChange)="q.set($event)" placeholder="Numéro, titulaire ou cours" aria-label="Rechercher un certificat" />
        </label>
      </div>
      <div class="table-wrap">
        <table class="simple">
          <thead><tr><th>Numéro</th><th>Titulaire</th><th>Cours</th><th>Date</th><th>Score</th><th>Statut</th><th><span class="bw-visually-hidden">Actions</span></th></tr></thead>
          <tbody>
            @for (c of liste(); track c.numero) {
              <tr>
                <td class="num">{{ c.numero }}</td>
                <td><strong>{{ c.titulaire }}</strong></td>
                <td class="small">{{ titre(c.coursId) }}</td>
                <td class="num muted">{{ c.date }}</td>
                <td class="num">{{ c.score }} %</td>
                <td>
                  @if (revoques().includes(c.numero)) { <span class="pill ko">Révoqué</span> } @else { <span class="pill ok">Valide</span> }
                </td>
                <td>
                  <button type="button" class="icon-btn" [matMenuTriggerFor]="m" [attr.aria-label]="'Actions pour ' + c.numero"><mat-icon>more_vert</mat-icon></button>
                  <mat-menu #m="matMenu" xPosition="before">
                    <a mat-menu-item [routerLink]="['/verifier', c.numero]"><mat-icon>verified</mat-icon>Voir la page publique</a>
                    <button mat-menu-item type="button"><mat-icon>download</mat-icon>Télécharger le PDF</button>
                    @if (!revoques().includes(c.numero)) {
                      <button mat-menu-item type="button" class="danger" (click)="revoquer(c)"><mat-icon>block</mat-icon>Révoquer</button>
                    }
                  </mat-menu>
                </td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="muted">Aucun certificat ne correspond.</td></tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: `
    .grow { flex: 1; }
    .simple { width: 100%; border-collapse: collapse; font-size: 14px; }
    th { padding: var(--space-3) var(--space-4); text-align: left; font: 600 13px var(--font-body); color: var(--color-text-muted); background: var(--color-bg); white-space: nowrap; }
    td { padding: 10px var(--space-4); border-top: 1px solid var(--color-border); }
  `,
})
export class AdminCertificats {
  private readonly snack = inject(MatSnackBar);
  protected readonly q = signal('');
  protected readonly revoques = signal<string[]>([]);
  protected readonly liste = computed(() => {
    const q = this.q().trim().toLowerCase();
    return CERTIFICATS_EMIS.filter((c) => !q || `${c.numero} ${c.titulaire} ${this.titre(c.coursId)}`.toLowerCase().includes(q));
  });

  protected titre(id: number): string {
    return coursParId(id)?.titre ?? '';
  }

  protected revoquer(c: Certificat): void {
    this.revoques.update((l) => [...l, c.numero]);
    this.snack.open(`Certificat ${c.numero} révoqué. Action ajoutée au journal d’audit.`, 'Annuler', { duration: 4000 });
  }
}
