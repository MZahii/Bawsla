import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { errorMessage } from '../../../shared/models/api-response.model';

/** Mêmes règles que le backend (dto/MotDePasse) : 8 à 72 caractères, au moins une lettre et un chiffre. */
const MOT_DE_PASSE = /^(?=.*\p{L})(?=.*\d).*$/u;

function confirmationIdentique(group: AbstractControl): ValidationErrors | null {
  const { motDePasse, confirmation } = group.value as { motDePasse: string; confirmation: string };
  return confirmation && motDePasse !== confirmation ? { differents: true } : null;
}

/** Étape 2 : depuis le lien reçu par email (?token=…), choix du nouveau mot de passe. */
@Component({
  selector: 'app-reinitialiser',
  imports: [
    ReactiveFormsModule, RouterLink, Logo, BwButton,
    MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
  ],
  templateUrl: './reinitialiser.html',
  styleUrls: ['../auth-page.scss', '../recuperation.scss'],
})
export class Reinitialiser {
  private readonly auth = inject(AuthService);

  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly lienInvalide = signal(!this.token);
  protected readonly termine = signal(false);
  protected readonly hidePassword = signal(true);

  protected readonly form = inject(FormBuilder).nonNullable.group(
    {
      motDePasse: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72), Validators.pattern(MOT_DE_PASSE)]],
      confirmation: ['', Validators.required],
    },
    { validators: confirmationIdentique },
  );

  private readonly motDePasse = toSignal(this.form.controls.motDePasse.valueChanges, { initialValue: '' });

  /** Robustesse indicative : 0 (vide) à 4, même calcul qu'à l'inscription. */
  protected readonly force = computed(() => {
    const mdp = this.motDePasse();
    if (!mdp) return 0;
    if (mdp.length < 8) return 1;
    let score = 2;
    if (mdp.length >= 12) score++;
    if (/[a-z]/.test(mdp) && /[A-Z]/.test(mdp) && /\d/.test(mdp)) score++;
    else if (/[^A-Za-z0-9]/.test(mdp)) score++;
    return Math.min(score, 4);
  });
  protected readonly libelleForce = computed(() => ['', 'Trop court', 'Correct', 'Bon', 'Solide'][this.force()]);

  protected submit(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.reinitialiserMotDePasse(this.token, this.form.getRawValue().motDePasse).subscribe({
      next: () => {
        this.termine.set(true);
        this.loading.set(false);
      },
      error: (err) => {
        // 400 sur le jeton : lien expiré ou déjà utilisé, on propose d'en demander un autre.
        if (err?.status === 400 && !err?.error?.details) {
          this.lienInvalide.set(true);
        } else {
          this.error.set(errorMessage(err, 'Modification impossible pour le moment. Réessaie dans un instant.'));
        }
        this.loading.set(false);
      },
    });
  }
}
