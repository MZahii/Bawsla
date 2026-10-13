import { SelectionModel } from '@angular/cdk/collections';
import { DatePipe } from '@angular/common';
import { Component, OnInit, TemplateRef, effect, inject, signal, viewChild } from '@angular/core';
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
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { ROLE_LABELS, Role } from '../../../../shared/models/user.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { Utilisateur } from '../../models/utilisateur.model';
import { UserService } from '../../services/user.service';

/** Mêmes règles que MotDePasse côté user-service. */
const MDP_REGEX = /^(?=.*\p{L})(?=.*\d).{8,72}$/u;

/**
 * Back-office : gestion des comptes, branchée sur user-service (ADMIN uniquement).
 * Liste filtrable (rôle, statut, recherche) et triable, création, modification, activation /
 * désactivation (aussi en lot) et réinitialisation du mot de passe, qui débloque aussi le compte.
 * Le backend refuse qu'un admin se désactive lui-même ou que le dernier admin actif perde son accès.
 */
@Component({
  selector: 'app-admin-users',
  imports: [
    DatePipe, FormsModule, MatTableModule, MatSortModule, MatPaginatorModule, MatCheckboxModule, MatChipsModule, MatMenuModule,
    MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule, BwButton, EmptyState, Avatar, Kpi, PageHeader, Skeleton,
  ],
  templateUrl: './admin-users.html',
})
export class AdminUsers implements OnInit {
  private readonly api = inject(UserService);
  private readonly dialog = inject(MatDialog);
  private readonly snack = inject(MatSnackBar);
  private readonly sort = viewChild(MatSort);
  private readonly paginator = viewChild(MatPaginator);
  private readonly form = viewChild.required<TemplateRef<unknown>>('formUtilisateur');
  private readonly formMdp = viewChild.required<TemplateRef<unknown>>('formMotDePasse');
  private ref?: MatDialogRef<unknown>;

  protected readonly moi = inject(AuthService).currentUser()?.id ?? null;
  protected readonly roles = ROLE_LABELS;
  protected readonly listeRoles: Role[] = ['ETUDIANT', 'ENSEIGNANT', 'ADMIN'];
  protected readonly colonnes = ['select', 'nom', 'role', 'statut', 'inscrit', 'connexion', 'actions'];
  protected readonly tous = signal<Utilisateur[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enCours = signal(false);
  protected readonly erreurForm = signal<string | null>(null);
  protected readonly source = new MatTableDataSource<Utilisateur>([]);
  protected readonly selection = new SelectionModel<Utilisateur>(true, [], true, (a, b) => a.id === b.id);
  protected readonly role = signal<Role | null>(null);
  protected readonly statut = signal<'tous' | 'actif' | 'inactif'>('tous');
  protected readonly q = signal('');

  protected brouillon = this.vide();
  protected edition: Utilisateur | null = null;
  protected cibleMdp: Utilisateur | null = null;
  protected nouveauMdp = '';

  constructor() {
    this.source.sortingDataAccessor = (u, col) => (col === 'nom' ? this.nomComplet(u).toLowerCase() : String(u[col as keyof Utilisateur] ?? ''));
    effect(() => {
      this.appliquerFiltres();
      const so = this.sort();
      const p = this.paginator();
      if (so) this.source.sort = so;
      if (p) this.source.paginator = p;
    });
  }

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.findAll().subscribe({
      next: (liste) => {
        this.majTous(() => liste);
        this.selection.clear();
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger les comptes'));
        this.chargement.set(false);
      },
    });
  }

  /**
   * Recalcule les lignes affichées. Appelée par l'effet (filtres) ET directement après chaque réponse HTTP :
   * en zoneless, attendre l'effet laisse la table afficher l'ancienne ligne jusqu'au rendu suivant.
   */
  private appliquerFiltres(): void {
    const r = this.role();
    const s = this.statut();
    const q = this.q().trim().toLowerCase();
    this.source.data = this.tous().filter(
      (u) => (!r || u.role === r) && (s === 'tous' || u.actif === (s === 'actif')) && `${this.nomComplet(u)} ${u.email}`.toLowerCase().includes(q),
    );
  }

  /** Met à jour la liste et la table en une fois. */
  private majTous(fn: (l: Utilisateur[]) => Utilisateur[]): void {
    this.tous.update(fn);
    this.appliquerFiltres();
  }

  protected nomComplet(u: Pick<Utilisateur, 'prenom' | 'nom'>): string {
    return `${u.prenom} ${u.nom}`.trim();
  }

  protected nombre(r: Role): number {
    return this.tous().filter((u) => u.role === r).length;
  }

