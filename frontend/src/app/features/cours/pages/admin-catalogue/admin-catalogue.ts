import { AsyncPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { CATEGORIES, LIBELLE_NIVEAU, LIBELLE_STATUT, NIVEAUX, VIDE, libelleMatiere, libellePrix } from '../../models/cours.constantes';
import { Cours, StatutCours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Etat = 'chargement' | 'ok' | 'erreur';

/**
 * Back-office : catalogue de la plateforme, lu depuis cours-service (tous les cours pour l'ADMIN).
 * Trois onglets : matières (liste de référence commune avec le module Quiz, non modifiable ici), niveaux et tous les cours.
 * Publier ou retirer un cours du catalogue passe par le backend (changerStatut). La note n'est pas encore fournie.
 */
@Component({
  selector: 'app-admin-catalogue',
  imports: [AsyncPipe, RouterLink, MatTabsModule, MatTableModule, MatIconModule, MatMenuModule, MatSnackBarModule, EmptyState, PageHeader, Skeleton, CoursImage],
  templateUrl: './admin-catalogue.html',
})
export class AdminCatalogue {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);
  protected readonly formateurs = inject(FormateurService);

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours[]>([]);
  protected readonly enCours = signal<number | null>(null);

  protected readonly categories = computed(() =>
    CATEGORIES.map((c) => ({ ...c, cours: this.cours().filter((x) => x.categorie === c.nom).length })),
  );
  protected readonly niveaux = computed(() =>
    NIVEAUX.map((n) => ({ ...n, cours: this.cours().filter((x) => x.niveau === n.code).length })),
  );

  protected readonly colCat = ['icone', 'nom', 'cours'];
  protected readonly colCours = ['titre', 'enseignant', 'niveau', 'prix', 'statut', 'inscrits', 'actions'];
  protected readonly matiere = (c: Cours): string => libelleMatiere(c.categorie, c.categoriePrecision) || VIDE;
  protected readonly niveau = (c: Cours): string => (c.niveau ? LIBELLE_NIVEAU[c.niveau] : VIDE);
  protected readonly prix = (c: Cours): string => libellePrix(c.prix);
  protected readonly libelleStatut = (s: StatutCours): string => LIBELLE_STATUT[s];

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.service.findAll().subscribe({
      next: (liste) => {
        this.cours.set(liste);
        this.etat.set('ok');
      },
      error: (err) => {
        this.snack.open(errorMessage(err, 'Impossible de charger le catalogue'), 'OK', { duration: 5000 });
        this.etat.set('erreur');
      },
    });
  }

  /** Publier (refusé par le backend sans chapitre) ou retirer du catalogue (retour en brouillon). */
  protected changerStatut(c: Cours, statut: StatutCours): void {
    this.enCours.set(c.id);
    this.service.changerStatut(c.id, statut).subscribe({
      next: (maj) => {
        this.cours.update((l) => l.map((x) => (x.id === maj.id ? maj : x)));
        this.enCours.set(null);
        this.snack.open(statut === 'PUBLIE' ? 'Cours publié' : 'Cours retiré du catalogue', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.enCours.set(null);
        this.snack.open(errorMessage(err, 'Changement de statut impossible'), 'OK', { duration: 6000 });
      },
    });
  }
}
