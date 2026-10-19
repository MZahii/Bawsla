import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { errorMessage } from '../../../shared/models/api-response.model';

/** Étape 1 : l'utilisateur saisit son email et reçoit un lien valable 30 minutes. */
@Component({
  selector: 'app-mot-de-passe-oublie',
  imports: [ReactiveFormsModule, RouterLink, Logo, BwButton, MatFormFieldModule, MatInputModule, MatIconModule],
  templateUrl: './mot-de-passe-oublie.html',
  styleUrls: ['../auth-page.scss', '../recuperation.scss'],
})
export class MotDePasseOublie {
  private readonly auth = inject(AuthService);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  /** Email auquel le lien a été envoyé : bascule la page sur l'écran « vérifie ta boîte mail ». */
  protected readonly envoye = signal<string | null>(null);
  protected readonly renvoye = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // Pré-rempli quand on arrive depuis la connexion (?email=…)
    email: [inject(ActivatedRoute).snapshot.queryParamMap.get('email') ?? '', [Validators.required, Validators.email]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.envoyer(false);
  }

  protected renvoyer(): void {
    this.envoyer(true);
  }

  protected modifierEmail(): void {
    this.envoye.set(null);
    this.renvoye.set(false);
  }

  private envoyer(renvoi: boolean): void {
    const email = this.form.getRawValue().email.trim();
    this.loading.set(true);
    this.error.set(null);
    this.auth.motDePasseOublie(email).subscribe({
      next: () => {
        this.envoye.set(email);
        this.renvoye.set(renvoi);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Envoi impossible pour le moment. Réessaie dans un instant.'));
        this.loading.set(false);
      },
    });
  }
}
