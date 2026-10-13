import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { CATEGORIES, ONBOARDING } from '../../../../core/demo/demo-plus';
import { enregistrerOnboarding, lireOnboarding } from '../../../../core/profil/onboarding';
import { ThemeService } from '../../../../core/theme/theme.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { ROLE_LABELS } from '../../../../shared/models/user.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, PageHeader, Skeleton } from '../../../../shared/ui';
import { Utilisateur } from '../../models/utilisateur.model';
import { UserService } from '../../services/user.service';

/** Mêmes règles que MotDePasse côté user-service. */
const MDP_REGEX = /^(?=.*\p{L})(?=.*\d).{8,72}$/u;

/**
 * Mon compte, commun à l'apprenant (/app/profil) et au formateur (/studio/profil).
 * Informations (nom, prénom, présentation) et mot de passe : user-service (PUT /api/users/me…).
 * Parcours de l'apprenant : encore stocké sur l'appareil (core/profil/onboarding), en attendant son endpoint.
 * Profil de formateur (expertise, lien) : arrive avec les demandes formateur (checklist 1.5).
 */
@Component({
  selector: 'app-mon-compte',
  imports: [
    DatePipe, FormsModule, ReactiveFormsModule, RouterLink, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatRadioModule, MatIconModule, BwButton, EmptyState, Avatar, PageHeader, Skeleton,
  ],
  templateUrl: './mon-compte.html',
  styleUrl: './mon-compte.scss',
})
export class MonCompte implements OnInit {
  private readonly api = inject(UserService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly theme = inject(ThemeService);
  protected readonly roles = ROLE_LABELS;

  protected readonly user = signal<Utilisateur | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enregistrementInfos = signal(false);
  protected readonly erreurInfos = signal<string | null>(null);
  protected readonly enregistrementMdp = signal(false);
  protected readonly erreurMdp = signal<string | null>(null);

  protected readonly nom = computed(() => {
    const u = this.user();
    return u ? `${u.prenom} ${u.nom}`.trim() || u.email : '';
  });
  protected readonly etudiant = computed(() => this.user()?.role === 'ETUDIANT');
  /** « octobre 2026 » (l'application n'a pas de LOCALE_ID français, d'où Intl). */
  protected readonly membreDepuis = computed(() => {
    const u = this.user();
    return u ? new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric' }).format(new Date(u.dateCreation)) : '';
  });

  protected readonly infos = this.fb.group({
    prenom: ['', [Validators.required, Validators.maxLength(100)]],
    nom: ['', [Validators.required, Validators.maxLength(100)]],
    bio: ['', Validators.maxLength(500)],
  });

  protected readonly mdp = this.fb.group({
    ancien: ['', Validators.required],
    nouveau: ['', [Validators.required, Validators.pattern(MDP_REGEX)]],
    confirmation: ['', Validators.required],
  });

  /** Parcours de l'apprenant (questionnaire d'accueil), stocké sur l'appareil. */
  protected parcours = lireOnboarding() ?? { profil: null, objectif: null, domaines: [], temps: null, passe: false };
  protected readonly profils = ONBOARDING.profils;
  protected readonly objectifs = ONBOARDING.objectifs;
  protected readonly domainesListe = CATEGORIES.map((c) => c.nom);
  protected readonly profilLibelle = computed(() => ONBOARDING.profils.find((p) => p.code === this.parcours.profil)?.titre ?? 'Non renseigné');

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.api.moi().subscribe({
      next: (u) => this.appliquer(u),
      error: (err) => this.erreur.set(errorMessage(err, 'Impossible de charger ton compte')),
    });
  }

  protected enregistrerInfos(): void {
    if (this.infos.invalid) {
      this.infos.markAllAsTouched();
      return;
    }
    this.enregistrementInfos.set(true);
    this.erreurInfos.set(null);
    this.api.modifierMonProfil(this.infos.getRawValue()).subscribe({
      next: (u) => {
        this.appliquer(u);
        this.enregistrementInfos.set(false);
        // Met à jour le nom affiché dans l'en-tête (session du front).
        this.auth.refreshMe().subscribe({ error: () => undefined });
        this.snack.open('Informations enregistrées', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.erreurInfos.set(errorMessage(err, 'Enregistrement impossible'));
        this.enregistrementInfos.set(false);
      },
    });
  }

  protected confirmationDifferente(): boolean {
    const { nouveau, confirmation } = this.mdp.getRawValue();
    return confirmation.length > 0 && nouveau !== confirmation;
  }

  protected changerMotDePasse(): void {
    if (this.mdp.invalid || this.confirmationDifferente()) {
      this.mdp.markAllAsTouched();
      return;
    }
    const { ancien, nouveau } = this.mdp.getRawValue();
    this.enregistrementMdp.set(true);
    this.erreurMdp.set(null);
    this.api.changerMonMotDePasse(ancien, nouveau).subscribe({
      next: () => {
        this.enregistrementMdp.set(false);
        this.mdp.reset();
        this.snack.open('Mot de passe modifié', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.erreurMdp.set(errorMessage(err, 'Changement impossible'));
        this.enregistrementMdp.set(false);
      },
    });
  }

  protected enregistrerParcours(): void {
    enregistrerOnboarding({ ...this.parcours, passe: false });
    this.snack.open('Parcours enregistré sur cet appareil', 'OK', { duration: 2500 });
  }

  private appliquer(u: Utilisateur): void {
    this.user.set(u);
    this.infos.reset({ prenom: u.prenom, nom: u.nom, bio: u.bio ?? '' });
  }
}
