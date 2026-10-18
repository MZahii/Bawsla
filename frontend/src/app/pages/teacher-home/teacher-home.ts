import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { BwButton, EmptyState } from '../../shared';
import { errorMessage } from '../../shared/models/api-response.model';
import { Avatar, BwChart, Kpi, Skeleton, Timeline, TimelineItem } from '../../shared/ui';
import { ForumApercu } from '../../features/forum/components/forum-apercu';
import { tempsRelatif } from '../../features/forum/components/outils';
import { IMAGE_DEFAUT, LIBELLE_STATUT, libellePrix } from '../../features/cours/models/cours.constantes';
import { Cours } from '../../features/cours/models/cours.model';
import { CoursImageService } from '../../features/cours/services/cours-image.service';
import { CoursService } from '../../features/cours/services/cours.service';
import { StudioLigne } from '../../features/quiz/models/quiz.model';
import { QuizService } from '../../features/quiz/services/quiz.service';
import { LigneApprenant } from '../../features/user/models/ia.model';
import { IaUserService } from '../../features/user/services/ia-user.service';

const CLE_ATTENTE = 'bawsla.formateur.enAttente';
const NB_SEMAINES = 8;
const SEMAINE_MS = 7 * 24 * 3600 * 1000;

type Etat = 'chargement' | 'ok' | 'erreur';

/**
 * Tableau de bord du studio formateur.
 * Données : cours-service (GET /api/cours, filtré sur les cours du formateur),
 * user-service (GET /api/users/formateur/apprenants : inscriptions, progression, risque d'abandon),
 * quiz-service (GET /api/quiz/mes-quiz : quiz en brouillon).
 * Un formateur qui vient de s'inscrire voit que son profil attend la validation de l'admin.
 */
