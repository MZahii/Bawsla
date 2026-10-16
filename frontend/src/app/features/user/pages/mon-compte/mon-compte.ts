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
import { of, switchMap } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { ONBOARDING } from '../../../../core/demo/demo-plus';
import { ThemeService } from '../../../../core/theme/theme.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { ROLE_LABELS } from '../../../../shared/models/user.model';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { Avatar, Dropzone, PageHeader, Skeleton } from '../../../../shared/ui';
import { SuggestionParcoursIa } from '../../models/ia-generative.model';
import { DOMAINES_PARCOURS } from '../../models/parcours.constantes';
import { DemandeFormateur, Parcours, Utilisateur } from '../../models/utilisateur.model';
import { AvatarService } from '../../services/avatar.service';
import { IaGenerativeService } from '../../services/ia-generative.service';
import { UserService } from '../../services/user.service';

/** Mêmes règles que MotDePasse côté user-service. */
const MDP_REGEX = /^(?=.*\p{L})(?=.*\d).{8,72}$/u;
const TYPES_PHOTO = ['image/png', 'image/jpeg', 'image/webp'];
const PHOTO_MAX = 2 * 1024 * 1024;

/**
 * Mon compte, commun à l'apprenant (/app/profil) et au formateur (/studio/profil), branché sur user-service :
 * informations et photo, parcours (apprenant), demande pour devenir formateur, mot de passe, suppression du
 * compte (désactivation) et thème.
 */
@Component({
  selector: 'app-mon-compte',
  imports: [
    DatePipe, FormsModule, ReactiveFormsModule, RouterLink, MatTabsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatRadioModule, MatIconModule, AiBox, BwButton, EmptyState, Avatar, Dropzone, PageHeader, Skeleton,
  ],
  templateUrl: './mon-compte.html',
  styleUrl: './mon-compte.scss',
})
export class MonCompte implements OnInit {
  private readonly api = inject(UserService);
  private readonly avatars = inject(AvatarService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly theme = inject(ThemeService);
  protected readonly roles = ROLE_LABELS;

  protected readonly user = signal<Utilisateur | null>(null);
  protected readonly photo = signal<string | undefined>(undefined);
  protected readonly erreur = signal<string | null>(null);
  protected readonly enregistrementInfos = signal(false);
  protected readonly erreurInfos = signal<string | null>(null);
  protected readonly envoiPhoto = signal(false);
  protected readonly erreurPhoto = signal<string | null>(null);
  protected readonly cleDropzone = signal(0);
  protected readonly enregistrementMdp = signal(false);
  protected readonly erreurMdp = signal<string | null>(null);

  protected readonly nom = computed(() => {
    const u = this.user();
    return u ? `${u.prenom} ${u.nom}`.trim() || u.email : '';
  });
  protected readonly etudiant = computed(() => this.user()?.role === 'ETUDIANT');
  protected readonly formateur = computed(() => this.user()?.role === 'ENSEIGNANT');
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

  // ---------- Parcours (apprenant) ----------
  protected parcours: Parcours = { profil: null, objectif: null, domaines: [], temps: null };
  protected readonly enregistrementParcours = signal(false);
  protected readonly profils = ONBOARDING.profils;
  protected readonly objectifs = ONBOARDING.objectifs;
  protected readonly tempsListe = ONBOARDING.temps;
  /** Les matières des cours, plus un ancien domaine encore enregistré (pour pouvoir le retirer). */
  protected domainesListe: string[] = DOMAINES_PARCOURS;

  // IA générative : domaines et objectif suggérés à partir d'une phrase (rien n'est enregistré sans « Enregistrer »).
  private readonly iaGen = inject(IaGenerativeService);
  protected texteSuggestion = '';
  protected readonly suggestion = signal<SuggestionParcoursIa | null>(null);
  protected readonly suggestionEnCours = signal(false);
  protected readonly erreurSuggestion = signal<string | null>(null);
  protected readonly profilLibelle = signal('Non renseigné');

  // ---------- Demande formateur ----------
  protected readonly demande = signal<DemandeFormateur | null>(null);
  protected readonly envoiDemande = signal(false);
  protected readonly erreurDemande = signal<string | null>(null);
  protected readonly demandeForm = this.fb.group({
    expertise: ['', [Validators.required, Validators.maxLength(200)]],
    presentation: ['', Validators.maxLength(1000)],
    lien: ['', Validators.maxLength(300)],
  });
  /** Le formulaire de demande s'affiche s'il n'y a ni demande en cours ni validation. */
  protected readonly peutDemander = computed(() => {
    const u = this.user();
    const d = this.demande();
    return !!u && u.role !== 'ADMIN' && !u.formateurValide && d?.statut !== 'EN_ATTENTE';
  });

  // ---------- Suppression du compte ----------
  protected readonly confirmationSuppression = signal(false);
  protected readonly suppressionEnCours = signal(false);
  protected readonly erreurSuppression = signal<string | null>(null);
  protected mdpSuppression = '';

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.api.moi().subscribe({
      next: (u) => {
        this.appliquer(u);
        if (u.role === 'ETUDIANT') this.chargerParcours();
        if (u.role !== 'ADMIN') this.api.maDemandeFormateur().subscribe({ next: (d) => this.demande.set(d), error: () => undefined });
      },
      error: (err) => this.erreur.set(errorMessage(err, 'Impossible de charger ton compte')),
    });
  }

