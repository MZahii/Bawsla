import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';

import { BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { QuizDetail, QuizRequest, TypeQuiz } from '../../models/quiz.model';
import {
  DIFFICULTES,
  Difficulte,
  MATIERES,
  MATIERE_AUTRE,
  MAX_LONGUEUR_MATIERE,
  NiveauBloom,
  matiereDuCours,
} from '../../models/quiz-ia.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

interface QuestionEditee {
  enonce: string;
  choix: string[];
  /** Erreur de raisonnement de chaque distracteur (vide pour la bonne réponse), gardée telle quelle à l'édition. */
  erreurs: string[];
  bonne: number;
  explication: string;
  competence: string;
  niveauBloom?: NiveauBloom;
}

const MAX_QUESTIONS = 30;

function questionVide(): QuestionEditee {
  return { enonce: '', choix: ['', '', '', ''], erreurs: ['', '', '', ''], bonne: 0, explication: '', competence: '' };
}

/**
 * Studio : création ou modification manuelle d'un quiz. Le formateur choisit la matière et le cours, le type
 * (quiz de chapitre ou examen final), le niveau, puis écrit chaque question. Le quiz est enregistré en brouillon
 * par quiz-service ; la publication se fait depuis la liste du studio.
 */
@Component({
  selector: 'app-quiz-editeur',
  imports: [
    FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatRadioModule, MatButtonToggleModule,
    MatIconModule, BwButton, PageHeader,
  ],
  templateUrl: './quiz-editeur.html',
  styleUrl: './quiz-editeur.scss',
})
export class QuizEditeur {
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly quizService = inject(QuizService);
  private readonly catalogue = inject(CoursTitresService);

  /** Identifiant du quiz modifié ; null pour un nouveau quiz. */
  protected readonly quizId = Number(inject(ActivatedRoute).snapshot.paramMap.get('id')) || null;

  protected readonly matieres = MATIERES;
  protected readonly matiereAutre = MATIERE_AUTRE;
  protected readonly maxLongueurMatiere = MAX_LONGUEUR_MATIERE;
  protected readonly matiere = signal('Informatique');
  protected readonly precision = signal('');
  protected readonly cours = computed(() =>
    this.catalogue.cours().filter((c) => matiereDuCours(c.categorie) === this.matiere() || c.id === this.coursId()),
  );
  protected readonly coursId = signal<number | null>(null);
  private readonly matiereRenseignee = computed(() => this.matiere() !== MATIERE_AUTRE || this.precision().trim().length >= 2);
  protected readonly titre = signal('');
  protected readonly type = signal<TypeQuiz>('QUIZ');
  protected readonly difficultes = DIFFICULTES;
  protected readonly difficulte = signal<Difficulte>('INTERMEDIAIRE');
  protected readonly questions = signal<QuestionEditee[]>([questionVide()]);
  protected readonly lettres = ['A', 'B', 'C', 'D', 'E', 'F'];
  protected readonly maxQuestions = MAX_QUESTIONS;
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  /** Une question est complète quand l'énoncé et tous les choix sont remplis, et les choix tous différents. */
  protected readonly completes = computed(() => this.questions().map((q) => this.complete(q)));
  protected readonly valide = computed(
    () => this.coursId() !== null && this.matiereRenseignee() && this.titre().trim().length >= 3 && this.completes().every((c) => c),
  );

  constructor() {
    this.catalogue.charger();
    if (this.quizId) {
      this.quizService.detail(this.quizId).subscribe({
        next: (d) => this.remplir(d),
        error: (e: HttpErrorResponse) => this.erreur.set(e.error?.message ?? 'Quiz introuvable.'),
      });
    }
  }

  protected choisirMatiere(m: string): void {
    this.matiere.set(m);
    const premier = this.catalogue.cours().find((c) => matiereDuCours(c.categorie) === m);
    this.coursId.set(premier?.id ?? null);
  }

  protected modifier(i: number, changement: Partial<QuestionEditee>): void {
    this.questions.update((l) => l.map((q, j) => (j === i ? { ...q, ...changement } : q)));
  }

  protected modifierChoix(i: number, c: number, texte: string): void {
    const choix = [...this.questions()[i].choix];
    choix[c] = texte;
    this.modifier(i, { choix });
  }

  protected ajouter(): void {
    if (this.questions().length < MAX_QUESTIONS) this.questions.update((l) => [...l, questionVide()]);
  }

  protected supprimer(i: number): void {
    if (this.questions().length > 1) this.questions.update((l) => l.filter((_, j) => j !== i));
  }

  protected enregistrer(): void {
    const coursId = this.coursId();
    if (!this.valide() || coursId === null || this.envoi()) return;
    this.envoi.set(true);
    this.erreur.set(null);
    const corps: QuizRequest = {
      titre: this.titre().trim(),
      coursId,
      type: this.type(),
      matiere: this.matiere() === MATIERE_AUTRE ? this.precision().trim() : this.matiere(),
      difficulte: this.difficulte(),
      questions: this.questions().map((q) => ({
        enonce: q.enonce.trim(),
        niveauBloom: q.niveauBloom,
        competence: q.competence.trim() || undefined,
        explication: q.explication.trim() || undefined,
        choix: q.choix.map((texte, k) => ({
          texte: texte.trim(),
          estCorrecte: k === q.bonne,
          erreurAssociee: k === q.bonne ? undefined : q.erreurs[k]?.trim() || undefined,
        })),
      })),
    };
    const requete = this.quizId ? this.quizService.update(this.quizId, corps) : this.quizService.create(corps);
    requete.subscribe({
      next: (quiz) => {
        this.snack.open(`Quiz « ${quiz.titre} » enregistré en brouillon avec ${quiz.nbQuestions} questions`, 'OK', { duration: 3000 });
        this.router.navigateByUrl('/studio/quiz');
      },
      error: (e: HttpErrorResponse) => {
        this.envoi.set(false);
        const details = e.error?.details ? ' : ' + Object.values(e.error.details).join(', ') : '';
        this.erreur.set((e.error?.message ?? 'Enregistrement impossible.') + details);
      },
    });
  }

  private remplir(d: QuizDetail): void {
    const q = d.quiz;
    this.titre.set(q.titre);
    this.type.set(q.type);
    if (q.difficulte) this.difficulte.set(q.difficulte);
    if (q.matiere && (MATIERES as readonly string[]).includes(q.matiere)) {
      this.matiere.set(q.matiere);
    } else if (q.matiere) {
      this.matiere.set(MATIERE_AUTRE);
      this.precision.set(q.matiere);
    }
    this.coursId.set(q.coursId);
    this.questions.set(
      d.questions.map((qu) => ({
        enonce: qu.enonce,
        choix: qu.choix.map((c) => c.texte),
        erreurs: qu.choix.map((c) => c.erreurAssociee ?? ''),
        bonne: Math.max(0, qu.choix.findIndex((c) => c.estCorrecte)),
        explication: qu.explication ?? '',
        competence: qu.competence ?? '',
        niveauBloom: qu.niveauBloom,
      })),
    );
  }

  private complete(q: QuestionEditee): boolean {
    const choix = q.choix.map((c) => c.trim().toLowerCase());
    return q.enonce.trim().length > 0 && choix.every((c) => c.length > 0) && new Set(choix).size === choix.length;
  }
}
