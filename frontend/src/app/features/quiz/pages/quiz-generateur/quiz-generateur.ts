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

import { COURS } from '../../../../core/demo/demo-data';
import { AiBox, AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import {
  DIFFICULTES,
  Difficulte,
  GenererQuizResponse,
  NIVEAUX_BLOOM,
  NiveauBloom,
  libelleBloom,
  libelleDifficulte,
} from '../../models/quiz-ia.model';
import { QuizIaService, messageErreurIa } from '../../services/quiz-ia.service';

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
}

const MAX_COMPETENCES = 8;
const MAX_LONGUEUR_COMPETENCE = 150;

/**
 * Studio : génération d'un QCM par l'IA à partir d'objectifs (thème et compétences visées), jamais du
 * contenu d'un chapitre. Le formateur choisit la matière, le cours, les niveaux de Bloom, l'un des 5 niveaux
 * de difficulté et le nombre de questions, puis accepte ou rejette chaque question proposée. Les questions
 * incohérentes sont déjà écartées par la vérification Self-Consistency.
 * À brancher : QuizService.create() avec les questions acceptées (quiz-service).
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

  // La matière vient du cours (catégorie côté module Cours) : on ne propose que les cours de la matière choisie.
  protected readonly matieres = [...new Set(COURS.map((c) => c.categorie))].sort();
  protected readonly matiere = signal(COURS[0]?.categorie ?? '');
  protected readonly cours = computed(() => COURS.filter((c) => c.categorie === this.matiere()));
  protected readonly coursId = signal<number | null>(COURS[0]?.id ?? null);

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
    this.snack.open(`Quiz enregistré en brouillon avec ${this.acceptees()} questions`, 'OK', { duration: 3000 });
    this.router.navigateByUrl('/studio/quiz');
  }
}