  /** « aujourd’hui », « hier », « il y a 3 j »… ou « jamais ». */
  protected depuis(iso: string | null): string {
    if (!iso) return 'jamais';
    const jour = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
    const j = Math.round((jour(new Date()) - jour(new Date(iso))) / 86_400_000);
    if (j <= 0) return 'aujourd’hui';
    if (j === 1) return 'hier';
    if (j < 14) return `il y a ${j} j`;
    if (j < 60) return `il y a ${Math.floor(j / 7)} sem.`;
    return `il y a ${Math.floor(j / 30)} mois`;
  }

  protected toutSelectionne(): boolean {
    return this.source.data.length > 0 && this.selection.selected.length === this.source.data.length;
  }

  protected basculerTout(): void {
    if (this.toutSelectionne()) this.selection.clear();
    else this.selection.select(...this.source.data);
  }

  // ---------- Création / modification ----------

  protected ouvrir(u?: Utilisateur): void {
    this.edition = u ?? null;
    this.erreurForm.set(null);
    this.brouillon = u ? { prenom: u.prenom, nom: u.nom, email: u.email, role: u.role, actif: u.actif, motDePasse: '' } : this.vide();
    this.ref = this.dialog.open(this.form(), { width: '480px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected brouillonValide(): boolean {
    const b = this.brouillon;
    const base = !!b.prenom.trim() && !!b.nom.trim() && /^\S+@\S+\.\S+$/.test(b.email.trim());
    return base && (this.edition !== null || MDP_REGEX.test(b.motDePasse));
  }

  protected enregistrer(): void {
    const { motDePasse, ...b } = this.brouillon;
    const edition = this.edition;
    const appel: Observable<Utilisateur> = edition ? this.api.modifier(edition.id, b) : this.api.creer({ ...b, motDePasse });
    this.enCours.set(true);
    this.erreurForm.set(null);
    appel.subscribe({
      next: (u) => {
        this.majTous((l) => (edition ? l.map((x) => (x.id === u.id ? u : x)) : [u, ...l]));
        this.enCours.set(false);
        this.ref?.close();
        this.snack.open(edition ? 'Compte modifié' : 'Compte créé', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.erreurForm.set(errorMessage(err, 'Enregistrement impossible'));
        this.enCours.set(false);
      },
    });
  }

  // ---------- Activation ----------

  protected activer(actif: boolean, cibles: Utilisateur[] = this.selection.selected): void {
    const aTraiter = cibles.filter((u) => u.actif !== actif);
    if (aTraiter.length === 0) {
      this.selection.clear();
      return;
    }
    const appels = aTraiter.map((u) =>
      this.api.changerStatut(u.id, actif).pipe(
        map((maj) => ({ ok: true as const, maj })),
        catchError((err) => of({ ok: false as const, nom: this.nomComplet(u), message: errorMessage(err) })),
      ),
    );
    forkJoin(appels).subscribe((res) => {
      const majs = new Map(res.flatMap((r) => (r.ok ? [[r.maj.id, r.maj] as const] : [])));
      this.majTous((l) => l.map((u) => majs.get(u.id) ?? u));
      this.selection.clear();
      const echecs = res.filter((r) => !r.ok);
      const verbe = actif ? 'activé(s)' : 'désactivé(s)';
      const texte = echecs.length === 0
        ? `${majs.size} compte(s) ${verbe}`
        : `${majs.size} compte(s) ${verbe}, ${echecs.length} refusé(s) : ${echecs.map((e) => `${e.nom} (${e.message})`).join(' · ')}`;
      this.snack.open(texte, 'OK', { duration: echecs.length ? 8000 : 3000 });
    });
  }

  // ---------- Mot de passe ----------

  protected ouvrirMotDePasse(u: Utilisateur): void {
    this.cibleMdp = u;
    this.nouveauMdp = '';
    this.erreurForm.set(null);
    this.ref = this.dialog.open(this.formMdp(), { width: '440px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected mdpValide(): boolean {
    return MDP_REGEX.test(this.nouveauMdp);
  }

  protected reinitialiser(): void {
    const u = this.cibleMdp;
    if (!u) return;
    this.enCours.set(true);
    this.erreurForm.set(null);
    this.api.reinitialiserMotDePasse(u.id, this.nouveauMdp).subscribe({
      next: () => {
        this.enCours.set(false);
        this.ref?.close();
        this.snack.open(`Mot de passe de ${this.nomComplet(u)} réinitialisé. Transmets-le-lui de façon sûre.`, 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.erreurForm.set(errorMessage(err, 'Réinitialisation impossible'));
        this.enCours.set(false);
      },
    });
  }

  private vide() {
    return { prenom: '', nom: '', email: '', role: 'ETUDIANT' as Role, actif: true, motDePasse: '' };
  }
}
