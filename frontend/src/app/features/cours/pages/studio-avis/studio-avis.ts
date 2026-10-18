import { AsyncPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { LIBELLE_STATUT } from '../../models/cours.constantes';
import { Cours, StatutCours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';

type Etat = 'chargement' | 'ok' | 'erreur';

/**
 * Studio : les avis des apprenants sur les cours du formateur.
 * cours-service ne stocke pas encore d'avis : la page liste les vrais cours du formateur, chacun « Pas encore d'avis ».
 * À brancher : CoursService.avis(coursId) et POST /api/cours/avis/{id}/reponse (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-studio-avis',
  imports: [AsyncPipe, RouterLink, MatSnackBarModule, BwButton, EmptyState, PageHeader, Skeleton, CoursImage],
  templateUrl: './studio-avis.html',
  styleUrl: './studio-avis.scss',
})
export class StudioAvis {
  private readonly service = inject(CoursService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours[]>([]);
  protected readonly libelle = (s: StatutCours): string => LIBELLE_STATUT[s];

  constructor() {
    this.charger();
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.service.findAll().subscribe({
      next: (liste) => {
        const moi = this.auth.currentUser();
        this.cours.set(this.auth.role() === 'ENSEIGNANT' ? liste.filter((c) => c.enseignantId === moi?.id) : liste);
        this.etat.set('ok');
      },
      error: (err) => {
        this.snack.open(errorMessage(err, 'Impossible de charger tes cours'), 'OK', { duration: 5000 });
        this.etat.set('erreur');
      },
    });
  }
}
