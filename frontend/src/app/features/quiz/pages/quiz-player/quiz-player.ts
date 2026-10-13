import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { QUESTIONS, QUIZ, coursParId } from '../../../../core/demo/demo-data';
import { AiBox, AiLoading, Badge, BwButton, EmptyState, Route } from '../../../../shared';
import { QuestionQcm, RemedierResponse, codeBloom } from '../../models/quiz-ia.model';
import { QuizIaService, messageErreurIa } from '../../services/quiz-ia.service';

type Phase = 'question' | 'correction' | 'resultat';
type EtatRattrapage = 'inactif' | 'chargement' | 'pret' | 'erreur';

/** Nombre de questions de rattrapage demandées à l'IA par passage du quiz (limite le coût LLM). */
const MAX_RATTRAPAGES = 3;

/**
 * Passage d'un quiz : une question par écran, correction immédiate, puis résultat et remédiation IA.
 * La remédiation appelle POST /api/ai/quiz/remedier avec la première erreur : explication, conseil et question
 * de rattrapage. Rien sur l'apprenant n'est envoyé, seulement la question et les deux réponses.
 * À brancher : les questions viennent de quiz-service.
 */
@Component({
  selector: 'app-quiz-player',
  imports: [RouterLink, MatIconModule, AiBox, AiLoading, Badge, BwButton, EmptyState, Route],
  templateUrl: './quiz-player.html',
  styleUrl: './quiz-player.scss',
})
export class QuizPlayer {
  private readonly ia = inject(QuizIaService);

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

  // Question de rattrapage : la bonne réponse n'est révélée qu'après validation.
  protected readonly rattrapage = signal<EtatRattrapage>('inactif');
  protected readonly remediation = signal<RemedierResponse | null>(null);
  protected readonly erreurIa = signal('');
  protected readonly choixRattrapage = signal<number | null>(null);
  protected readonly rattrapageValide = signal(false);
  protected readonly essais = signal(0);
  protected readonly essaisRestants = computed(() => MAX_RATTRAPAGES - this.essais());
  private appelIa?: Subscription;
  protected readonly questionRattrapage = computed<QuestionQcm | undefined>(() => this.remediation()?.questionRattrapage);
  protected readonly rattrapageJuste = computed(() => {
    const i = this.choixRattrapage();
    return i !== null && this.questionRattrapage()?.choix[i]?.estCorrecte === true;
  });

  protected essayerRattrapage(): void {
    const e = this.erreurs()[0];
    if (!e || this.rattrapage() === 'chargement' || this.essaisRestants() <= 0) return;
    this.essais.update((n) => n + 1);
    this.rattrapage.set('chargement');
    this.erreurIa.set('');
    this.choixRattrapage.set(null);
    this.rattrapageValide.set(false);
    this.appelIa = this.ia
      .remedier({
        notion: e.notion,
        niveauBloom: codeBloom(e.bloom),
        enonce: e.enonce,
        choixDonne: e.options[e.donnee],
        bonneReponse: e.options[e.bonne],
        explication: e.explication,
      })
      .subscribe({
        next: (r) => {
          this.remediation.set(r);
          this.rattrapage.set('pret');
        },
        error: (err) => {
          this.erreurIa.set(messageErreurIa(err));
          this.rattrapage.set('erreur');
        },
      });
  }

  protected choisirRattrapage(i: number): void {
    if (!this.rattrapageValide()) this.choixRattrapage.set(i);
  }

  protected validerRattrapage(): void {
    if (this.choixRattrapage() !== null) this.rattrapageValide.set(true);
  }

  protected etatOptionRattrapage(i: number): 'bonne' | 'fausse' | 'choisie' | '' {
    const q = this.questionRattrapage();
    if (this.rattrapageValide() && q) {
      if (q.choix[i].estCorrecte) return 'bonne';
      if (i === this.choixRattrapage()) return 'fausse';
      return '';
    }
    return i === this.choixRattrapage() ? 'choisie' : '';
  }

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
    this.appelIa?.unsubscribe();
    this.rattrapage.set('inactif');
    this.remediation.set(null);
    this.erreurIa.set('');
    this.choixRattrapage.set(null);
    this.rattrapageValide.set(false);
    this.essais.set(0);
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
