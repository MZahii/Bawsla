import { Component, computed, effect, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { libellePrix } from '../../../../core/demo/demo-data';
import { coursDuFormateur, meta } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { Kpi, PageHeader, Rating } from '../../../../shared/ui';

interface Ligne {
  id: number;
  titre: string;
  categorie: string;
  image: string;
  statut: 'PUBLIE' | 'BROUILLON';
  inscrits: number;
  note: number;
  completion: number;
  chapitres: number;
  prix: string;
  maj: string;
}

/**
 * Studio : tous les cours du formateur dans un tableau triable et paginé, avec filtres par statut
 * et actions rapides (modifier, dupliquer, publier, archiver).
 * À brancher : CoursService.findByEnseignant() et les statistiques par cours.
 */
@Component({
  selector: 'app-studio-cours',
  imports: [RouterLink, FormsModule, MatTableModule, MatSortModule, MatPaginatorModule, MatMenuModule, MatIconModule, MatButtonToggleModule, BwButton, PageHeader, Kpi, Rating],
  templateUrl: './studio-cours.html',
})
export class StudioCours {
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);

  protected readonly colonnes = ['titre', 'statut', 'prix', 'inscrits', 'note', 'completion', 'maj', 'actions'];
  protected readonly lignes: Ligne[] = coursDuFormateur().map((c) => ({
    id: c.id,
    titre: c.titre,
    categorie: c.categorie,
    image: meta(c.id).image,
    statut: meta(c.id).statut,
    inscrits: meta(c.id).inscrits,
    note: meta(c.id).note,
    completion: Math.round(c.progression * 0.8 + 12) % 100,
    chapitres: c.chapitres.length,
    prix: libellePrix(c.prix),
    maj: meta(c.id).majLe,
  }));
  protected readonly filtre = signal<'tous' | 'PUBLIE' | 'BROUILLON'>('tous');
  protected readonly q = signal('');
  protected readonly source = new MatTableDataSource<Ligne>(this.lignes);
  protected readonly publies = this.lignes.filter((l) => l.statut === 'PUBLIE').length;
  protected readonly inscritsTotal = this.lignes.reduce((t, l) => t + l.inscrits, 0);

  constructor() {
    effect(() => {
      const f = this.filtre();
      const q = this.q().toLowerCase();
      this.source.data = this.lignes.filter((l) => (f === 'tous' || l.statut === f) && l.titre.toLowerCase().includes(q));
      const s = this.sort();
      const p = this.paginator();
      if (s) this.source.sort = s;
      if (p) this.source.paginator = p;
    });
  }

  protected readonly vide = computed(() => this.filtre() !== 'tous' || this.q() !== '');
}
