import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

import { AUDIT } from '../../core/demo/demo-plus';
import { BwButton } from '../../shared';
import { PageHeader, Timeline, TimelineItem } from '../../shared/ui';

const TYPES: Record<string, { libelle: string; icone: string; ton: TimelineItem['ton'] }> = {
  utilisateur: { libelle: 'Utilisateurs', icone: 'person', ton: 'neutre' },
  cours: { libelle: 'Cours', icone: 'video_library', ton: 'accent' },
  catalogue: { libelle: 'Catalogue', icone: 'category', ton: 'accent' },
  ia: { libelle: 'IA', icone: 'auto_awesome', ton: 'ia' },
  forum: { libelle: 'Forum', icone: 'forum', ton: 'alerte' },
  certificat: { libelle: 'Certificats', icone: 'workspace_premium', ton: 'alerte' },
  parametres: { libelle: 'Paramètres', icone: 'tune', ton: 'neutre' },
};

/**
 * Back-office : journal d'audit (qui a fait quoi, quand), filtrable par type d'action.
 * À brancher : GET /api/users/audit (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-audit',
  imports: [FormsModule, MatChipsModule, MatIconModule, BwButton, PageHeader, Timeline],
  template: `
    <bw-page-header title="Journal d’audit" subtitle="Toutes les actions sensibles, conservées 12 mois." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Audit' }]">
      <button type="button" bwButton="secondaire"><mat-icon>download</mat-icon> Exporter (CSV)</button>
    </bw-page-header>
    <section class="card">
      <div class="card-head" style="flex-wrap: wrap">
        <mat-chip-listbox [value]="type()" (change)="type.set($event.value ?? null)" aria-label="Filtrer par type">
          @for (t of types; track t.id) { <mat-chip-option [value]="t.id">{{ t.libelle }}</mat-chip-option> }
        </mat-chip-listbox>
        <label class="field-search">
          <mat-icon aria-hidden="true">search</mat-icon>
          <input [ngModel]="q()" (ngModelChange)="q.set($event)" placeholder="Personne ou élément" aria-label="Rechercher dans le journal" />
        </label>
      </div>
      <div class="card-body"><bw-timeline [items]="items()" /></div>
      @if (items().length === 0) { <p class="card-body muted">Aucune action ne correspond.</p> }
    </section>
  `,
})
export class Audit {
  protected readonly types = Object.entries(TYPES).map(([id, t]) => ({ id, libelle: t.libelle }));
  protected readonly type = signal<string | null>(null);
  protected readonly q = signal('');
  protected readonly items = computed<TimelineItem[]>(() => {
    const q = this.q().toLowerCase();
    return AUDIT.filter((a) => (!this.type() || a.type === this.type()) && `${a.qui} ${a.cible}`.toLowerCase().includes(q)).map((a) => ({
      quand: a.quand,
      titre: `${a.qui} ${a.action} ${a.cible}`,
      detail: TYPES[a.type].libelle,
      icone: TYPES[a.type].icone,
      ton: TYPES[a.type].ton,
    }));
  });
}
