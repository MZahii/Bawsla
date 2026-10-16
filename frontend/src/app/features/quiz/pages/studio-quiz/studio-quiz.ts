import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';
import { StatistiquesQuiz, StudioLigne } from '../../models/quiz.model';
import { CoursTitresService } from '../../services/cours-titres.service';
import { QuizService } from '../../services/quiz.service';

/** Studio : les quiz du formateur, leurs résultats, et l'analyse des questions les plus ratées. */
@Component({
  selector: 'app-studio-quiz',
  imports: [RouterLink, MatTableModule, MatMenuModule, MatIconModule, BwButton, EmptyState, PageHeader, Kpi, BwChart],
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
    series: [{ label: 'Tentatives', data: this.stats()?.distribution ?? [0, 0, 0, 0, 0] }],
  }));

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

  protected ton(v: number | null | undefined): string {
    if (v == null) return '';
    return v >= 70 ? 'ok' : v >= 60 ? 'warn' : 'ko';
  }

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
