import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { COURS } from '../../../../core/demo/demo-data';
import { BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import {
  DIFFICULTES,
  Difficulte,
  MATIERES,
  MATIERE_AUTRE,
  MAX_LONGUEUR_MATIERE,
  matiereDuCours,
} from '../../models/quiz-ia.model';

interface QuestionEditee {
  enonce: string;
  choix: string[];
  bonne: number;
  explication: string;
}

const MAX_QUESTIONS = 30;

function questionVide(): QuestionEditee {
  return { enonce: '', choix: ['', '', '', ''], bonne: 0, explication: '' };
}

/**
 * Studio : création manuelle d'un quiz (sans IA). Le formateur choisit la matière et le cours, le niveau
 * de difficulté, puis écrit chaque question : énoncé, 4 choix, la bonne réponse et une explication.
 * À brancher : QuizService.create() (quiz-service) ; le quiz est enregistré en brouillon.
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

  protected readonly matieres = MATIERES;
  protected readonly matiereAutre = MATIERE_AUTRE;
  protected readonly maxLongueurMatiere = MAX_LONGUEUR_MATIERE;
  protected readonly matiere = signal('Informatique');
  protected readonly precision = signal('');
  protected readonly cours = computed(() => COURS.filter((c) => matiereDuCours(c.categorie) === this.matiere()));
  protected readonly coursId = signal<number | null>(this.cours()[0]?.id ?? null);
  private readonly matiereRenseignee = computed(() => this.matiere() !== MATIERE_AUTRE || this.precision().trim().length >= 2);
  protected readonly titre = signal('');
  protected readonly difficultes = DIFFICULTES;
  protected readonly difficulte = signal<Difficulte>('INTERMEDIAIRE');
  protected readonly questions = signal<QuestionEditee[]>([questionVide()]);
  protected readonly lettres = ['A', 'B', 'C', 'D'];
  protected readonly maxQuestions = MAX_QUESTIONS;

  /** Une question est complète quand l'énoncé et les 4 choix sont remplis, et les choix tous différents. */
  protected readonly completes = computed(() => this.questions().map((q) => this.complete(q)));
  protected readonly valide = computed(
    () => this.coursId() !== null && this.matiereRenseignee() && this.titre().trim().length >= 3 && this.completes().every((c) => c),
  );

  protected choisirMatiere(m: string): void {
    this.matiere.set(m);
    this.coursId.set(this.cours()[0]?.id ?? null);
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
    if (!this.valide()) return;
    this.snack.open(`Quiz « ${this.titre().trim()} » enregistré en brouillon avec ${this.questions().length} questions`, 'OK', {
      duration: 3000,
    });
    this.router.navigateByUrl('/studio/quiz');
  }

  private complete(q: QuestionEditee): boolean {
    const choix = q.choix.map((c) => c.trim().toLowerCase());
    return q.enonce.trim().length > 0 && choix.every((c) => c.length > 0) && new Set(choix).size === choix.length;
  }
}
