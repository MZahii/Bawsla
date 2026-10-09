import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { coursParId, libellePrix } from '../../../../core/demo/demo-data';
import { dureeTotale, formatDuree, meta } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

type Etape = 'formulaire' | 'traitement' | 'confirme';

/**
 * Paiement d'un cours payant. SIMULÉ : aucun prestataire n'est appelé et aucune carte n'est débitée.
 * À brancher : POST /api/cours/{id}/commandes (crée la commande et l'inscription), puis, plus tard,
 * un vrai prestataire de paiement côté backend. Le front ne voit jamais les données de carte en clair.
 */
@Component({
  selector: 'app-paiement',
  imports: [ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatRadioModule, MatIconModule, BwButton, EmptyState, PageHeader],
  templateUrl: './paiement.html',
  styleUrl: './paiement.scss',
})
export class Paiement {
  readonly id = input(0, { transform: numberAttribute });
  protected readonly user = inject(AuthService).currentUser;

  protected readonly cours = computed(() => coursParId(this.id()));
  protected readonly image = computed(() => meta(this.id()).image);
  protected readonly duree = computed(() => (this.cours() ? formatDuree(dureeTotale(this.cours()!)) : ''));
  protected readonly etape = signal<Etape>('formulaire');
  protected readonly code = signal('');
  protected readonly remise = signal(0);
  protected readonly codeMessage = signal<string | null>(null);
  protected readonly total = computed(() => Math.max(0, (this.cours()?.prix ?? 0) - this.remise()));
  protected readonly libelle = libellePrix;
  protected readonly reference = 'CMD-' + (10483 + Math.floor(Math.random() * 50));

  protected readonly form = inject(FormBuilder).nonNullable.group({
    moyen: ['carte'],
    titulaire: ['', Validators.required],
    numero: ['', [Validators.required, Validators.pattern(/^[\d ]{16,19}$/)]],
    expiration: ['', [Validators.required, Validators.pattern(/^(0[1-9]|1[0-2])\/\d{2}$/)]],
    cvc: ['', [Validators.required, Validators.pattern(/^\d{3}$/)]],
  });

  protected appliquerCode(): void {
    const c = this.code().trim().toUpperCase();
    if (c === 'BIENVENUE10') {
      this.remise.set(Math.round((this.cours()?.prix ?? 0) * 0.1));
      this.codeMessage.set('Code appliqué : 10 % de réduction.');
    } else {
      this.remise.set(0);
      this.codeMessage.set(c ? 'Ce code n’est pas valide.' : null);
    }
  }

  protected payer(): void {
    if (this.form.controls.moyen.value === 'carte' && this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.etape.set('traitement');
    setTimeout(() => {
      const c = this.cours();
      if (c) c.inscrit = true; // aperçu : l'inscription est créée par cours-service après le paiement
      this.etape.set('confirme');
    }, 1200);
  }
}
