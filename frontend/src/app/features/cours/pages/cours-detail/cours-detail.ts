import { AsyncPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, PageHeader, Skeleton } from '../../../../shared/ui';
import { FORMATEUR_DEFAUT, LIBELLE_NIVEAU, formatDate, formatDuree, libelleMatiere, libellePrix } from '../../models/cours.constantes';
import { Chapitre, Cours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Etat = 'chargement' | 'ok' | 'introuvable';

/**
 * Fiche d'un cours (avant de le suivre) : présentation, programme, formateur et avis.
 * Les données viennent de cours-service (findById) et le nom du formateur de user-service.
 * Image, prix, chapitres et durée sont réels ; la note et les inscrits ne sont pas encore fournis (pas d'étoiles, 0 inscrit).
 * L'inscription réelle viendra plus tard.
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
  protected readonly niveau = computed(() => {
    const n = this.cours()?.niveau;
    return n ? LIBELLE_NIVEAU[n] : '';
  });
  protected readonly maj = computed(() => formatDate(this.cours()?.dateModification ?? this.cours()?.dateCreation));

  protected readonly cta = computed(() => {
    if (this.espace !== 'etudiant') {
      return { label: 'S’inscrire gratuitement', link: ['/register'] as unknown[], params: { cours: this.id() } };
    }
    return { label: 'Commencer le cours', link: ['/app/cours', this.id(), 'lecture'] as unknown[], params: {} };
  });

  protected readonly apprendre = [
    'Comprendre les notions clés du cours avec des exemples concrets',
    'Appliquer ce que tu apprends dans des exercices et des quiz',
    'Savoir quand utiliser chaque technique dans un vrai projet',
    'Repérer les erreurs fréquentes et les corriger',
  ];

  ngOnInit(): void {
    this.service.findById(this.id()).subscribe({
      next: (c) => {
        this.cours.set(c);
        this.etat.set('ok');
        this.formateurs.nom(c.enseignantId).subscribe((nom) => this.formateur.set(nom));
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
