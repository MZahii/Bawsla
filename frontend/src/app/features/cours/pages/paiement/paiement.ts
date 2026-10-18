import { AsyncPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { FORMATEUR_DEFAUT, formatDuree, libellePrix } from '../../models/cours.constantes';
import { Cours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Etat = 'chargement' | 'ok' | 'introuvable' | 'inscrit';

/**
 * Accès à un cours depuis le catalogue. Le cours vient de cours-service (findById).
 * Cours gratuit : inscription réelle (sInscrire). Cours payant : cours-service n'a pas encore de paiement en ligne,
 * la page le dit clairement et ne simule aucun paiement.
 * À brancher plus tard : POST /api/cours/{id}/commandes et un prestataire de paiement côté backend.
 */
@Component({
  selector: 'app-paiement',
  imports: [AsyncPipe, RouterLink, MatIconModule, MatSnackBarModule, BwButton, EmptyState, PageHeader, Skeleton, CoursImage],
  templateUrl: './paiement.html',
  styleUrl: './paiement.scss',
})
export class Paiement implements OnInit {
  private readonly service = inject(CoursService);
  private readonly formateurs = inject(FormateurService);
  private readonly snack = inject(MatSnackBar);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours | null>(null);
  protected readonly formateur = signal(FORMATEUR_DEFAUT);
  protected readonly action = signal(false);
  protected readonly payant = computed(() => (this.cours()?.prix ?? 0) > 0);
  protected readonly prix = computed(() => libellePrix(this.cours()?.prix));
  protected readonly duree = computed(() => formatDuree(this.cours()?.dureeTotaleMinutes));

  ngOnInit(): void {
    this.service.findById(this.id()).subscribe({
      next: (c) => {
        this.cours.set(c);
        this.etat.set('ok');
        this.formateurs.nom(c.enseignantId).subscribe((nom) => this.formateur.set(nom));
      },
      error: (err) => {
        this.etat.set('introuvable');
        if ((err as { status?: number })?.status !== 404) {
          this.snack.open(errorMessage(err, 'Impossible de charger le cours'), 'OK', { duration: 5000 });
        }
      },
    });
  }

  /** Cours gratuit uniquement : inscription réelle (sans doublon côté serveur). */
  protected sInscrire(): void {
    const c = this.cours();
    if (!c || this.payant() || this.action()) return;
    this.action.set(true);
    this.service.sInscrire(c.id).subscribe({
      next: () => {
        this.action.set(false);
        this.etat.set('inscrit');
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Inscription impossible'), 'OK', { duration: 6000 });
      },
    });
  }
}
