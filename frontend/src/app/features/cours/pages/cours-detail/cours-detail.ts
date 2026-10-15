import { AsyncPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, PageHeader, Skeleton } from '../../../../shared/ui';
import { FORMATEUR_DEFAUT, LIBELLE_NIVEAU, formatDate, formatDuree, libelleMatiere, libellePrix, pourcent } from '../../models/cours.constantes';
import { Chapitre, Cours, Inscription } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Etat = 'chargement' | 'ok' | 'introuvable';

/**
 * Fiche d'un cours (avant de le suivre) : présentation, programme, formateur et avis.
 * Les données viennent de cours-service (findById) et le nom du formateur de user-service.
 * Image, prix, chapitres, durée et nombre d'inscrits sont réels ; la note n'est pas encore fournie (pas d'étoiles).
 * Un apprenant connecté s'inscrit ici (gratuit ou payant : le paiement n'existe pas encore), puis commence, continue ou revoit le cours ;
 * un visiteur garde le lien vers l'inscription au site.
 */
@Component({
  selector: 'app-cours-detail',
  imports: [RouterLink, AsyncPipe, MatIconModule, MatTabsModule, MatSnackBarModule, BwButton, EmptyState, Avatar, PageHeader, Skeleton, CoursImage],
  templateUrl: './cours-detail.html',
  styleUrl: './cours-detail.scss',
})
export class CoursDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly service = inject(CoursService);
  private readonly formateurs = inject(FormateurService);
  private readonly snack = inject(MatSnackBar);
  private readonly auth = inject(AuthService);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly espace = (this.route.snapshot.data['espace'] as string) ?? 'public';
  protected readonly base = this.espace === 'etudiant' ? '/app/explorer' : '/catalogue';

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours | null>(null);
  protected readonly formateur = signal(FORMATEUR_DEFAUT);

  protected readonly chapitres = signal<Chapitre[]>([]);
  protected readonly prix = computed(() => libellePrix(this.cours()?.prix));
  /** Matière du cours, avec sa précision pour « Autre ». */
  protected readonly matiere = computed(() => libelleMatiere(this.cours()?.categorie, this.cours()?.categoriePrecision) || 'Cours');
  protected readonly payant = computed(() => (this.cours()?.prix ?? 0) > 0);
  protected readonly duree = computed(() => formatDuree(this.cours()?.dureeTotaleMinutes));
  protected readonly formatDuree = formatDuree;
  protected readonly pourcent = pourcent;
  protected readonly niveau = computed(() => {
    const n = this.cours()?.niveau;
    return n ? LIBELLE_NIVEAU[n] : '';
  });
  protected readonly maj = computed(() => formatDate(this.cours()?.dateModification ?? this.cours()?.dateCreation));

  /** Mon inscription à ce cours (apprenant connecté) ; null si je ne suis pas inscrit ou si je ne suis pas apprenant. */
  protected readonly inscription = signal<Inscription | null>(null);
  protected readonly action = signal(false);
  /** Seul un apprenant connecté, dans l'espace « Explorer », peut s'inscrire. */
  protected readonly peutSInscrire = computed(() => this.espace === 'etudiant' && this.auth.role() === 'ETUDIANT');

  protected readonly cta = computed(() => {
    if (this.espace !== 'etudiant') {
      return { label: 'S’inscrire gratuitement', link: ['/register'] as unknown[], params: { cours: this.id() } };
    }
    const i = this.inscription();
    const label = !i || (i.progression === 0 && !i.termine) ? 'Commencer le cours' : i.termine ? 'Revoir le cours' : 'Continuer le cours';
    return { label, link: ['/app/cours', this.id(), 'lecture'] as unknown[], params: {} };
  });

  protected readonly apprendre = [
    'Comprendre les notions clés du cours avec des exemples concrets',
    'Appliquer ce que tu apprends dans des exercices et des quiz',
    'Savoir quand utiliser chaque technique dans un vrai projet',
    'Repérer les erreurs fréquentes et les corriger',
  ];

  protected sInscrire(): void {
    const c = this.cours();
    if (!c || this.action()) return;
    this.action.set(true);
    this.service.sInscrire(c.id).subscribe({
      next: (i) => {
        this.action.set(false);
        this.inscription.set(i);
        this.cours.set({ ...c, nbInscrits: (c.nbInscrits ?? 0) + 1 });
        this.snack.open('Tu es inscrit à ce cours.', 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Inscription impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  /** Définitif : la progression est effacée avec l'inscription, d'où la confirmation. */
  protected seDesinscrire(): void {
    const c = this.cours();
    if (!c || this.action()) return;
    if (!window.confirm('Te désinscrire de ce cours ? Ta progression sera effacée définitivement.')) return;
    this.action.set(true);
    this.service.seDesinscrire(c.id).subscribe({
      next: () => {
        this.action.set(false);
        this.inscription.set(null);
        this.cours.set({ ...c, nbInscrits: Math.max(0, (c.nbInscrits ?? 1) - 1) });
        this.snack.open('Inscription supprimée.', 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Désinscription impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  private chargerInscription(coursId: number): void {
    this.service.mesInscriptions().subscribe({
      next: (liste) => this.inscription.set(liste.find((i) => i.coursId === coursId) ?? null),
      error: () => undefined, // sans cette information, « S'inscrire » reste proposé : le serveur répond de façon idempotente
    });
  }

  ngOnInit(): void {
    this.service.findById(this.id()).subscribe({
      next: (c) => {
        this.cours.set(c);
        this.etat.set('ok');
        this.formateurs.nom(c.enseignantId).subscribe((nom) => this.formateur.set(nom));
        if (this.peutSInscrire()) this.chargerInscription(c.id);
        this.service.chapitres(c.id).subscribe({
          next: (l) => this.chapitres.set(l),
          error: (err) => this.snack.open(errorMessage(err, 'Impossible de charger le programme'), 'OK', { duration: 5000 }),
        });
      },
      error: (err) => {
        this.etat.set('introuvable');
        if ((err as { status?: number })?.status !== 404) {
          this.snack.open(errorMessage(err, 'Impossible de charger le cours'), 'OK', { duration: 5000 });
        }
      },
    });
  }
}
