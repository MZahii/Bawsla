import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { errorMessage } from '../../../shared/models/api-response.model';
import { RegisterRequest } from '../../../shared/models/user.model';

type Choix = RegisterRequest['role'];

/**
 * Inscription ouverte à tous. On choisit d'abord « apprendre » (ETUDIANT) ou « enseigner » (ENSEIGNANT).
 * - Apprenant : compte créé, puis questionnaire d'accueil facultatif (/demarrer).
 * - Formateur : on demande en plus le domaine d'expertise et une courte présentation ; le profil
 *   est validé par l'admin avant la première publication (Admin → Demandes formateurs).
 * À brancher : expertise, bio et lien ne font pas encore partie de RegisterRequest. Les ajouter au DTO
 * (user-service, CONTRATS_API.md) ou les envoyer ensuite par POST /api/users/me/demande-formateur.
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Logo, BwButton, MatFormFieldModule, MatInputModule, MatIconModule],
  templateUrl: './register.html',
  styleUrl: '../auth-page.scss',
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly choix = signal<Choix>(this.route.snapshot.queryParamMap.get('role') === 'formateur' ? 'ENSEIGNANT' : 'ETUDIANT');
  protected readonly formateur = computed(() => this.choix() === 'ENSEIGNANT');
  /** Cours choisi avant l'inscription (?cours=3) : on y revient après. */
  private readonly coursVise = this.route.snapshot.queryParamMap.get('cours');

  protected readonly roles: { code: Choix; icone: string; titre: string; texte: string }[] = [
    { code: 'ETUDIANT', icone: 'school', titre: 'Je veux apprendre', texte: 'Suivre des cours, passer les examens, obtenir des certificats' },
    { code: 'ENSEIGNANT', icone: 'co_present', titre: 'Je veux enseigner', texte: 'Publier mes cours, gratuits ou payants, après validation' },
  ];

  /** Mêmes contraintes que RegisterRequest côté user-service, plus les champs du formateur. */
  protected readonly form = inject(FormBuilder).nonNullable.group({
    prenom: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email]],
    motDePasse: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    expertise: [''],
    bio: ['', Validators.maxLength(400)],
    lien: [''],
  });

  protected choisir(c: Choix): void {
    this.choix.set(c);
    const exp = this.form.controls.expertise;
    exp.setValidators(c === 'ENSEIGNANT' ? [Validators.required] : []);
    exp.updateValueAndValidity();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { prenom, nom, email, motDePasse } = this.form.getRawValue();
    this.auth.register({ prenom, nom, email, motDePasse, role: this.choix() }).subscribe({
      next: () => {
        if (this.formateur()) {
          try {
            localStorage.setItem('bawsla.formateur.enAttente', '1');
          } catch {
            /* ignoré */
          }
          this.router.navigateByUrl('/studio');
        } else {
          this.router.navigate(['/demarrer'], { queryParams: this.coursVise ? { cours: this.coursVise } : {} });
        }
      },
      error: (err) => {
        this.error.set(errorMessage(err, 'Inscription impossible'));
        this.loading.set(false);
      },
    });
  }
}
