import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import { COURS } from '../../../../core/demo/demo-data';
import { QUESTIONS_GENEREES, QuestionGeneree } from '../../../../core/demo/demo-plus';
import { AiBox, AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';

type Etat = 'vide' | 'generation' | 'resultat';
type Decision = 'acceptee' | 'rejetee' | 'attente';

/**
 * Studio : génération d'un QCM par l'IA à partir d'un chapitre. L'enseignant choisit les niveaux de
 * Bloom, la difficulté et le nombre de questions, puis accepte ou rejette chaque question proposée.
 * Les questions incohérentes sont déjà écartées par la vérification Self-Consistency.
 * À brancher : POST /api/ai/quiz/generer puis QuizService.create() avec les questions acceptées.
 */
@Component({
  selector: 'app-quiz-generateur',
  imports: [
    FormsModule, MatFormFieldModule, MatSelectModule, MatCheckboxModule, MatSliderModule, MatButtonToggleModule,
    MatIconModule, AiBox, AiLoading, BwButton, EmptyState, PageHeader,
  ],
  templateUrl: './quiz-generateur.html',
  styleUrl: './quiz-generateur.scss',
})
export class QuizGenerateur {
  private readonly snack = inject(MatSnackBar);
  private readonly router = inject(Router);

  protected readonly cours = COURS.filter((c) => c.chapitres.length > 0);
  protected coursId = 1;
  protected chapitre = 'Normalisation';
  protected readonly chapitres = computed(() => this.cours.find((c) => c.id === this.coursId)?.chapitres ?? []);
  protected readonly bloom = signal([
    { nom: 'Se souvenir', actif: true },
    { nom: 'Comprendre', actif: true },
    { nom: 'Appliquer', actif: false },
    { nom: 'Analyser', actif: true },
    { nom: 'Évaluer', actif: true },
    { nom: 'Créer', actif: false },
  ]);
  protected difficulte = 'mixte';
  protected nombre = 6;
  protected readonly lettres = ['A', 'B', 'C', 'D'];

  protected readonly etat = signal<Etat>('vide');
  protected readonly questions = signal<(QuestionGeneree & { decision: Decision })[]>([]);
  protected readonly acceptees = computed(() => this.questions().filter((q) => q.decision === 'acceptee').length);
  protected readonly ecartees = computed(() => this.questions().filter((q) => q.rejetee).length);

  protected basculerBloom(nom: string): void {
    this.bloom.update((b) => b.map((x) => (x.nom === nom ? { ...x, actif: !x.actif } : x)));
  }

  protected generer(): void {
    this.etat.set('generation');
    setTimeout(() => {
      this.questions.set(QUESTIONS_GENEREES.map((q) => ({ ...q, decision: q.rejetee ? 'rejetee' : 'attente' })));
      this.etat.set('resultat');
    }, 1600);
  }

  protected decider(q: QuestionGeneree, decision: Decision): void {
    this.questions.update((l) => l.map((x) => (x === q ? { ...x, decision } : x)));
  }

  protected toutAccepter(): void {
    this.questions.update((l) => l.map((x) => (x.rejetee ? x : { ...x, decision: 'acceptee' })));
  }

  protected enregistrer(): void {
    this.snack.open(`Quiz enregistré en brouillon avec ${this.acceptees()} questions`, 'OK', { duration: 3000 });
    this.router.navigateByUrl('/studio/quiz');
  }
}
