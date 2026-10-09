import { DestroyRef, Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { QUESTIONS, coursParId } from '../../../../core/demo/demo-data';
import { CERTIFICATS, Certificat, ETAT_EXAMEN, ajouterCertificat } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';

type Phase = 'intro' | 'examen' | 'resultat';

/**
 * Examen final d'un cours. Il se débloque quand tous les chapitres sont terminés.
 * Différences avec un quiz de chapitre : chronomètre, pas de correction question par question,
 * nombre de tentatives limité, et un certificat créé si le score atteint le seuil.
 * À brancher : QuizService.examenFinal(coursId), POST de la tentative, puis certificat créé par quiz-service.
 */
@Component({
  selector: 'app-examen-final',
  imports: [RouterLink, MatIconModule, BwButton, EmptyState],
  templateUrl: './examen-final.html',
  styleUrls: ['../quiz-player/quiz-player.scss', './examen-final.scss'],
})
export class ExamenFinalPage {
  readonly coursId = input(0, { transform: numberAttribute });

  protected readonly cours = computed(() => coursParId(this.coursId()));
  protected readonly questions = QUESTIONS;
  protected readonly lettres = ['A', 'B', 'C', 'D'];
  protected readonly etat = computed(() => ETAT_EXAMEN[this.coursId()] ?? { tentativesFaites: 0, meilleurScore: null });
  protected readonly dejaCertifie = computed(() => CERTIFICATS.find((c) => c.coursId === this.coursId()));
  protected readonly restants = computed(() => this.cours()?.chapitres.filter((ch) => !ch.fait).length ?? 0);
  protected readonly tentativesRestantes = computed(() => (this.cours()?.examen.tentatives ?? 0) - this.etat().tentativesFaites - this.tentativesSession());

  protected readonly phase = signal<Phase>('intro');
  protected readonly index = signal(0);
  protected readonly reponses = signal<(number | null)[]>([]);
  protected readonly secondes = signal(0);
  private readonly tentativesSession = signal(0);
  protected readonly certificat = signal<Certificat | null>(null);
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly question = computed(() => this.questions[this.index()]);
  protected readonly repondues = computed(() => this.reponses().filter((r) => r !== null).length);
  protected readonly score = computed(() => {
    const justes = this.reponses().filter((r, i) => r === this.questions[i].bonne).length;
    return Math.round((justes / this.questions.length) * 100);
  });
  protected readonly reussi = computed(() => this.score() >= (this.cours()?.examen.seuil ?? 70));
  protected readonly chrono = computed(() => {
    const s = this.secondes();
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  });
  /** Notions ratées, pour dire quoi revoir avant une nouvelle tentative. */
  protected readonly aRevoir = computed(() => [...new Set(this.questions.filter((q, i) => this.reponses()[i] !== q.bonne).map((q) => q.notion))]);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.arreter());
  }

  protected commencer(): void {
    this.reponses.set(this.questions.map(() => null));
    this.index.set(0);
    this.secondes.set((this.cours()?.examen.duree ?? 30) * 60);
    this.phase.set('examen');
    this.arreter();
    this.timer = setInterval(() => {
      this.secondes.update((s) => s - 1);
      if (this.secondes() <= 0) this.terminer();
    }, 1000);
  }

  protected choisir(i: number): void {
    this.reponses.update((r) => r.map((v, k) => (k === this.index() ? i : v)));
  }

  protected aller(i: number): void {
    this.index.set(i);
  }

  protected terminer(): void {
    this.arreter();
    this.tentativesSession.update((n) => n + 1);
    if (this.reussi() && !this.dejaCertifie()) {
      this.certificat.set(ajouterCertificat(this.coursId(), this.score()));
    }
    this.phase.set('resultat');
  }

  private arreter(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
