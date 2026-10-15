import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { AiBox, AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import {
  DIFFICULTES,
  Difficulte,
  GenererQuizResponse,
  MATIERES,
  MATIERE_AUTRE,
  MAX_LONGUEUR_MATIERE,
  NIVEAUX_BLOOM,
  NiveauBloom,
  libelleBloom,
  libelleDifficulte,
  QuestionQcm,
  matiereDuCours,
} from '../../models/quiz-ia.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizIaService, messageErreurIa } from '../../services/quiz-ia.service';
import { QuizService } from '../../services/quiz.service';

type Etat = 'vide' | 'generation' | 'resultat' | 'erreur';
type Decision = 'acceptee' | 'rejetee' | 'attente';

interface QuestionAffichee {
  enonce: string;
  options: string[];
  bonne: number;
  bloom: string;
  competence?: string;
  explication: string;
  /** Raison donnée par la vérification Self-Consistency quand la question est écartée. */
  rejetee?: string;
  decision: Decision;
  /** Question telle que proposée par l'IA (erreurs associées et niveau de Bloom), envoyée à quiz-service. */
  source: QuestionQcm;
}

const MAX_COMPETENCES = 8;
const MAX_LONGUEUR_COMPETENCE = 150;

/**
 * Studio : génération d'un QCM par l'IA à partir d'objectifs (thème et compétences visées), jamais du
 * contenu d'un chapitre. Le formateur choisit la matière (ou « Autre » avec une précision, transmise à l'IA), le cours, les niveaux de Bloom, l'un des 5 niveaux
 * de difficulté et le nombre de questions, puis accepte ou rejette chaque question proposée. Les questions
 * incohérentes sont déjà écartées par la vérification Self-Consistency.
 * Les questions acceptées sont enregistrées en brouillon par quiz-service (POST /api/quiz).
 */
@Component({
  selector: 'app-quiz-generateur',
  imports: [
    FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatSliderModule,
    MatButtonToggleModule, MatIconModule, AiBox, AiLoading, BwButton, EmptyState, PageHeader,
  ],
  templateUrl: './quiz-generateur.html',
  styleUrl: './quiz-generateur.scss',
})
export class QuizGenerateur {
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly ia = inject(QuizIaService);
  private readonly quizService = inject(QuizService);
  private readonly catalogue = inject(CoursTitresService);

  // Liste générale de disciplines ; on ne propose que les cours de la matière choisie (déduite de leur catégorie).
  protected readonly matieres = MATIERES;
  protected readonly matiereAutre = MATIERE_AUTRE;
  protected readonly maxLongueurMatiere = MAX_LONGUEUR_MATIERE;
  protected readonly matiere = signal('Informatique');
  protected readonly precision = signal('');
  protected readonly cours = computed(() => this.catalogue.cours().filter((c) => matiereDuCours(c.categorie) === this.matiere()));
  protected readonly coursId = signal<number | null>(null);
  protected readonly envoi = signal(false);
  /** Matière envoyée à l'IA : la précision pour « Autre ». */
  protected readonly matiereEnvoyee = computed(() => (this.matiere() === MATIERE_AUTRE ? this.precision().trim() : this.matiere()));

  protected readonly theme = signal('');
  protected readonly competencesTexte = signal('');
  protected readonly competences = computed(() =>
    this.competencesTexte()
      .split('\n')
      .map((c) => c.trim())
      .filter((c) => c.length > 0),
  );

  protected readonly niveaux = NIVEAUX_BLOOM;
  protected readonly bloom = signal<NiveauBloom[]>(['COMPRENDRE', 'APPLIQUER', 'ANALYSER']);
  protected readonly difficultes = DIFFICULTES;
  protected readonly difficulte = signal<Difficulte>('INTERMEDIAIRE');
  protected readonly resumeDifficulte = computed(() => DIFFICULTES.find((d) => d.code === this.difficulte())?.resume);
  protected readonly nombre = signal(5);
  protected readonly lettres = ['A', 'B', 'C', 'D'];
  protected readonly maxCompetences = MAX_COMPETENCES;
  protected readonly maxLongueurCompetence = MAX_LONGUEUR_COMPETENCE;
  protected readonly competenceTropLongue = computed(() => this.competences().some((c) => c.length > MAX_LONGUEUR_COMPETENCE));

  protected readonly formulaireValide = computed(
    () =>
      this.coursId() !== null &&
      this.matiereEnvoyee().length >= 2 &&
      this.matiereEnvoyee().length <= MAX_LONGUEUR_MATIERE &&
      this.theme().trim().length >= 2 &&
      this.competences().length >= 1 &&
      this.competences().length <= MAX_COMPETENCES &&
      !this.competenceTropLongue() &&
      this.bloom().length >= 1,
  );

  protected readonly etat = signal<Etat>('vide');
  protected readonly erreur = signal('');
  protected readonly reponse = signal<GenererQuizResponse | null>(null);
  protected readonly questions = signal<QuestionAffichee[]>([]);
  protected readonly acceptees = computed(() => this.questions().filter((q) => q.decision === 'acceptee').length);
  protected readonly ecartees = computed(() => this.questions().filter((q) => q.rejetee).length);
  protected readonly libelleNiveau = computed(() => {
    const r = this.reponse();
    return r ? libelleDifficulte(r.difficulte) : '';
  });

  constructor() {
    this.catalogue.charger();
  }

  protected choisirMatiere(m: string): void {
    this.matiere.set(m);
    this.coursId.set(this.cours()[0]?.id ?? null);
  }

  protected basculerBloom(code: NiveauBloom): void {
    this.bloom.update((b) => (b.includes(code) ? b.filter((x) => x !== code) : [...b, code]));
  }

  protected generer(): void {
    const coursId = this.coursId();
    if (coursId === null || !this.formulaireValide() || this.etat() === 'generation') return;
    this.etat.set('generation');
    this.ia
      .generer({
        coursId,
        matiere: this.matiereEnvoyee(),
        theme: this.theme().trim(),
        competences: this.competences(),
        niveauxBloom: this.bloom(),
        difficulte: this.difficulte(),
        nbQuestions: this.nombre(),
      })
      .subscribe({
        next: (r) => {
          this.reponse.set(r);
          this.questions.set(
            r.questions.map((q) => ({
              enonce: q.enonce,
              options: q.choix.map((c) => c.texte),
              bonne: q.choix.findIndex((c) => c.estCorrecte),
              bloom: libelleBloom(q.niveauBloom),
              competence: q.competence,
              explication: q.explication,
              rejetee: q.statut === 'REJETEE' ? (q.raisonRejet ?? 'Réponses divergentes à la vérification.') : undefined,
              decision: q.statut === 'REJETEE' ? 'rejetee' : 'attente',
              source: q,
            })),
          );
          this.etat.set('resultat');
        },
        error: (err) => {
          this.erreur.set(messageErreurIa(err));
          this.etat.set('erreur');
        },
      });
  }

  protected decider(q: QuestionAffichee, decision: Decision): void {
    this.questions.update((l) => l.map((x) => (x === q ? { ...x, decision } : x)));
  }

  protected toutAccepter(): void {
    this.questions.update((l) => l.map((x) => (x.rejetee || x.decision === 'rejetee' ? x : { ...x, decision: 'acceptee' })));
  }

  protected enregistrer(): void {
    const coursId = this.coursId();
    const acceptees = this.questions().filter((q) => q.decision === 'acceptee');
    if (coursId === null || acceptees.length === 0 || this.envoi()) return;
    this.envoi.set(true);
    this.quizService
      .create({
        titre: this.theme().trim(),
        description: 'Généré avec l’IA puis relu par le formateur.',
        coursId,
        theme: this.theme().trim(),
        matiere: this.matiereEnvoyee(),
        difficulte: this.reponse()?.difficulte ?? this.difficulte(),
        questions: acceptees.map((q) => ({
          enonce: q.enonce,
          niveauBloom: q.source.niveauBloom,
          competence: q.competence,
          explication: q.explication,
          choix: q.source.choix.map((c) => ({ texte: c.texte, estCorrecte: c.estCorrecte, erreurAssociee: c.erreurAssociee })),
        })),
      })
      .subscribe({
        next: (quiz) => {
          this.snack.open(`Quiz « ${quiz.titre} » enregistré en brouillon avec ${quiz.nbQuestions} questions`, 'OK', { duration: 3000 });
          this.router.navigateByUrl('/studio/quiz');
        },
        error: (e: HttpErrorResponse) => {
          this.envoi.set(false);
          this.snack.open(e.error?.message ?? 'Enregistrement impossible.', 'OK', { duration: 5000 });
        },
      });
  }
}
