import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Component, computed, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AiLoading, BwButton, EmptyState } from '../../../../shared';
import { Certificat, Passage, Quiz, TentativeResultat } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

type Phase = 'chargement' | 'introuvable' | 'intro' | 'examen' | 'envoi' | 'resultat';

/**
 * Examen final d'un cours. Différences avec un quiz de chapitre : chronomètre, pas de correction question par
 * question, nombre de tentatives limité. quiz-service corrige la tentative et crée le certificat si le score
 * atteint le seuil.
 */
@Component({
  selector: 'app-examen-final',
  imports: [RouterLink, MatIconModule, AiLoading, BwButton, EmptyState],
  templateUrl: './examen-final.html',
  styleUrls: ['../quiz-player/quiz-player.scss', './examen-final.scss'],
})
export class ExamenFinalPage {
  private readonly quizService = inject(QuizService);
  protected readonly catalogue = inject(CoursTitresService);

  readonly coursId = input(0, { transform: numberAttribute });

  protected readonly titreCours = computed(() => this.catalogue.titre(this.coursId()));
  protected readonly examen = signal<Quiz | null>(null);
  protected readonly passage = signal<Passage | null>(null);
  protected readonly dejaCertifie = signal<Certificat | null>(null);
  protected readonly questions = computed(() => this.passage()?.questions ?? []);
  protected readonly lettres = ['A', 'B', 'C', 'D', 'E', 'F'];
  /** null = tentatives illimitées. */
  protected readonly tentativesRestantes = computed(() => this.passage()?.tentativesRestantes ?? null);

  protected readonly phase = signal<Phase>('chargement');
  protected readonly erreurEnvoi = signal('');
  protected readonly index = signal(0);
  protected readonly reponses = signal<(number | null)[]>([]);
  protected readonly secondes = signal(0);
  protected readonly resultat = signal<TentativeResultat | null>(null);
  private timer: ReturnType<typeof setInterval> | null = null;

  protected readonly question = computed(() => this.questions()[this.index()]);
  protected readonly repondues = computed(() => this.reponses().filter((r) => r !== null).length);
  protected readonly chrono = computed(() => {
    const s = Math.max(0, this.secondes());
    return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
  });
  /** Compétences ratées, pour dire quoi revoir avant une nouvelle tentative. */
  protected readonly aRevoir = computed(() => [
    ...new Set((this.resultat()?.corrections ?? []).filter((c) => !c.correcte && c.competence).map((c) => c.competence as string)),
  ]);

  constructor() {
    this.catalogue.charger();
    inject(DestroyRef).onDestroy(() => this.arreter());
    effect(() => this.charger(this.coursId()));
  }

  protected commencer(): void {
    const p = this.passage();
    if (!p) return;
    this.reponses.set(p.questions.map(() => null));
    this.index.set(0);
    this.erreurEnvoi.set('');
    this.secondes.set(p.quiz.dureeMinutes * 60);
    this.phase.set('examen');
    this.arreter();
    this.timer = setInterval(() => {
      this.secondes.update((s) => s - 1);
      if (this.secondes() <= 0) this.terminer();
    }, 1000);
  }

  protected choisir(optionId: number): void {
    this.reponses.update((r) => r.map((v, k) => (k === this.index() ? optionId : v)));
  }

  protected aller(i: number): void {
    this.index.set(i);
  }

  protected terminer(): void {
    const p = this.passage();
    if (!p || this.phase() !== 'examen') return;
    this.arreter();
    this.phase.set('envoi');
    this.quizService
      .soumettre(p.quiz.id, { reponses: p.questions.map((q, i) => ({ questionId: q.id, optionId: this.reponses()[i] ?? null })) })
      .subscribe({
        next: (r) => {
          this.resultat.set(r);
          this.passage.update((x) => (x && x.tentativesRestantes != null ? { ...x, tentativesRestantes: Math.max(0, x.tentativesRestantes - 1) } : x));
          this.phase.set('resultat');
        },
        error: (err: HttpErrorResponse) => {
          // Le temps est peut-être écoulé : on laisse l'apprenant renvoyer ses réponses.
          this.erreurEnvoi.set(err.error?.message ?? 'Envoi impossible, réessaie.');
          this.phase.set('examen');
        },
      });
  }

  protected retourConsignes(): void {
    this.resultat.set(null);
    this.phase.set('intro');
  }

  private charger(coursId: number): void {
    this.phase.set('chargement');
    this.resultat.set(null);
    if (!coursId) {
      this.phase.set('introuvable');
      return;
    }
    this.quizService.mesCertificats().subscribe({
      next: (l) => this.dejaCertifie.set(l.find((c) => c.coursId === coursId) ?? null),
      error: () => this.dejaCertifie.set(null),
    });
    this.quizService.findAll(coursId, 'EXAMEN').subscribe({
      next: (liste) => {
        const examen = liste.find((q) => q.statut === 'PUBLIE') ?? null;
        this.examen.set(examen);
        if (!examen) {
          this.phase.set('introuvable');
          return;
        }
        this.quizService.passage(examen.id).subscribe({
          next: (p) => {
            this.passage.set(p);
            this.phase.set('intro');
          },
          // Plus de tentative ou examen retiré : on affiche les consignes sans bouton « Commencer ».
          error: () => {
            this.passage.set(null);
            this.phase.set('intro');
          },
        });
      },
      error: () => this.phase.set('introuvable'),
    });
  }

  private arreter(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }
}
