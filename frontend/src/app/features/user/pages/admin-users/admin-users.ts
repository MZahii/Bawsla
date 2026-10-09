import { SelectionModel } from '@angular/cdk/collections';
import { Component, TemplateRef, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';

import { UTILISATEURS_ADMIN } from '../../../../core/demo/demo-plus';
import { ROLE_LABELS, Role } from '../../../../shared/models/user.model';
import { BwButton } from '../../../../shared';
import { Avatar, Kpi, PageHeader } from '../../../../shared/ui';

type Utilisateur = (typeof UTILISATEURS_ADMIN)[number] & { role: Role };

/**
 * Back-office : gestion des comptes. Tableau dense avec sélection multiple, actions groupées,
 * filtres par rôle et par statut, profil de l'apprenant (étudiant, professionnel…) ou expertise du
 * formateur, création et modification dans une boîte de dialogue.
 * À brancher : UserService (GET/POST/PUT /api/users, activation, changement de rôle).
 */
@Component({
  selector: 'app-admin-users',
  imports: [
    FormsModule, MatTableModule, MatSortModule, MatPaginatorModule, MatCheckboxModule, MatChipsModule, MatMenuModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule, BwButton, Avatar, Kpi, PageHeader,
  ],
  templateUrl: './admin-users.html',
})
export class AdminUsers {
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);
  private readonly form = viewChild.required<TemplateRef<unknown>>('formUtilisateur');
  private ref?: MatDialogRef<unknown>;

  protected readonly roles = ROLE_LABELS;
  protected readonly listeRoles: Role[] = ['ETUDIANT', 'ENSEIGNANT', 'ADMIN'];
  protected readonly colonnes = ['select', 'nom', 'role', 'profil', 'statut', 'inscrit', 'connexion', 'actions'];
  protected readonly tous = signal<Utilisateur[]>(UTILISATEURS_ADMIN as Utilisateur[]);
  protected readonly source = new MatTableDataSource<Utilisateur>([]);
  protected readonly selection = new SelectionModel<Utilisateur>(true, []);
  protected readonly role = signal<Role | null>(null);
  protected readonly statut = signal<'tous' | 'actif' | 'inactif'>('tous');
  protected readonly q = signal('');

  protected brouillon = { nom: '', email: '', role: 'ETUDIANT' as Role, actif: true };
  protected edition: Utilisateur | null = null;

  constructor() {
    effect(() => {
      const r = this.role();
      const s = this.statut();
      const q = this.q().toLowerCase();
      this.source.data = this.tous().filter(
        (u) => (!r || u.role === r) && (s === 'tous' || u.actif === (s === 'actif')) && `${u.nom} ${u.email}`.toLowerCase().includes(q),
      );
      const so = this.sort();
      const p = this.paginator();
      if (so) this.source.sort = so;
      if (p) this.source.paginator = p;
    });
  }

  protected nombre(r: Role): number {
    return this.tous().filter((u) => u.role === r).length;
  }

  protected toutSelectionne(): boolean {
    return this.source.data.length > 0 && this.selection.selected.length === this.source.data.length;
  }

  protected basculerTout(): void {
    if (this.toutSelectionne()) this.selection.clear();
    else this.selection.select(...this.source.data);
  }

  protected ouvrir(u?: Utilisateur): void {
    this.edition = u ?? null;
    this.brouillon = u ? { nom: u.nom, email: u.email, role: u.role, actif: u.actif } : { nom: '', email: '', role: 'ETUDIANT', actif: true };
    this.ref = this.dialog.open(this.form(), { width: '480px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected enregistrer(): void {
    const b = this.brouillon;
    if (this.edition) {
      const id = this.edition.id;
      this.tous.update((l) => l.map((u) => (u.id === id ? { ...u, ...b } : u)));
    } else {
      this.tous.update((l) => [{ id: Date.now(), ...b, profil: '', inscrit: '08/10/2026', derniereConnexion: 'jamais' } as Utilisateur, ...l]);
    }
    this.ref?.close();
    this.snack.open(this.edition ? 'Compte modifié' : 'Compte créé, un email d’activation a été envoyé', 'OK', { duration: 3000 });
  }

  protected activer(actif: boolean, cibles: Utilisateur[] = this.selection.selected): void {
    const ids = new Set(cibles.map((u) => u.id));
    this.tous.update((l) => l.map((u) => (ids.has(u.id) ? { ...u, actif } : u)));
    this.snack.open(`${ids.size} compte(s) ${actif ? 'activé(s)' : 'désactivé(s)'}`, 'Annuler', { duration: 3000 });
    this.selection.clear();
  }
}
