import { Component, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { DEMANDES_FORMATEUR } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader } from '../../../../shared/ui';

type Demande = (typeof DEMANDES_FORMATEUR)[number];

/**
 * Back-office : demandes pour devenir formateur. N'importe qui peut postuler à l'inscription ;
 * l'admin accepte (le compte peut publier) ou refuse (avec un motif envoyé par email).
 * À brancher : GET /api/users/demandes-formateur, POST .../{id}/accepter et .../{id}/refuser
 * (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-admin-formateurs',
  imports: [MatIconModule, BwButton, EmptyState, Avatar, Kpi, PageHeader],
  template: `
    <bw-page-header title="Demandes formateurs" subtitle="Toute personne peut demander à enseigner. Vérifie son expertise avant d’ouvrir la publication." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Demandes formateurs' }]" />

    <div class="grid-4">
      <bw-kpi label="En attente" [value]="demandes().length" icon="hourglass_top" />
      <bw-kpi label="Acceptées ce mois" [value]="4 + acceptees()" icon="how_to_reg" />
      <bw-kpi label="Refusées ce mois" value="1" icon="person_off" />
      <bw-kpi label="Délai moyen de réponse" value="19 h" icon="schedule" />
    </div>

    <div class="list mt">
      @for (d of demandes(); track d.id) {
        <article class="card req">
          <header>
            <bw-avatar [nom]="d.nom" [size]="48" />
            <div class="who">
              <h2>{{ d.nom }}</h2>
              <span class="muted small">{{ d.email }}, demande envoyée {{ d.quand }}</span>
            </div>
          </header>
          <dl>
            <div><dt>Expertise</dt><dd>{{ d.expertise }}</dd></div>
            <div><dt>Expérience</dt><dd>{{ d.experience }}</dd></div>
            <div><dt>Lien</dt><dd>{{ d.lien }}</dd></div>
          </dl>
          <p class="bio">{{ d.bio }}</p>
          @if (refus() === d.id) {
            <label class="lbl" [for]="'m-' + d.id">Motif du refus (envoyé par email)</label>
            <textarea [id]="'m-' + d.id" rows="2" placeholder="Ex. : merci de préciser ton expérience dans le domaine."></textarea>
            <footer>
              <button type="button" bwButton="fantome" (click)="refus.set(null)">Annuler</button>
              <button type="button" bwButton="danger" (click)="traiter(d, false)">Confirmer le refus</button>
            </footer>
          } @else {
            <footer>
              <button type="button" bwButton="fantome"><mat-icon>mail</mat-icon> Demander des précisions</button>
              <button type="button" bwButton="secondaire" (click)="refus.set(d.id)">Refuser</button>
              <button type="button" bwButton="secondaire" class="ok" (click)="traiter(d, true)"><mat-icon>check</mat-icon> Accepter</button>
            </footer>
          }
        </article>
      } @empty {
        <bw-empty-state icon="how_to_reg" title="Aucune demande en attente" message="Les nouvelles demandes apparaîtront ici." />
      }
    </div>
  `,
  styles: `
    .list { display: grid; gap: var(--space-4); }
    .req { padding: var(--space-5); }
    header { display: flex; align-items: center; gap: var(--space-3); }
    .who { display: flex; flex-direction: column; }
    h2 { margin: 0; font: 600 18px var(--font-heading); }
    dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--space-3); margin: var(--space-4) 0; }
    dt { font-size: 13px; color: var(--color-text-muted); }
    dd { margin: 2px 0 0; font-weight: 600; }
    .bio { margin: 0; padding: var(--space-3); border-left: 3px solid var(--color-border); background: var(--color-bg); }
    footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); margin-top: var(--space-4); }
    .lbl { display: block; margin-top: var(--space-4); font-weight: 600; font-size: 14px; }
    textarea { width: 100%; box-sizing: border-box; margin-top: var(--space-2); padding: var(--space-3); border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-surface); color: var(--color-text); font: 400 15px var(--font-body); }
  `,
})
export class AdminFormateurs {
  private readonly snack = inject(MatSnackBar);
  protected readonly demandes = signal<Demande[]>([...DEMANDES_FORMATEUR]);
  protected readonly refus = signal<number | null>(null);
  protected readonly acceptees = signal(0);

  protected traiter(d: Demande, accepte: boolean): void {
    this.demandes.update((l) => l.filter((x) => x.id !== d.id));
    this.refus.set(null);
    if (accepte) this.acceptees.update((n) => n + 1);
    this.snack.open(accepte ? `${d.nom} peut maintenant publier des cours` : `Demande de ${d.nom} refusée`, 'OK', { duration: 3000 });
  }
}
