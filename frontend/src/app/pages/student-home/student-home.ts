import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AuthService } from '../../core/auth/auth.service';
import { lireOnboarding } from '../../core/profil/onboarding';
import { BwButton, EmptyState } from '../../shared';
import { errorMessage } from '../../shared/models/api-response.model';
import { CourseTile, CourseTileData, ProgressRing, SectionHead, Skeleton } from '../../shared/ui';
import { ForumApercu } from '../../features/forum/components/forum-apercu';
import { FORMATEUR_DEFAUT, IMAGE_DEFAUT, LIBELLE_NIVEAU, formatDuree, libelleMatiere, libellePrix } from '../../features/cours/models/cours.constantes';
import { Chapitre, Cours, Inscription } from '../../features/cours/models/cours.model';
import { CoursImageService } from '../../features/cours/services/cours-image.service';
import { CoursService } from '../../features/cours/services/cours.service';
import { FormateurService } from '../../features/cours/services/formateur.service';
import { Certificat } from '../../features/quiz/models/quiz.model';
import { QuizService } from '../../features/quiz/services/quiz.service';
import { CoursRecommande } from '../../features/user/models/ia.model';
import { IaUserService } from '../../features/user/services/ia-user.service';

type Etat = 'chargement' | 'ok' | 'erreur';

interface Suivi {
  cours: Cours;
  inscription: Inscription;
  pourcentage: number;
}

/**
 * Accueil de l'espace apprenant : reprendre où on s'est arrêté, examen final débloqué,
 * recommandations de l'IA, progression, certificats et forum.
 * Données : cours-service (GET /api/cours/mes-inscriptions, GET /api/cours, chapitres et progression),
 * quiz-service (GET /api/quiz/certificats/mes), user-service (GET /api/users/me/ia : recommandations).
 */