@Component({
  selector: 'app-teacher-home',
  imports: [RouterLink, MatIconModule, BwButton, EmptyState, Avatar, BwChart, Kpi, Skeleton, Timeline, ForumApercu],
  templateUrl: './teacher-home.html',
  styleUrl: './teacher-home.scss',
})
export class TeacherHome implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly coursService = inject(CoursService);
  private readonly quizService = inject(QuizService);
  private readonly iaService = inject(IaUserService);
  private readonly images = inject(CoursImageService);

  protected readonly nom = computed(() => {
    const u = this.auth.currentUser();
    return [u?.prenom, u?.nom].filter(Boolean).join(' ');
  });
  protected readonly enAttente = signal(lire(CLE_ATTENTE) === '1');
  protected readonly libelleStatut = LIBELLE_STATUT;
  protected readonly libellePrix = libellePrix;
  protected readonly tempsRelatif = tempsRelatif;

  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  protected readonly mesCours = signal<Cours[]>([]);
  protected readonly brouillons = signal<StudioLigne[]>([]);
  protected readonly imagesCours = signal<Record<number, string>>({});

  protected readonly etatApprenants = signal<Etat>('chargement');
  protected readonly erreurApprenants = signal('');
  private readonly lignes = signal<LigneApprenant[]>([]);

  protected readonly totalInscriptions = computed(() => this.mesCours().reduce((t, c) => t + (c.nbInscrits ?? 0), 0));
  protected readonly nbPublies = computed(() => this.mesCours().filter((c) => c.statut === 'PUBLIE').length);
  protected readonly tauxAchevement = computed(() => {
    const l = this.lignes();
    return l.length ? `${Math.round((l.filter((x) => (x.progression ?? 0) >= 1).length / l.length) * 100)} %` : '–';
  });
  protected readonly nbRisqueEleve = computed(
    () => new Set(this.lignes().filter((l) => l.risque?.niveau === 'eleve').map((l) => l.etudiantId)).size,
  );

  /** Achèvement par cours (% d'inscriptions terminées), calculé sur les lignes apprenants. */
  protected readonly achevement = computed(() => {
    const parCours: Record<number, { total: number; finis: number }> = {};
    for (const l of this.lignes()) {
      const s = (parCours[l.coursId] ??= { total: 0, finis: 0 });
      s.total++;
      if ((l.progression ?? 0) >= 1) s.finis++;
    }
    const res: Record<number, number> = {};
    for (const [id, s] of Object.entries(parCours)) res[+id] = Math.round((s.finis / s.total) * 100);
    return res;
  });

  /** Nouvelles inscriptions par semaine, sur les 8 dernières semaines. */
  protected readonly inscriptionsParSemaine = computed(() => {
    const maintenant = Date.now();
    const valeurs = new Array<number>(NB_SEMAINES).fill(0);
    for (const l of this.lignes()) {
      if (!l.dateInscription) continue;
      const age = Math.floor((maintenant - new Date(l.dateInscription).getTime()) / SEMAINE_MS);
      if (age >= 0 && age < NB_SEMAINES) valeurs[NB_SEMAINES - 1 - age]++;
    }
    const labels = valeurs.map((_, i) => {
      const debut = new Date(maintenant - (NB_SEMAINES - i) * SEMAINE_MS);
      return debut.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
    });
    return { labels, series: [{ label: 'Nouvelles inscriptions', data: valeurs }], total: valeurs.reduce((a, b) => a + b, 0) };
  });

  protected readonly aSuivre = computed(() =>
    this.lignes()
      .filter((l) => l.risque?.niveau === 'eleve')
      .sort((a, b) => (b.risque?.probabilite ?? 0) - (a.risque?.probabilite ?? 0))
      .slice(0, 4),
  );

  protected readonly activite = computed<TimelineItem[]>(() =>
    this.lignes()
      .filter((l) => l.dateInscription)
      .sort((a, b) => (b.dateInscription ?? '').localeCompare(a.dateInscription ?? ''))
      .slice(0, 4)
      .map((l) => ({
        quand: tempsRelatif(l.dateInscription),
        titre: `${l.prenom} ${l.nom.charAt(0)}. s’est inscrit`,
        detail: l.coursTitre,
        icone: 'person_add',
        ton: 'neutre' as const,
      })),
  );

  ngOnInit(): void {
    this.charger();
    this.chargerApprenants();
  }

  protected charger(): void {
    const id = this.auth.currentUser()?.id;
    this.etat.set('chargement');
    forkJoin({
      cours: this.coursService.findAll(),
      quiz: this.quizService.mesQuiz().pipe(catchError(() => of<StudioLigne[]>([]))),
    }).subscribe({
      next: ({ cours, quiz }) => {
        const miens = cours.filter((c) => c.enseignantId === id);
        this.mesCours.set(miens);
        this.brouillons.set(quiz.filter((q) => q.statut === 'BROUILLON').slice(0, 4));
        for (const c of miens) {
          this.images.url(c).subscribe((url) => this.imagesCours.update((m) => ({ ...m, [c.id]: url })));
        }
        this.etat.set('ok');
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger tes cours'));
        this.etat.set('erreur');
      },
    });
  }

  protected chargerApprenants(): void {
    this.etatApprenants.set('chargement');
    this.iaService.apprenantsDuFormateur().subscribe({
      next: (d) => {
        this.lignes.set(d.lignes ?? []);
        this.etatApprenants.set('ok');
      },
      error: (err) => {
        this.erreurApprenants.set(errorMessage(err, 'Les données de tes apprenants sont indisponibles'));
        this.etatApprenants.set('erreur');
      },
    });
  }

  protected image(c: Cours): string {
    return this.imagesCours()[c.id] ?? IMAGE_DEFAUT;
  }

  protected pct(p: number | undefined): string {
    return p === undefined ? '–' : `${Math.round(p * 100)} %`;
  }

  protected masquerAttente(): void {
    this.enAttente.set(false);
    try {
      localStorage.removeItem(CLE_ATTENTE);
    } catch {
      /* ignoré */
    }
  }
}

function lire(cle: string): string | null {
  try {
    return localStorage.getItem(cle);
  } catch {
    return null;
  }
}
