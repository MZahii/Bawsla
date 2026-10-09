import { Component, computed, effect, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';

import { LIBELLE_PROFIL, NOTIONS } from '../../../../core/demo/demo-data';
import { APPRENANTS, InscriptionApprenant, LIBELLE_RISQUE, Risque, coursDuFormateur } from '../../../../core/demo/demo-plus';
import { AiBox, BwButton } from '../../../../shared';
import { Avatar, BwChart, Kpi, PageHeader } from '../../../../shared/ui';

/**
 * Studio : les apprenants inscrits aux cours du formateur. Pas de classe ni de groupe : une ligne par
 * inscription (apprenant × cours), filtrable par cours, avec la date d'inscription, la progression,
 * la moyenne aux quiz et le risque d'abandon (prédit par le modèle du module User) pour ce cours.
 * Le risque n'est jamais montré à l'apprenant (DESIGN.md, section 8).
 * À brancher : UserService.apprenantsDuFormateur() (inscriptions de cours-service) et /api/ai/user/risque.
 */
@Component({
  selector: 'app-apprenants',
  imports: [FormsModule, MatTableModule, MatSortModule, MatPaginatorModule, MatSelectModule, MatButtonToggleModule, MatIconModule, AiBox, BwButton, Avatar, BwChart, Kpi, PageHeader],
  templateUrl: './apprenants.html',
  styleUrl: './apprenants.scss',
})
export class Apprenants {
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);

  protected readonly colonnes = ['nom', 'cours', 'inscritLe', 'progression', 'moyenne', 'tendance', 'risque', 'activite'];
  protected readonly libelle = LIBELLE_RISQUE;
  protected readonly profil = LIBELLE_PROFIL;
  protected readonly cours = coursDuFormateur().filter((c) => APPRENANTS.some((a) => a.coursId === c.id));
  protected readonly coursChoisi = signal<number | null>(null);
  protected readonly risque = signal<Risque | 'tous'>('tous');
  protected readonly q = signal('');
  protected readonly source = new MatTableDataSource<InscriptionApprenant>(APPRENANTS);
  protected readonly ouvert = signal<InscriptionApprenant | null>(null);

  protected readonly compte = (r: Risque) => APPRENANTS.filter((e) => e.risque === r).length;
  protected readonly moyenne = Math.round(APPRENANTS.reduce((t, e) => t + e.moyenne, 0) / APPRENANTS.length);
  protected readonly certifies = APPRENANTS.filter((e) => e.certifie).length;

  protected readonly detail = computed(() => {
    const e = this.ouvert();
    if (!e) return null;
    return {
      courbe: { labels: ['S34', 'S35', 'S36', 'S37', 'S38', 'S39'], series: [{ label: 'Score moyen', data: e.tendance }] },
      notions: NOTIONS.slice(0, 5).map((n, i) => ({ nom: n.nom, valeur: Math.max(15, Math.min(98, e.moyenne + ((i * 23) % 40) - 20)) })),
    };
  });

  constructor() {
    effect(() => {
      const c = this.coursChoisi();
      const r = this.risque();
      const q = this.q().toLowerCase();
      this.source.data = APPRENANTS.filter((e) => (!c || e.coursId === c) && (r === 'tous' || e.risque === r) && e.nom.toLowerCase().includes(q));
      const s = this.sort();
      const p = this.paginator();
      if (s) this.source.sort = s;
      if (p) this.source.paginator = p;
    });
  }

  /** Points d'une mini-courbe SVG (80 × 24) à partir des scores. */
  protected sparkline(v: number[]): string {
    const min = Math.min(...v);
    const max = Math.max(...v);
    return v.map((x, i) => `${(i / (v.length - 1)) * 80},${22 - ((x - min) / (max - min || 1)) * 20}`).join(' ');
  }
}