@Component({
  selector: 'app-student-home',
  imports: [DatePipe, RouterLink, MatIconModule, BwButton, EmptyState, CourseTile, ProgressRing, SectionHead, Skeleton, ForumApercu],
  templateUrl: './student-home.html',
  styleUrl: './student-home.scss',
})
export class StudentHome implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly coursService = inject(CoursService);
  private readonly quizService = inject(QuizService);
  private readonly iaService = inject(IaUserService);
  private readonly images = inject(CoursImageService);
  private readonly formateurs = inject(FormateurService);

  protected readonly prenom = computed(() => this.auth.currentUser()?.prenom || 'toi');
  // « Dimanche 18 octobre » : seule la première lettre prend une majuscule.
  protected readonly date = new Date()
    .toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })
    .replace(/^./, (c) => c.toUpperCase());
  protected readonly onboarding = lireOnboarding();
  protected readonly sousTitreReco = this.onboarding?.objectif
    ? `Choisi par l’IA d’après ton objectif (« ${this.onboarding.objectif} ») et tes derniers quiz`
    : 'Choisi par l’IA à partir de tes derniers quiz';

  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  private readonly catalogue = signal<Cours[]>([]);
  private readonly inscriptions = signal<Inscription[]>([]);
  protected readonly certificats = signal<Certificat[]>([]);
  private readonly imagesCours = signal<Record<number, string>>({});
  private readonly noms = signal<Record<number, string>>({});
  protected readonly prochain = signal<Chapitre | null>(null);

  protected readonly etatReco = signal<Etat>('chargement');
  private readonly recos = signal<CoursRecommande[]>([]);
  protected readonly motifSansReco = signal('');
  protected readonly joursActifs = signal<number | null>(null);

  /** Cours suivis, du plus récemment actif au plus ancien. */
  private readonly suivis = computed<Suivi[]>(() => {
    const parId = new Map(this.catalogue().map((c) => [c.id, c]));
    return this.inscriptions()
      .flatMap((i) => {
        const cours = parId.get(i.coursId);
        return cours ? [{ cours, inscription: i, pourcentage: Math.round(i.progression * 100) }] : [];
      })
      .sort((a, b) => derniere(b.inscription).localeCompare(derniere(a.inscription)));
  });
  protected readonly nbSuivis = computed(() => this.suivis().length);
  protected readonly enCours = computed(() => this.suivis().filter((s) => !s.inscription.termine));
  protected readonly nbTermines = computed(() => this.suivis().filter((s) => s.inscription.termine).length);
  protected readonly progressionMoyenne = computed(() => {
    const s = this.suivis();
    return s.length ? Math.round(s.reduce((t, x) => t + x.pourcentage, 0) / s.length) : 0;
  });
  protected readonly reprendre = computed(() => this.enCours()[0] ?? null);
  protected readonly imageReprendre = computed(() => {
    const r = this.reprendre();
    return r ? (this.imagesCours()[r.cours.id] ?? IMAGE_DEFAUT) : IMAGE_DEFAUT;
  });
  protected readonly faits = computed(() => {
    const r = this.reprendre();
    return r ? Math.round(r.inscription.progression * r.cours.nbChapitres) : 0;
  });
  /** Cours dont tous les chapitres sont terminés et dont le certificat n'est pas encore obtenu. */
  protected readonly examenPret = computed(
    () => this.suivis().find((s) => s.inscription.termine && !this.certificats().some((c) => c.coursId === s.cours.id))?.cours ?? null,
  );
  protected readonly tuilesEnCours = computed(() => this.enCours().slice(1, 4).map((s) => this.tuile(s.cours, s.pourcentage)));
  protected readonly derniersCertificats = computed(() => this.certificats().slice(0, 3));

  protected readonly recommandations = computed(() => {
    const parId = new Map(this.catalogue().map((c) => [c.id, c]));
    const suivis = new Map(this.suivis().map((s) => [s.cours.id, s]));
    return this.recos().flatMap((r) => {
      const c = parId.get(r.coursId);
      if (!c) return [];
      const s = suivis.get(c.id);
      const lien = s?.inscription.termine ? ['/app/evaluations/examen', c.id] : ['/app/explorer', c.id];
      return [{ coursId: c.id, raison: r.raison, lien, tuile: this.tuile(c, s?.pourcentage) }];
    });
  });

  ngOnInit(): void {
    this.charger();
    this.chargerRecommandations();
  }

  protected charger(): void {
    this.etat.set('chargement');
    forkJoin({
      inscriptions: this.coursService.mesInscriptions(),
      cours: this.coursService.findAll(),
      certificats: this.quizService.mesCertificats().pipe(catchError(() => of<Certificat[]>([]))),
    }).subscribe({
      next: ({ inscriptions, cours, certificats }) => {
        this.catalogue.set(cours);
        this.inscriptions.set(inscriptions);
        this.certificats.set([...certificats].sort((a, b) => b.dateEmission.localeCompare(a.dateEmission)));
        this.chargerDetails(cours);
        this.chargerProchainChapitre();
        this.etat.set('ok');
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger tes cours'));
        this.etat.set('erreur');
      },
    });
  }

  protected explorer(): void {
    this.router.navigate(['/app/explorer']);
  }

  private chargerRecommandations(): void {
    this.etatReco.set('chargement');
    this.iaService.monIa().subscribe({
      next: (ia) => {
        this.recos.set(ia.recommandations ?? []);
        this.motifSansReco.set(ia.recommandationsIndisponibles ?? '');
        this.joursActifs.set(ia.indicateurs?.joursActifs30j ?? null);
        this.etatReco.set('ok');
      },
      error: (err) => {
        this.motifSansReco.set(errorMessage(err, 'Les recommandations sont indisponibles pour le moment.'));
        this.etatReco.set('erreur');
      },
    });
  }

  private chargerDetails(cours: Cours[]): void {
    for (const id of new Set(cours.map((c) => c.enseignantId))) {
      this.formateurs.nom(id).subscribe((nom) => this.noms.update((n) => ({ ...n, [id]: nom })));
    }
    for (const c of cours) {
      this.images.url(c).subscribe((url) => this.imagesCours.update((m) => ({ ...m, [c.id]: url })));
    }
  }

  private chargerProchainChapitre(): void {
    const r = this.reprendre();
    if (!r) return;
    forkJoin({ chapitres: this.coursService.chapitres(r.cours.id), progression: this.coursService.maProgression(r.cours.id) }).subscribe({
      next: ({ chapitres, progression }) => {
        const faits = new Set(progression?.chapitresTermines ?? []);
        this.prochain.set([...chapitres].sort((a, b) => a.ordre - b.ordre).find((c) => !faits.has(c.id)) ?? null);
      },
      error: () => this.prochain.set(null),
    });
  }

  private tuile(c: Cours, progression?: number): CourseTileData {
    return {
      id: c.id,
      titre: c.titre,
      description: c.description ?? undefined,
      categorie: libelleMatiere(c.categorie, c.categoriePrecision),
      niveau: c.niveau ? LIBELLE_NIVEAU[c.niveau] : '',
      enseignant: this.noms()[c.enseignantId] ?? FORMATEUR_DEFAUT,
      image: this.imagesCours()[c.id] ?? IMAGE_DEFAUT,
      prix: libellePrix(c.prix),
      chapitres: c.nbChapitres,
      duree: formatDuree(c.dureeTotaleMinutes) || undefined,
      progression,
    };
  }
}

function derniere(i: Inscription): string {
  return i.dateDerniereActivite ?? i.dateInscription;
}
