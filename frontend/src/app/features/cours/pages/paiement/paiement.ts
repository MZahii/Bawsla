import { AsyncPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader, Skeleton } from '../../../../shared/ui';
import { FORMATEUR_DEFAUT, formatDuree, libellePrix } from '../../models/cours.constantes';
import { Commande, Cours } from '../../models/cours.model';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';
import { FormateurService } from '../../services/formateur.service';

type Etat = 'chargement' | 'ok' | 'introuvable' | 'inscrit';

/**
 * Accès à un cours depuis le catalogue. Cours gratuit : inscription directe (sInscrire).
 * Cours payant : paiement <b>simulé</b> (POST /api/cours/{id}/commandes) avec une carte de test ; aucune somme n'est débitée,
 * le serveur vérifie la carte (Luhn, expiration), garde les 4 derniers chiffres et inscrit l'apprenant.
 */
@Component({
  selector: 'app-paiement',
  imports: [
    AsyncPipe, RouterLink, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatIconModule, MatSnackBarModule,
    BwButton, EmptyState, PageHeader, Skeleton, CoursImage,
  ],
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
  protected readonly commande = signal<Commande | null>(null);
  protected readonly payant = computed(() => (this.cours()?.prix ?? 0) > 0);
  protected readonly prix = computed(() => libellePrix(this.cours()?.prix));
  protected readonly duree = computed(() => formatDuree(this.cours()?.dureeTotaleMinutes));

  protected readonly form = inject(NonNullableFormBuilder).group({
    numeroCarte: ['', [Validators.required, Validators.pattern(/^[0-9 ]{12,23}$/)]],
    expiration: ['', [Validators.required, Validators.pattern(/^(0[1-9]|1[0-2])\/[0-9]{2}$/)]],
    cryptogramme: ['', [Validators.required, Validators.pattern(/^[0-9]{3,4}$/)]],
  });

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

  /** Remplit le formulaire avec une carte de test (accordée ou refusée). */
  protected carteTest(numero: string): void {
    const an = (new Date().getFullYear() + 2) % 100;
    this.form.setValue({ numeroCarte: numero, expiration: `12/${String(an).padStart(2, '0')}`, cryptogramme: '123' });
  }

  /** Groupe les chiffres par 4 pendant la saisie. */
  protected formaterNumero(): void {
    const ctrl = this.form.controls.numeroCarte;
    const chiffres = ctrl.value.replace(/\D/g, '').slice(0, 19);
    const groupe = chiffres.replace(/(.{4})(?=.)/g, '$1 ');
    if (groupe !== ctrl.value) ctrl.setValue(groupe, { emitEvent: false });
  }

  /** Ajoute la barre de MM/AA pendant la saisie. */
  protected formaterExpiration(): void {
    const ctrl = this.form.controls.expiration;
    const chiffres = ctrl.value.replace(/\D/g, '').slice(0, 4);
    const valeur = chiffres.length > 2 ? `${chiffres.slice(0, 2)}/${chiffres.slice(2)}` : chiffres;
    if (valeur !== ctrl.value) ctrl.setValue(valeur, { emitEvent: false });
  }

  protected payer(): void {
    const c = this.cours();
    if (!c || !this.payant() || this.action()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.action.set(true);
    this.service.payer(c.id, this.form.getRawValue()).subscribe({
      next: (commande) => {
        this.action.set(false);
        this.commande.set(commande);
        this.form.reset();
        this.etat.set('inscrit');
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Paiement refusé'), 'OK', { duration: 6000 });
      },
    });
  }
}
