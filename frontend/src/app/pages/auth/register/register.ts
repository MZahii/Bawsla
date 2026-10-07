import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { errorMessage } from '../../../shared/models/api-response.model';
import { RegisterRequest } from '../../../shared/models/user.model';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule, RouterLink, Logo, BwButton,
    MatFormFieldModule, MatInputModule, MatButtonToggleModule,
  ],
  templateUrl: './register.html',
  styleUrl: '../auth-page.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Mêmes contraintes que RegisterRequest côté user-service. */
  protected readonly form = inject(FormBuilder).nonNullable.group({
    prenom: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    role: ['ETUDIANT' as RegisterRequest['role'], Validators.required],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => this.router.navigateByUrl('/'),
      error: (err) => {
        this.error.set(errorMessage(err, 'Inscription impossible'));
        this.loading.set(false);
      },
    });
  }
}
