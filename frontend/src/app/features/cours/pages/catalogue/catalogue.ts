import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute } from '@angular/router';

import { COURS } from '../../../../core/demo/demo-data';
import { CATEGORIES, NIVEAUX, dureeTotale, meta, tuile } from '../../../../core/demo/demo-plus';
import { EmptyState } from '../../../../shared';
import { CourseTile, PageHeader } from '../../../../shared/ui';

type Tri = 'pertinence' | 'populaires' | 'note' | 'recents';

/**
 * Catalogue des cours : filtres à gauche (domaine, niveau, prix, durée, note), tri, vue grille ou liste, pagination.
 * Sert au visiteur (/catalogue) et à l'apprenant (/app/explorer). Les filtres se lisent aussi dans l'URL
 * (?domaine=Données&niveau=DEBUTANT&q=sql), ce qui permet les liens du menu Explorer.
 * À brancher : CoursService.search(filtres) avec pagination côté serveur.
 */
@Component({
  selector: 'app-catalogue',
  imports: [FormsModule, MatCheckboxModule, MatRadioModule, MatSelectModule, MatButtonToggleModule, MatPaginatorModule, MatIconModule, CourseTile, PageHeader, EmptyState],
  templateUrl: './catalogue.html',
  styleUrl: './catalogue.scss',
})
export class Catalogue {
  private readonly route = inject(ActivatedRoute);
  private readonly params = toSignal(this.route.queryParamMap);
  protected readonly espace = (this.route.snapshot.data['espace'] as string) ?? 'public';
  protected readonly base = this.espace === 'etudiant' ? '/app/explorer' : '/catalogue';

  protected readonly categories = CATEGORIES;
  protected readonly niveaux = NIVEAUX;
  protected readonly durees = [
    { code: 'court', libelle: 'Moins de 2 h' },
    { code: 'moyen', libelle: '2 à 3 h' },
    { code: 'long', libelle: 'Plus de 3 h' },
  ];

  protected readonly q = signal('');
  protected readonly domaines = signal<string[]>([]);
  protected readonly niveauxChoisis = signal<string[]>([]);
  protected readonly duree = signal('');
  protected readonly noteMin = signal(0);
  protected readonly prix = signal<'' | 'gratuit' | 'payant'>('');
  protected readonly tri = signal<Tri>('pertinence');
  protected readonly vue = signal<'grid' | 'row'>('grid');
  protected readonly page = signal(0);
  protected readonly parPage = signal(6);
  protected readonly filtresMobile = signal(false);

  constructor() {
    const p = this.route.snapshot.queryParamMap;
    if (p.get('domaine')) this.domaines.set([p.get('domaine')!]);
    if (p.get('niveau')) this.niveauxChoisis.set([p.get('niveau')!]);
    if (p.get('q')) this.q.set(p.get('q')!);
    if (p.get('prix') === 'gratuit' || p.get('prix') === 'payant') this.prix.set(p.get('prix') as 'gratuit' | 'payant');
  }

  protected readonly resultats = computed(() => {
    this.params();
    const q = this.q().trim().toLowerCase();
    let l = COURS.filter((c) => meta(c.id).statut === 'PUBLIE');
    if (q) l = l.filter((c) => (c.titre + ' ' + c.description + ' ' + c.motsCles.join(' ')).toLowerCase().includes(q));
    if (this.domaines().length) l = l.filter((c) => this.domaines().includes(c.categorie));
    if (this.niveauxChoisis().length) l = l.filter((c) => this.niveauxChoisis().includes(c.niveau));
    const d = this.duree();
    if (d) l = l.filter((c) => { const m = dureeTotale(c); return d === 'court' ? m < 120 : d === 'moyen' ? m >= 120 && m <= 180 : m > 180; });
    if (this.noteMin()) l = l.filter((c) => meta(c.id).note >= this.noteMin());
    if (this.prix()) l = l.filter((c) => (this.prix() === 'gratuit' ? c.prix === 0 : c.prix > 0));
    const t = this.tri();
    if (t === 'populaires') l = [...l].sort((a, b) => meta(b.id).inscrits - meta(a.id).inscrits);
    if (t === 'note') l = [...l].sort((a, b) => meta(b.id).note - meta(a.id).note);
    if (t === 'recents') l = [...l].sort((a, b) => b.id - a.id);
    return l;
  });

  protected readonly pageCourante = computed(() => {
    const start = this.page() * this.parPage();
    return this.resultats().slice(start, start + this.parPage()).map((c) => tuile(c, this.espace === 'etudiant' && c.inscrit));
  });

  protected readonly nbFiltres = computed(() => this.domaines().length + this.niveauxChoisis().length + (this.duree() ? 1 : 0) + (this.noteMin() ? 1 : 0) + (this.prix() ? 1 : 0));

  protected basculer(liste: 'domaines' | 'niveauxChoisis', v: string): void {
    this[liste].update((l) => (l.includes(v) ? l.filter((x) => x !== v) : [...l, v]));
    this.page.set(0);
  }
  protected compte(nom: string): number {
    return COURS.filter((c) => c.categorie === nom).length;
  }
  protected reinitialiser(): void {
    this.q.set('');
    this.domaines.set([]);
    this.niveauxChoisis.set([]);
    this.duree.set('');
    this.noteMin.set(0);
    this.prix.set('');
    this.page.set(0);
  }
  protected paginer(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.parPage.set(e.pageSize);
  }
}
