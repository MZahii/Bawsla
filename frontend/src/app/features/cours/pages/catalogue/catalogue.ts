import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared';
import { CourseTile, CourseTileData, PageHeader, Skeleton } from '../../../../shared/ui';
import { CATEGORIES, FORMATEUR_DEFAUT, IMAGE_DEFAUT, LIBELLE_NIVEAU, NIVEAUX, formatDuree, libelleMatiere, libellePrix, matiereDepuisUrl } from '../../models/cours.constantes';
import { Cours } from '../../models/cours.model';
import { CoursImageService } from '../../services/cours-image.service';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Tri = 'recents' | 'titre';
type FiltrePrix = '' | 'gratuit' | 'payant';
type Etat = 'chargement' | 'ok' | 'erreur';

/**
 * Catalogue des cours : filtres à gauche (domaine, niveau, prix), recherche, tri, vue grille ou liste, pagination.
 * Sert au visiteur (/catalogue) et à l'apprenant (/app/explorer). Les cours viennent de cours-service
 * (findAll, seuls les cours publiés sont visibles par un apprenant) ; les filtres, le tri et la pagination se font
 * côté client. Ils se lisent aussi dans l'URL (?domaine=Données&niveau=DEBUTANT&prix=gratuit&q=sql),
 * ce qui permet les liens du menu Explorer. La note n'est pas encore fournie : pas d'étoiles.
 */
@Component({
  selector: 'app-catalogue',
  imports: [FormsModule, MatCheckboxModule, MatSelectModule, MatButtonToggleModule, MatPaginatorModule, MatRadioModule, MatIconModule, MatSnackBarModule, CourseTile, PageHeader, EmptyState, Skeleton],
  templateUrl: './catalogue.html',
  styleUrl: './catalogue.scss',
})
export class Catalogue {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CoursService);
  private readonly formateurs = inject(FormateurService);
  private readonly images = inject(CoursImageService);
  private readonly snack = inject(MatSnackBar);
  private readonly params = toSignal(this.route.queryParamMap);
  protected readonly espace = (this.route.snapshot.data['espace'] as string) ?? 'public';
  protected readonly base = this.espace === 'etudiant' ? '/app/explorer' : '/catalogue';

  protected readonly niveaux = NIVEAUX;
  protected readonly etat = signal<Etat>('chargement');
  private readonly cours = signal<Cours[]>([]);
  /** Nom du formateur par identifiant ; « Formateur » tant qu'il n'est pas connu. */
  private readonly noms = signal<Record<number, string>>({});
  /** Image affichable par identifiant de cours (téléchargée avec le JWT) ; image par défaut en attendant. */
  private readonly imagesCours = signal<Record<number, string>>({});

  protected readonly q = signal('');
  protected readonly domaines = signal<string[]>([]);
  protected readonly niveauxChoisis = signal<string[]>([]);
  protected readonly prix = signal<FiltrePrix>('');
  protected readonly tri = signal<Tri>('recents');
  protected readonly vue = signal<'grid' | 'row'>('grid');
  protected readonly page = signal(0);
  protected readonly parPage = signal(6);
  protected readonly filtresMobile = signal(false);

  /** Liste commune des catégories, plus celles des cours qui n'en font pas partie. */
  protected readonly categories = computed(() => {
    const connues: string[] = CATEGORIES.map((c) => c.nom);
    const autres = [...new Set(this.cours().map((c) => c.categorie).filter((n): n is string => !!n))].filter((n) => !connues.includes(n));
    return [...connues, ...autres];
  });

  constructor() {
    const p = this.route.snapshot.queryParamMap;
    // Un ancien libellé (Données, Back-end…) dans l'URL filtre sur « Informatique » au lieu d'afficher une liste vide.
    if (p.get('domaine')) this.domaines.set([matiereDepuisUrl(p.get('domaine'))]);
    if (p.get('niveau')) this.niveauxChoisis.set([p.get('niveau')!]);
    if (p.get('q')) this.q.set(p.get('q')!);
    if (p.get('prix') === 'gratuit' || p.get('prix') === 'payant') this.prix.set(p.get('prix') as FiltrePrix);
    this.charger();
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.service.findAll().subscribe({
      next: (liste) => {
        this.cours.set(liste);
        this.etat.set('ok');
        this.chargerFormateurs(liste);
        this.chargerImages(liste);
      },
      error: (err) => {
        this.snack.open(errorMessage(err, 'Impossible de charger le catalogue'), 'OK', { duration: 5000 });
        this.etat.set('erreur');
      },
    });
  }

  private chargerFormateurs(liste: Cours[]): void {
    for (const id of new Set(liste.map((c) => c.enseignantId))) {
      this.formateurs.nom(id).subscribe((nom) => this.noms.update((n) => ({ ...n, [id]: nom })));
    }
  }

  private chargerImages(liste: Cours[]): void {
    for (const c of liste) {
      this.images.url(c).subscribe((url) => this.imagesCours.update((m) => ({ ...m, [c.id]: url })));
    }
  }

  protected readonly resultats = computed(() => {
    this.params();
    const q = this.q().trim().toLowerCase();
    let l = this.cours();
    if (q) l = l.filter((c) => (c.titre + ' ' + (c.description ?? '')).toLowerCase().includes(q));
    if (this.domaines().length) l = l.filter((c) => !!c.categorie && this.domaines().includes(c.categorie));
    if (this.niveauxChoisis().length) l = l.filter((c) => !!c.niveau && this.niveauxChoisis().includes(c.niveau));
    if (this.prix()) l = l.filter((c) => (this.prix() === 'gratuit' ? !(c.prix > 0) : c.prix > 0));
    if (this.tri() === 'titre') return [...l].sort((a, b) => a.titre.localeCompare(b.titre, 'fr'));
    return [...l].sort((a, b) => b.dateCreation.localeCompare(a.dateCreation));
  });

  protected readonly pageCourante = computed<CourseTileData[]>(() => {
    const start = this.page() * this.parPage();
    return this.resultats().slice(start, start + this.parPage()).map((c) => ({
      id: c.id,
      titre: c.titre,
      description: c.description ?? undefined,
      categorie: libelleMatiere(c.categorie, c.categoriePrecision),
      niveau: c.niveau ? LIBELLE_NIVEAU[c.niveau] : '',
      enseignant: this.noms()[c.enseignantId] ?? FORMATEUR_DEFAUT,
      image: this.imagesCours()[c.id] ?? IMAGE_DEFAUT,
      prix: libellePrix(c.prix),
      chapitres: c.nbChapitres,
      duree: formatDuree(c.dureeTotaleMinutes) || undefined,
    }));
  });

  protected readonly nbFiltres = computed(() => this.domaines().length + this.niveauxChoisis().length + (this.prix() ? 1 : 0));

  protected basculer(liste: 'domaines' | 'niveauxChoisis', v: string): void {
    this[liste].update((l) => (l.includes(v) ? l.filter((x) => x !== v) : [...l, v]));
    this.page.set(0);
  }
  protected compte(nom: string): number {
    return this.cours().filter((c) => c.categorie === nom).length;
  }
  protected reinitialiser(): void {
    this.q.set('');
    this.domaines.set([]);
    this.niveauxChoisis.set([]);
    this.prix.set('');
    this.page.set(0);
  }
  protected paginer(e: PageEvent): void {
    this.page.set(e.pageIndex);
    this.parPage.set(e.pageSize);
  }
}
