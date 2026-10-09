import { Component, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';

import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { CATEGORIES, ONBOARDING } from '../../core/demo/demo-plus';
import { lireOnboarding } from '../../core/profil/onboarding';
import { RouterLink } from '@angular/router';
import { ROLE_LABELS } from '../../shared/models/user.model';
import { BwButton } from '../../shared';
import { Avatar, Dropzone, PageHeader } from '../../shared/ui';

/**
 * Profil et réglages du compte, commun aux trois espaces : informations, parcours (apprenant :
 * profil, objectif, centres d'intérêt) ou expertise (formateur), sécurité, notifications, préférences.
 * À brancher : UserService (PUT /api/users/me, changement de mot de passe, préférences).
 */
@Component({
  selector: 'app-profile',
  imports: [
    FormsModule, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatRadioModule,
    RouterLink, MatIconModule, BwButton, Avatar, Dropzone, PageHeader,
  ],
  templateUrl: './profile.html',
  styleUrl: './profile.scss',
})
export class Profile {
  private readonly snack = inject(MatSnackBar);
  protected readonly auth = inject(AuthService);
  protected readonly theme = inject(ThemeService);
  protected readonly roles = ROLE_LABELS;

  protected readonly user = computed(() => this.auth.currentUser());
  protected readonly nom = computed(() => {
    const u = this.user();
    return u ? `${u.prenom} ${u.nom}`.trim() || u.email : 'Utilisateur';
  });
  protected readonly etudiant = computed(() => this.user()?.role === 'ETUDIANT');

  /** Réponses du questionnaire d'accueil (apprenant) : profil, objectif, domaines, temps. */
  protected readonly parcours = lireOnboarding();
  protected readonly profilLibelle = ONBOARDING.profils.find((p) => p.code === this.parcours?.profil)?.titre ?? 'Non renseigné';
  protected readonly profils = ONBOARDING.profils;
  protected readonly objectifs = ONBOARDING.objectifs;
  protected readonly domainesListe = CATEGORIES.map((c) => c.nom);

  protected readonly sessions = [
    { appareil: 'Chrome sur Windows', lieu: 'Tunis', quand: 'Session actuelle', icone: 'computer', actuelle: true },
    { appareil: 'Safari sur iPhone', lieu: 'Ariana', quand: 'il y a 2 jours', icone: 'smartphone', actuelle: false },
  ];

  protected enregistrer(quoi: string): void {
    this.snack.open(`${quoi} enregistré(es)`, 'OK', { duration: 2500 });
  }
}