  // ---------- Informations ----------

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

  protected choisirPhoto(fichiers: File[]): void {
    const f = fichiers[0];
    if (!f) return;
    this.cleDropzone.update((k) => k + 1);
    this.erreurPhoto.set(null);
    if (!TYPES_PHOTO.includes(f.type)) {
      this.erreurPhoto.set('Format accepté : PNG, JPEG ou WebP');
      return;
    }
    if (f.size > PHOTO_MAX) {
      this.erreurPhoto.set('Image trop lourde : 2 Mo maximum');
      return;
    }
    this.envoiPhoto.set(true);
    this.api.envoyerAvatar(f).subscribe({
      next: (u) => {
        this.appliquer(u);
        this.envoiPhoto.set(false);
        this.snack.open('Photo enregistrée', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.erreurPhoto.set(errorMessage(err, 'Envoi impossible'));
        this.envoiPhoto.set(false);
      },
    });
  }

  protected retirerPhoto(): void {
    this.envoiPhoto.set(true);
    this.api.supprimerAvatar().subscribe({
      next: (u) => {
        this.appliquer(u);
        this.envoiPhoto.set(false);
        this.snack.open('Photo retirée', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.erreurPhoto.set(errorMessage(err, 'Suppression impossible'));
        this.envoiPhoto.set(false);
      },
    });
  }

  // ---------- Parcours ----------

  private chargerParcours(): void {
    this.api.monParcours().subscribe({ next: (p) => this.appliquerParcours(p), error: () => undefined });
  }

  protected suggerer(): void {
    const texte = this.texteSuggestion.trim();
    if (texte.length < 3 || this.suggestionEnCours()) return;
    this.suggestionEnCours.set(true);
    this.erreurSuggestion.set(null);
    this.suggestion.set(null);
    this.iaGen.suggererParcours(texte).subscribe({
      next: (s) => {
        this.suggestionEnCours.set(false);
        this.suggestion.set(s);
      },
      error: (err) => {
        this.suggestionEnCours.set(false);
        this.erreurSuggestion.set(errorMessage(err, 'L’IA générative est indisponible pour le moment.'));
      },
    });
  }

  /** Ajoute les domaines suggérés (sans retirer les autres) et l'objectif ; l'apprenant enregistre ensuite. */
  protected appliquerSuggestion(s: SuggestionParcoursIa): void {
    this.parcours = {
      ...this.parcours,
      domaines: [...new Set([...(this.parcours.domaines ?? []), ...s.domaines])],
      objectif: s.objectif ?? this.parcours.objectif,
    };
    this.suggestion.set(null);
    this.snack.open('Suggestion appliquée : vérifie puis enregistre.', 'OK', { duration: 3500 });
  }

  protected enregistrerParcours(): void {
    this.enregistrementParcours.set(true);
    this.api.enregistrerMonParcours(this.parcours).subscribe({
      next: (p) => {
        this.appliquerParcours(p);
        this.enregistrementParcours.set(false);
        this.snack.open('Parcours enregistré', 'OK', { duration: 2500 });
      },
      error: (err) => {
        this.enregistrementParcours.set(false);
        this.snack.open(errorMessage(err, 'Enregistrement impossible'), 'OK', { duration: 4000 });
      },
    });
  }

  private appliquerParcours(p: Parcours): void {
    this.parcours = { ...p, domaines: [...(p.domaines ?? [])] };
    this.domainesListe = [...new Set([...DOMAINES_PARCOURS, ...(p.domaines ?? [])])];
    this.profilLibelle.set(ONBOARDING.profils.find((x) => x.code === p.profil)?.titre ?? 'Non renseigné');
  }

  // ---------- Demande formateur ----------

  protected envoyerDemande(): void {
    if (this.demandeForm.invalid) {
      this.demandeForm.markAllAsTouched();
      return;
    }
    this.envoiDemande.set(true);
    this.erreurDemande.set(null);
    this.api.demanderAEtreFormateur(this.demandeForm.getRawValue()).subscribe({
      next: (d) => {
        this.demande.set(d);
        this.demandeForm.reset();
        this.envoiDemande.set(false);
        this.snack.open('Demande envoyée à l’équipe Bawsla', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.erreurDemande.set(errorMessage(err, 'Envoi impossible'));
        this.envoiDemande.set(false);
      },
    });
  }

  // ---------- Sécurité ----------

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

  protected supprimerCompte(): void {
    if (!this.mdpSuppression) return;
    this.suppressionEnCours.set(true);
    this.erreurSuppression.set(null);
    this.api.desactiverMonCompte(this.mdpSuppression).subscribe({
      next: () => {
        this.snack.open('Ton compte a été supprimé. À bientôt sur Bawsla.', 'OK', { duration: 5000 });
        this.auth.logout('/bienvenue');
      },
      error: (err) => {
        this.erreurSuppression.set(errorMessage(err, 'Suppression impossible'));
        this.suppressionEnCours.set(false);
      },
    });
  }

  private appliquer(u: Utilisateur): void {
    this.user.set(u);
    this.infos.reset({ prenom: u.prenom, nom: u.nom, bio: u.bio ?? '' });
    of(u)
      .pipe(switchMap((x) => this.avatars.url(x.id, x.avatarVersion)))
      .subscribe((url) => this.photo.set(url));
  }
}
