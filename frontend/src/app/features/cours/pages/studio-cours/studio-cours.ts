import { AsyncPipe } from '@angular/common';
import { Component, TemplateRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { LIBELLE_NIVEAU, LIBELLE_STATUT, VIDE, formatDate, libelleMatiere, libellePrix } from '../../models/cours.constantes';
import { Cours, StatutCours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';

interface Ligne {
  id: number;
  cours: Cours;
  titre: string;
  categorie: string;
  niveau: string;
  statut: StatutCours;
  prix: string;
  /** Pas encore fournis par cours-service : 0 ou « — » en attendant les lots suivants. */
  inscrits: number;
  completion: string;
  maj: string;
}

type Etat = 'chargement' | 'ok' | 'erreur';
type Filtre = 'tous' | StatutCours;

/**
 * Studio : les cours du formateur (tous les cours pour un ADMIN), lus depuis cours-service.
 * Statut et prix sont réels ; publier, dépublier, archiver et supprimer (avec confirmation) passent par le backend.
 * Inscrits, note et achèvement valent 0 ou « — » tant que le backend ne les fournit pas.
 */
@Component({
  selector: 'app-studio-cours',
  imports: [
    RouterLink, FormsModule, MatTableModule, MatSortModule, MatPaginatorModule, MatMenuModule, MatIconModule,
    AsyncPipe, MatButtonToggleModule, MatDialogModule, MatSnackBarModule, BwButton, EmptyState, PageHeader, Kpi, Skeleton, CoursImage,
  ],
  templateUrl: './studio-cours.html',
})
export class StudioCours {
  private readonly service = inject(CoursService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);
  private readonly dialog = inject(MatDialog);
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);
  private readonly confirmation = viewChild.required<TemplateRef<unknown>>('confirmer');
  private ref?: MatDialogRef<unknown>;

  protected readonly vide = VIDE;
  protected readonly libelle = (s: StatutCours): string => LIBELLE_STATUT[s];
  protected readonly colonnes = ['titre', 'statut', 'prix', 'inscrits', 'note', 'completion', 'maj', 'actions'];
  protected readonly etat = signal<Etat>('chargement');
  protected readonly lignes = signal<Ligne[]>([]);
  protected readonly filtre = signal<Filtre>('tous');
  protected readonly q = signal('');
  protected readonly source = new MatTableDataSource<Ligne>([]);
  protected readonly aSupprimer = signal<Ligne | null>(null);
  protected readonly suppression = signal(false);
  protected readonly enCours = signal<number | null>(null);

  protected readonly publies = computed(() => this.lignes().filter((l) => l.statut === 'PUBLIE').length);
  protected readonly brouillons = computed(() => this.lignes().filter((l) => l.statut === 'BROUILLON').length);
  protected readonly inscritsTotal = computed(() => this.lignes().reduce((t, l) => t + l.inscrits, 0));

  constructor() {
    effect(() => {
      const f = this.filtre();
      const q = this.q().toLowerCase();
      this.source.data = this.lignes().filter((l) => (f === 'tous' || l.statut === f) && l.titre.toLowerCase().includes(q));
      const s = this.sort();
      const p = this.paginator();
      if (s) this.source.sort = s;
      if (p) this.source.paginator = p;
    });
    this.charger();
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.service.findAll().subscribe({
      next: (cours) => {
        const moi = this.auth.currentUser();
        const miens = this.auth.role() === 'ENSEIGNANT' ? cours.filter((c) => c.enseignantId === moi?.id) : cours;
        this.lignes.set(miens.map(versLigne));
        this.etat.set('ok');
      },
      error: (err) => {
        this.snack.open(errorMessage(err, 'Impossible de charger les cours'), 'OK', { duration: 5000 });
        this.etat.set('erreur');
      },
    });
  }

  /** Publier, dépublier ou archiver. Publier est refusé par le backend s'il n'y a aucun chapitre. */
  protected changerStatut(ligne: Ligne, statut: StatutCours): void {
    this.enCours.set(ligne.id);
    this.service.changerStatut(ligne.id, statut).subscribe({
      next: (cours) => {
        this.lignes.update((l) => l.map((x) => (x.id === cours.id ? versLigne(cours) : x)));
        this.enCours.set(null);
        this.snack.open(
          statut === 'PUBLIE' ? 'Cours publié' : statut === 'ARCHIVE' ? 'Cours archivé' : 'Cours repassé en brouillon',
          'OK',
          { duration: 3000 },
        );
      },
      error: (err) => {
        this.enCours.set(null);
        this.snack.open(errorMessage(err, 'Changement de statut impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  protected demanderSuppression(ligne: Ligne): void {
    this.aSupprimer.set(ligne);
    this.ref = this.dialog.open(this.confirmation(), { width: '440px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected supprimer(): void {
    const ligne = this.aSupprimer();
    if (!ligne) return;
    this.suppression.set(true);
    this.service.delete(ligne.id).subscribe({
      next: () => {
        this.lignes.update((l) => l.filter((x) => x.id !== ligne.id));
        this.suppression.set(false);
        this.ref?.close();
        this.snack.open('Cours supprimé', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.suppression.set(false);
        this.snack.open(errorMessage(err, 'Suppression impossible'), 'OK', { duration: 5000 });
      },
    });
  }
}

function versLigne(c: Cours): Ligne {
  return {
    id: c.id,
    cours: c,
    titre: c.titre,
    categorie: libelleMatiere(c.categorie, c.categoriePrecision) || VIDE,
    niveau: c.niveau ? LIBELLE_NIVEAU[c.niveau] : VIDE,
    statut: c.statut,
    prix: libellePrix(c.prix),
    inscrits: 0,
    completion: VIDE,
    maj: formatDate(c.dateModification ?? c.dateCreation),
  };
}
