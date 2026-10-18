import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';

import { BwButton, EmptyState } from '../../shared';
import { errorMessage } from '../../shared/models/api-response.model';
import { PageHeader, Skeleton, Timeline, TimelineItem } from '../../shared/ui';
import { PageResponse } from '../../features/forum/models/forum.model';
import { ActionModeration, JournalModeration } from '../../features/forum/models/moderation.model';
import { LIBELLES_ACTION, LIBELLES_CIBLE, dateHeure, motifLisible } from '../../features/forum/components/outils';
import { FiltresJournal, ModerationForumService } from '../../features/forum/services/moderation.service';

/** Ton de la frise selon la famille d'action. */
function ton(action: ActionModeration): TimelineItem['ton'] {
  if (action.endsWith('VIGIE') || action === 'MASQUAGE_AUTO') return 'ia';
  if (action === 'SANCTIONNER' || action === 'SUPPRIMER' || action === 'MASQUER' || action === 'ESCALADER') return 'alerte';
  if (action === 'MODIFIER_PARAMETRES') return 'neutre';
  return 'accent';
}

/**
 * Back-office : journal des actions de modération du forum (qui a fait quoi, quand), filtrable par action
 * et exportable en CSV. Données : forum-service (GET /api/forum/admin/journal et /admin/journal/export).
 * Il n'existe pas encore de journal d'audit global à la plateforme : seule la modération du forum est journalisée.
 */
@Component({
  selector: 'app-audit',
  imports: [MatIconModule, MatSelectModule, BwButton, EmptyState, PageHeader, Skeleton, Timeline],
  template: `
    <bw-page-header
      title="Journal d’audit"
      subtitle="Actions de modération du forum. Les autres modules ne sont pas encore journalisés."
      [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Audit' }]"
    >
      <button type="button" bwButton="secondaire" (click)="exporter()" [disabled]="exportEnCours()"><mat-icon>download</mat-icon> Exporter (CSV)</button>
    </bw-page-header>
    <section class="card">
      <div class="card-head filtres">
        <mat-select class="sel" [value]="filtres().action ?? null" (valueChange)="filtrer($event ?? undefined)" placeholder="Toutes les actions" aria-label="Filtrer par action">
          <mat-option [value]="null">Toutes les actions</mat-option>
          @for (a of actions; track a) { <mat-option [value]="a">{{ libellesAction[a] }}</mat-option> }
        </mat-select>
        @if (erreurExport()) { <span class="small erreur" role="alert">{{ erreurExport() }}</span> }
      </div>
      @if (chargement()) {
        <div class="card-body" aria-busy="true"><bw-skeleton height="320px" /></div>
      } @else if (erreur()) {
        <bw-empty-state icon="error" title="Journal indisponible" [message]="erreur()" actionLabel="Réessayer" (action)="charger(page()?.page ?? 0)" />
      } @else if (items().length) {
        <div class="card-body"><bw-timeline [items]="items()" /></div>
        @if (page(); as p) {
          <div class="pager">
            <button type="button" bwButton="fantome" [disabled]="p.page === 0" (click)="charger(p.page - 1)">Précédent</button>
            <span class="small muted">Page {{ p.page + 1 }}</span>
            <button type="button" bwButton="fantome" [disabled]="!p.suivante" (click)="charger(p.page + 1)">Suivant</button>
          </div>
        }
      } @else {
        <bw-empty-state title="Aucune action enregistrée" [message]="filtres().action ? 'Aucune action de ce type dans le journal.' : ''" />
      }
    </section>
  `,
  styles: `
    .filtres { flex-wrap: wrap; gap: var(--space-3); }
    .sel { min-width: 240px; max-width: 100%; }
    .erreur { color: var(--color-error); }
    .pager { display: flex; align-items: center; justify-content: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-top: 1px solid var(--color-border); }
  `,
})
export class Audit implements OnInit {
  private readonly moderation = inject(ModerationForumService);

  protected readonly actions = Object.keys(LIBELLES_ACTION) as ActionModeration[];
  protected readonly libellesAction = LIBELLES_ACTION;

  protected readonly filtres = signal<FiltresJournal>({});
  protected readonly page = signal<PageResponse<JournalModeration> | null>(null);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal('');
  protected readonly exportEnCours = signal(false);
  protected readonly erreurExport = signal('');

  protected readonly items = computed<TimelineItem[]>(() =>
    (this.page()?.contenu ?? []).map((a) => ({
      quand: dateHeure(a.creeLe),
      titre: `${a.acteurNom || 'Modération automatique'} : ${LIBELLES_ACTION[a.action]}`,
      detail: [LIBELLES_CIBLE[a.cibleType] || a.cibleType, a.motif ? motifLisible(a.motif) : ''].filter(Boolean).join(', '),
      icone: a.acteurNom ? 'gavel' : 'auto_awesome',
      ton: ton(a.action),
    })),
  );

  ngOnInit(): void {
    this.charger(0);
  }

  protected filtrer(action: ActionModeration | undefined): void {
    this.filtres.set({ ...this.filtres(), action });
    this.charger(0);
  }

  protected charger(page: number): void {
    this.chargement.set(true);
    this.erreur.set('');
    this.moderation.journal({ ...this.filtres(), page }).subscribe({
      next: (p) => {
        this.page.set(p);
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger le journal'));
        this.chargement.set(false);
      },
    });
  }

  protected exporter(): void {
    this.exportEnCours.set(true);
    this.erreurExport.set('');
    this.moderation.journalCsv(this.filtres()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'journal-moderation.csv';
        a.click();
        URL.revokeObjectURL(url);
        this.exportEnCours.set(false);
      },
      error: (err) => {
        this.erreurExport.set(errorMessage(err, 'L’export a échoué'));
        this.exportEnCours.set(false);
      },
    });
  }
}
