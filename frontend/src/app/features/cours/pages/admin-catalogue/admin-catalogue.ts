import { Component, TemplateRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';

import { COURS, libellePrix } from '../../../../core/demo/demo-data';
import { CATEGORIES, LIBELLE_NIVEAU, NIVEAUX, meta } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { PageHeader, Rating } from '../../../../shared/ui';

interface Categorie {
  nom: string;
  icone: string;
  description: string;
  cours: number;
  visible: boolean;
}

/**
 * Back-office : catalogue de la plateforme. Trois onglets : catégories (CRUD avec boîte de dialogue),
 * niveaux et tous les cours (validation, mise en avant, retrait).
 * À brancher : CoursService (catégories, niveaux, cours) côté admin.
 */
@Component({
  selector: 'app-admin-catalogue',
  imports: [
    FormsModule, MatTabsModule, MatTableModule, MatIconModule, MatMenuModule, MatDialogModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatSlideToggleModule, BwButton, PageHeader, Rating,
  ],
  templateUrl: './admin-catalogue.html',
})
export class AdminCatalogue {
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly form = viewChild.required<TemplateRef<unknown>>('formCategorie');
  private ref?: MatDialogRef<unknown>;

  protected readonly categories = signal<Categorie[]>(CATEGORIES.map((c) => ({ ...c, visible: true })));
  protected readonly niveaux = NIVEAUX.map((n) => ({ ...n, cours: COURS.filter((c) => c.niveau === n.code).length }));
  protected readonly cours = COURS.map((c) => ({
    id: c.id,
    titre: c.titre,
    categorie: c.categorie,
    niveau: LIBELLE_NIVEAU[c.niveau],
    enseignant: c.enseignant,
    prix: libellePrix(c.prix),
    image: meta(c.id).image,
    statut: meta(c.id).statut,
    inscrits: meta(c.id).inscrits,
    note: meta(c.id).note,
    vedette: c.id === 1 || c.id === 3,
  }));
  protected readonly icones = ['database', 'dns', 'web', 'deployed_code', 'build', 'psychology', 'security', 'terminal', 'school'];
  protected readonly colCat = ['icone', 'nom', 'cours', 'visible', 'actions'];
  protected readonly colCours = ['titre', 'enseignant', 'niveau', 'prix', 'statut', 'inscrits', 'note', 'vedette', 'actions'];

  protected brouillon: Categorie = this.vide();
  protected edition: string | null = null;

  private vide(): Categorie {
    return { nom: '', icone: 'school', description: '', cours: 0, visible: true };
  }

  protected ouvrir(c?: Categorie): void {
    this.edition = c?.nom ?? null;
    this.brouillon = c ? { ...c } : this.vide();
    this.ref = this.dialog.open(this.form(), { width: '480px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected enregistrer(): void {
    const b = this.brouillon;
    if (!b.nom.trim()) return;
    this.categories.update((list) =>
      this.edition ? list.map((c) => (c.nom === this.edition ? b : c)) : [...list, b],
    );
    this.ref?.close();
    this.snack.open(this.edition ? 'Catégorie modifiée' : 'Catégorie créée', 'OK', { duration: 2500 });
  }

  protected supprimer(c: Categorie): void {
    this.categories.update((list) => list.filter((x) => x !== c));
    this.snack.open(`« ${c.nom} » supprimée`, 'Annuler', { duration: 4000 });
  }

  protected basculer(c: Categorie): void {
    this.categories.update((list) => list.map((x) => (x === c ? { ...x, visible: !x.visible } : x)));
  }
}
