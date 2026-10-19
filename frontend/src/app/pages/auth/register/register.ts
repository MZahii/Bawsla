import { Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of, shareReplay, switchMap } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { LIBELLE_NIVEAU, formatDuree, libellePrix } from '../../../features/cours/models/cours.constantes';
import { Cours } from '../../../features/cours/models/cours.model';
import { CoursImageService } from '../../../features/cours/services/cours-image.service';
import { CoursService } from '../../../features/cours/services/cours.service';
import { Logo } from '../../../shared/components/logo/logo';
import { BwButton } from '../../../shared/directives/bw-button';
import { errorMessage } from '../../../shared/models/api-response.model';
import { RegisterRequest } from '../../../shared/models/user.model';
import { Skeleton } from '../../../shared/ui/skeleton';

type Choix = RegisterRequest['role'];

/** Ce que montre le panneau photo, selon le rôle choisi. */
interface Vitrine {
  photo: string;
  titre: string;
  points: { icone: string; texte: string }[];
}

/**
 * Inscription ouverte à tous. On choisit d'abord « apprendre » (ETUDIANT) ou « enseigner » (ENSEIGNANT).
 * - Apprenant : compte créé, puis questionnaire d'accueil facultatif (/demarrer).
 * - Formateur : on demande en plus le domaine d'expertise et une courte présentation ; le profil
 *   est validé par l'admin avant la première publication (Admin → Demandes formateurs).
 * - Depuis un cours (?cours=3) : on affiche le cours visé, on reste en apprenant et on y revient après.
 * Expertise, présentation et lien partent avec l'inscription : user-service crée la demande formateur,
 * traitée dans Admin → Demandes formateurs, et son statut s'affiche dans « Mon compte ».
 */
@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, Logo, BwButton, Skeleton, MatButtonModule, MatFormFieldModule, MatInputModule, MatIconModule],
  templateUrl: './register.html',
  styleUrls: ['../auth-page.scss', './register.scss'],
})
export class Register {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly coursService = inject(CoursService);
  private readonly images = inject(CoursImageService);

  /** Cours choisi avant l'inscription (?cours=3) : on y revient après. */
  protected readonly coursVise = /^\d+$/.test(this.route.snapshot.queryParamMap.get('cours') ?? '')
    ? this.route.snapshot.queryParamMap.get('cours')
    : null;

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hidePassword = signal(true);
  protected readonly choix = signal<Choix>(
    !this.coursVise && this.route.snapshot.queryParamMap.get('role') === 'formateur' ? 'ENSEIGNANT' : 'ETUDIANT',
  );
  protected readonly formateur = computed(() => this.choix() === 'ENSEIGNANT');

  private readonly cours$ = (
    this.coursVise ? this.coursService.findById(+this.coursVise).pipe(catchError(() => of(null))) : of(null)
  ).pipe(shareReplay(1));
  /** Cours visé (undefined : en chargement, null : introuvable ou pas de cours). */
  protected readonly cours = toSignal<Cours | null | undefined>(this.cours$, {
    initialValue: this.coursVise ? undefined : null,
  });
  protected readonly coursImage = toSignal(
    this.cours$.pipe(switchMap((c) => (c ? this.images.url(c) : of(null)))),
    { initialValue: null },
  );
  protected readonly libelleNiveau = LIBELLE_NIVEAU;
  protected readonly formatDuree = formatDuree;
  protected readonly libellePrix = libellePrix;

  protected readonly roles: { code: Choix; icone: string; titre: string; texte: string }[] = [
    { code: 'ETUDIANT', icone: 'auto_stories', titre: 'Apprendre', texte: 'Cours, quiz et certificats' },
    { code: 'ENSEIGNANT', icone: 'history_edu', titre: 'Enseigner', texte: 'Publier mes propres cours' },
  ];

  private readonly vitrines: Record<Choix, Vitrine> = {
    ETUDIANT: {
      photo: 'assets/demo/cours/etude-portrait.jpg',
      titre: 'Apprends à ton rythme, sans perdre le cap.',
      points: [
        { icone: 'headphones', texte: 'Chaque cours a un résumé et une version audio' },
        { icone: 'target', texte: 'Les quiz te disent quelle notion revoir' },
        { icone: 'verified', texte: 'Un certificat vérifiable à la fin' },
      ],
    },
    ENSEIGNANT: {
      photo: 'assets/demo/cours/etude-3.jpg',
      titre: 'Transmets ce que tu sais, à ta façon.',
      points: [
        { icone: 'sell', texte: 'Tes cours, gratuits ou à ton prix' },
        { icone: 'auto_awesome', texte: 'Résumé, audio et quiz générés depuis ton PDF' },
        { icone: 'monitoring', texte: 'La progression de tes apprenants en un coup d’œil' },
      ],
    },
  };
  protected readonly vitrine = computed(() => this.vitrines[this.choix()]);
  protected readonly photos = [this.vitrines.ETUDIANT.photo, this.vitrines.ENSEIGNANT.photo];

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

  private readonly motDePasse = toSignal(this.form.controls.motDePasse.valueChanges, { initialValue: '' });
  private readonly bio = toSignal(this.form.controls.bio.valueChanges, { initialValue: '' });
  protected readonly bioLongueur = computed(() => this.bio().length);

  /** Robustesse indicative du mot de passe : 0 (vide) à 4. Seule la longueur minimale est bloquante. */
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

  constructor() {
    if (this.formateur()) this.choisir('ENSEIGNANT');
  }

  protected choisir(c: Choix): void {
    this.choix.set(c);
    const exp = this.form.controls.expertise;
    exp.setValidators(c === 'ENSEIGNANT' ? [Validators.required] : []);
    exp.updateValueAndValidity();
  }

  /** Flèches gauche/droite dans le groupe de rôles, comme un groupe de boutons radio natif. */
  protected naviguer(event: KeyboardEvent): void {
    if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'].includes(event.key)) return;
    event.preventDefault();
    const suivant: Choix = this.formateur() ? 'ETUDIANT' : 'ENSEIGNANT';
    this.choisir(suivant);
    const groupe = (event.currentTarget as HTMLElement).closest('[role="radiogroup"]');
    groupe?.querySelector<HTMLElement>(`[data-role="${suivant}"]`)?.focus();
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { prenom, nom, email, motDePasse, expertise, bio, lien } = this.form.getRawValue();
    const profilFormateur = this.formateur() ? { expertise, presentation: bio, lien } : {};
    this.auth.register({ prenom, nom, email, motDePasse, role: this.choix(), ...profilFormateur }).subscribe({
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
