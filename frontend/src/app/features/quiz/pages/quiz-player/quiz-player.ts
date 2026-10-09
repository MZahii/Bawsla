import { Component, computed, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { QUESTIONS, QUIZ, coursParId } from '../../../../core/demo/demo-data';
import { AiBox, Badge, BwButton, EmptyState, Route } from '../../../../shared';

type Phase = 'question' | 'correction' | 'resultat';

/**
 * Passage d'un quiz : une question par écran, correction immédiate, puis résultat et remédiation IA.
 * À brancher : les questions viennent de quiz-service, la remédiation de /api/ai/quiz.
 */
@Component({
  selector: 'app-quiz-player',
  imports: [RouterLink, MatIconModule, AiBox, Badge, BwButton, EmptyState, Route],
  templateUrl: './quiz-player.html',
  styleUrl: './quiz-player.scss',
})
export class QuizPlayer {
  readonly id = input(0, { transform: numberAttribute });

  protected readonly quiz = computed(() => QUIZ.find((q) => q.id === this.id()));
  protected readonly cours = computed(() => coursParId(this.quiz()?.coursId ?? 0));
  protected readonly questions = QUESTIONS;
  protected readonly lettres = ['A', 'B', 'C', 'D'];

  protected readonly index = signal(0);
  protected readonly choix = signal<number | null>(null);
  protected readonly phase = signal<Phase>('question');
  protected readonly reponses = signal<number[]>([]);

  protected readonly question = computed(() => this.questions[this.index()]);
  protected readonly juste = computed(() => this.choix() === this.question().bonne);

  /** L'itinéraire des questions : répondues (pleines) ou ratées (anneau d'erreur). */
  protected readonly etapes = computed(() =>
    this.questions.map((q, i) => {
      const r = this.reponses()[i];
      return { titre: `Question ${i + 1}`, fait: r !== undefined, rate: r !== undefined && r !== q.bonne };
    }),
  );

  protected readonly score = computed(() =>
    this.reponses().filter((r, i) => r === this.questions[i].bonne).length,
  );
  protected readonly pourcentage = computed(() => Math.round((this.score() / this.questions.length) * 100));
  protected readonly erreurs = computed(() =>
    this.questions
      .map((q, i) => ({ ...q, numero: i + 1, donnee: this.reponses()[i] }))
      .filter((q) => q.donnee !== q.bonne),
  );

  protected readonly mention = computed(() => {
    const p = this.pourcentage();
    if (p === 100) return 'Parfait, tu maîtrises ce quiz.';
    if (p >= 70) return 'Bien joué, encore un petit effort sur les points ci-dessous.';
    if (p >= 40) return 'Tu es sur la bonne route. Revois les notions ci-dessous.';
    return 'Ce quiz révèle un point à retravailler. C’est exactement à ça qu’il sert.';
  });

  protected choisir(i: number): void {
    if (this.phase() === 'question') this.choix.set(i);
  }

  protected valider(): void {
    if (this.choix() === null) return;
    this.reponses.update((r) => [...r, this.choix()!]);
    this.phase.set('correction');
  }

  protected suivante(): void {
    if (this.index() === this.questions.length - 1) {
      this.phase.set('resultat');
      return;
    }
    this.index.update((i) => i + 1);
    this.choix.set(null);
    this.phase.set('question');
  }

  protected recommencer(): void {
    this.index.set(0);
    this.choix.set(null);
    this.reponses.set([]);
    this.phase.set('question');
  }

  /** Apparence d'une option selon la phase : choisie, puis bonne ou fausse à la correction. */
  protected etatOption(i: number): 'bonne' | 'fausse' | 'choisie' | '' {
    const q = this.question();
    if (this.phase() === 'correction') {
      if (i === q.bonne) return 'bonne';
      if (i === this.choix()) return 'fausse';
      return '';
    }
    return i === this.choix() ? 'choisie' : '';
  }
}
