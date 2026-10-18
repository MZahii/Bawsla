import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';
import { StatistiquesQuiz, StudioLigne } from '../../models/quiz.model';
import { tonScore } from '../../models/ton-score';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/** Studio : les quiz du formateur, leurs résultats, et l'analyse des questions les plus ratées. */
@Component({
  selector: 'app-studio-quiz',
  imports: [RouterLink, FormsModule, MatButtonToggleModule, MatTableModule, MatMenuModule, MatIconModule, BwButton, EmptyState, PageHeader, Kpi, BwChart],
  templateUrl: './studio-quiz.html',
})
export class StudioQuiz {
  private readonly quizService = inject(QuizService);
  private readonly router = inject(Router);
  protected readonly cours = inject(CoursTitresService);

  protected readonly colonnes = ['titre', 'questions', 'tentatives', 'moyenne', 'reussite', 'statut', 'actions'];
  protected readonly lignes = signal<StudioLigne[]>([]);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly selection = signal<StudioLigne | null>(null);
  protected readonly stats = signal<StatistiquesQuiz | null>(null);
  protected readonly filtre = signal<'TOUS' | 'QUIZ' | 'EXAMEN'>('TOUS');
  protected readonly q = signal('');

  /** Lignes affichées : filtre par type, puis recherche dans le titre du quiz et celui du cours. */
  protected readonly lignesFiltrees = computed(() => {
    const f = this.filtre();
    const q = this.q().trim().toLowerCase();
    return this.lignes().filter(
      (l) =>
        (f === 'TOUS' || l.type === f) &&
        (!q || l.titre.toLowerCase().includes(q) || this.cours.titre(l.coursId).toLowerCase().includes(q)),
    );
  });

  protected readonly nbPublies = computed(() => this.lignes().filter((l) => l.statut === 'PUBLIE').length);
  protected readonly nbTentatives = computed(() => this.lignes().reduce((s, l) => s + l.nbTentatives, 0));
  /** Moyenne des scores, pondérée par le nombre de tentatives de chaque quiz. */
  protected readonly moyenneGlobale = computed(() => {
    const avec = this.lignes().filter((l) => l.moyenne != null && l.nbTentatives > 0);
    const n = avec.reduce((s, l) => s + l.nbTentatives, 0);
    return n === 0 ? null : Math.round(avec.reduce((s, l) => s + (l.moyenne ?? 0) * l.nbTentatives, 0) / n);
  });
  protected readonly nbQuestions = computed(() => this.lignes().reduce((s, l) => s + l.nbQuestions, 0));

  protected readonly distribution = computed(() => ({
    labels: ['0-19', '20-39', '40-59', '60-79', '80-100'],
    // Couleur 2 (bordeaux) : celle de la marque, plutôt que le bleu par défaut de la première série.
    series: [{ label: 'Tentatives', data: this.stats()?.distribution ?? [0, 0, 0, 0, 0], color: 2 as const }],
  }));

  /** Seules les questions avec au moins une erreur, la plus ratée en premier. */
  protected readonly questionsRatees = computed(() =>
    (this.stats()?.questions ?? []).filter((q) => q.nbReponses > 0 && q.tauxErreur > 0).sort((a, b) => b.tauxErreur - a.tauxErreur),
  );

  constructor() {
    this.cours.charger();
    this.recharger();
  }

  protected selectionner(l: StudioLigne): void {
    this.selection.set(l);
    this.stats.set(null);
    this.quizService.statistiques(l.id).subscribe({ next: (s) => this.stats.set(s) });
  }

  protected modifier(l: StudioLigne): void {
    this.router.navigate(['/studio/quiz', l.id]);
  }

  protected basculer(l: StudioLigne): void {
    const action = l.statut === 'PUBLIE' ? this.quizService.depublier(l.id) : this.quizService.publier(l.id);
    action.subscribe({ next: () => this.recharger(), error: (e) => this.erreur.set(message(e)) });
  }

  protected supprimer(l: StudioLigne): void {
    if (!confirm(`Supprimer le quiz « ${l.titre} » ?`)) return;
    this.quizService.delete(l.id).subscribe({
      next: () => {
        if (this.selection()?.id === l.id) this.selection.set(null);
        this.recharger();
      },
      error: (e) => this.erreur.set(message(e)),
    });
  }

  protected readonly ton = tonScore;

  private recharger(): void {
    this.erreur.set(null);
    this.quizService.mesQuiz().subscribe({
      next: (l) => {
        this.lignes.set(l);
        this.chargement.set(false);
        const sel = this.selection();
        const garder = sel ? l.find((x) => x.id === sel.id) : l[0];
        if (garder) this.selectionner(garder);
      },
      error: (e) => {
        this.chargement.set(false);
        this.erreur.set(message(e));
      },
    });
  }
}

function message(e: HttpErrorResponse): string {
  return e.error?.message ?? 'Le service des quiz est indisponible.';
}
