import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';
import { Certificat, HistoriqueLigne, Quiz } from '../../models/quiz.model';
import { tonScore } from '../../models/ton-score';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/**
 * Espace apprenant : « Évaluations ». Pour chaque cours, les quiz de chapitre publiés puis l'examen final,
 * qui donne le certificat. La comparaison se fait avec les autres apprenants du même cours (pas de classe).
 */
@Component({
  selector: 'app-quiz-list',
  imports: [DatePipe, RouterLink, MatIconModule, BwButton, EmptyState, PageHeader, Kpi, BwChart],
  templateUrl: './quiz-list.html',
  styleUrl: './quiz-list.scss',
})
export class QuizList {
  private readonly quizService = inject(QuizService);
  protected readonly catalogue = inject(CoursTitresService);

  protected readonly chargement = signal(true);
  protected readonly erreur = signal(false);
  private readonly quiz = signal<Quiz[]>([]);
  private readonly examens = signal<Quiz[]>([]);
  protected readonly historique = signal<HistoriqueLigne[]>([]);
  protected readonly HISTORIQUE_COURT = 6;
  protected readonly toutHistorique = signal(false);
  protected readonly historiqueAffiche = computed(() =>
    this.toutHistorique() ? this.historique() : this.historique().slice(0, this.HISTORIQUE_COURT),
  );
  private readonly certificats = signal<Certificat[]>([]);

  /** Meilleur score de l'apprenant par quiz. */
  private readonly meilleurs = computed(() => {
    const m = new Map<number, number>();
    for (const h of this.historique()) m.set(h.quizId, Math.max(m.get(h.quizId) ?? 0, h.pourcentage));
    return m;
  });

  protected readonly parcours = computed(() => {
    const coursIds = [...new Set([...this.quiz(), ...this.examens()].map((q) => q.coursId))];
    return coursIds.map((coursId) => {
      const quiz = this.quiz()
        .filter((q) => q.coursId === coursId)
        .map((q) => ({ ...q, score: this.meilleurs().get(q.id) ?? null }));
      const examen = this.examens().find((q) => q.coursId === coursId) ?? null;
      const certificat = this.certificats().find((c) => c.coursId === coursId) ?? null;
      const tentatives = examen ? this.historique().filter((h) => h.quizId === examen.id).length : 0;
      const restantes = examen && examen.tentativesMax > 0 ? Math.max(0, examen.tentativesMax - tentatives) : null;
      return { coursId, quiz, faits: quiz.filter((q) => q.score !== null).length, examen, certificat, restantes };
    });
  });

  protected readonly moyenne = computed(() => {
    const h = this.historique();
    return h.length ? Math.round(h.reduce((t, x) => t + x.pourcentage, 0) / h.length) : null;
  });
  /** Quiz de chapitre distincts tentés au moins une fois (sans les examens ni les tentatives répétées). */
  protected readonly nbQuizPasses = computed(() => new Set(this.historique().filter((h) => h.type === 'QUIZ').map((h) => h.quizId)).size);
  protected readonly examensDispo = computed(() => this.parcours().filter((p) => p.examen && !p.certificat && p.restantes !== 0).length);
  protected readonly nbCertificats = computed(() => this.certificats().length);
  /** Dernières tentatives des quiz pour lesquels une moyenne des autres apprenants existe. */
  protected readonly comparaison = computed(() => {
    const lignes = this.historique().filter((h) => h.moyenneAutres != null).slice(0, 8).reverse();
    return {
      labels: lignes.map((h) => h.titre),
      series: [
        // « Toi » en bordeaux (couleur de la marque), les autres en bleu : la couleur suit la série.
        { label: 'Toi', data: lignes.map((h) => h.pourcentage), color: 2 as const },
        { label: 'Autres apprenants du cours', data: lignes.map((h) => h.moyenneAutres as number), color: 1 as const },
      ],
    };
  });

  constructor() {
    this.catalogue.charger();
    forkJoin({
      quiz: this.quizService.findAll(undefined, 'QUIZ'),
      examens: this.quizService.findAll(undefined, 'EXAMEN'),
      historique: this.quizService.historique(),
      certificats: this.quizService.mesCertificats(),
    }).subscribe({
      next: (r) => {
        this.quiz.set(r.quiz.filter((q) => q.statut === 'PUBLIE'));
        this.examens.set(r.examens.filter((q) => q.statut === 'PUBLIE'));
        this.historique.set(r.historique);
        this.certificats.set(r.certificats);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set(true);
        this.chargement.set(false);
      },
    });
  }

  /** Préfixe « Quiz : » sans le doubler quand le titre (souvent généré par l'IA) le contient déjà. */
  protected libelle(titre: string): string {
    return /^quiz\s*:/i.test(titre) ? titre : `Quiz : ${titre}`;
  }

  protected readonly ton = tonScore;
}
