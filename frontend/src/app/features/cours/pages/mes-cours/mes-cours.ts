import { AsyncPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, ProgressRing, Skeleton } from '../../../../shared/ui';
import { LIBELLE_NIVEAU, formatDuree, libelleMatiere } from '../../models/cours.constantes';
import { Cours, Inscription } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';

interface Ligne {
  cours: Cours;
  inscription: Inscription;
  /** Progression en pourcentage entier, pour l'anneau. */
  pourcentage: number;
  matiere: string;
  niveau: string;
}

type Etat = 'chargement' | 'ok' | 'erreur';

/**
 * « Mes cours » de l'apprenant : les cours auxquels il est inscrit, en cours ou terminés (tous les chapitres terminés).
 * Les inscriptions viennent de cours-service (mes-inscriptions) ; un cours qui n'est plus publié n'apparaît plus ici.
 */
@Component({
  selector: 'app-mes-cours',
  imports: [RouterLink, AsyncPipe, MatTabsModule, MatIconModule, MatSnackBarModule, BwButton, EmptyState, PageHeader, ProgressRing, Skeleton, CoursImage],
  templateUrl: './mes-cours.html',
  styleUrl: './mes-cours.scss',
})
export class MesCours implements OnInit {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);

  protected readonly etat = signal<Etat>('chargement');
  protected readonly lignes = signal<Ligne[]>([]);
  protected readonly enCours = computed(() => this.lignes().filter((l) => !l.inscription.termine));
  protected readonly termines = computed(() => this.lignes().filter((l) => l.inscription.termine));
  protected readonly duree = formatDuree;

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.etat.set('chargement');
    forkJoin({ inscriptions: this.service.mesInscriptions(), cours: this.service.findAll() }).subscribe({
      next: ({ inscriptions, cours }) => {
        const parId = new Map(cours.map((c) => [c.id, c]));
        this.lignes.set(
          inscriptions.flatMap((i) => {
            const c = parId.get(i.coursId);
            return c
              ? [{ cours: c, inscription: i, pourcentage: Math.round(i.progression * 100), matiere: libelleMatiere(c.categorie, c.categoriePrecision),
                  niveau: c.niveau ? LIBELLE_NIVEAU[c.niveau] : '' }]
              : [];
          }),
        );
        this.etat.set('ok');
      },
      error: (err) => {
        this.snack.open(errorMessage(err, 'Impossible de charger tes cours'), 'OK', { duration: 5000 });
        this.etat.set('erreur');
      },
    });
  }
}
