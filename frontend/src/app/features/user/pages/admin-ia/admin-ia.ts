import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, ChartSerie, Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { MetriquesIa } from '../../models/ia.model';
import { IaUserService } from '../../services/ia-user.service';

/** Libellés des variables des modèles (noms des colonnes du jeu de données). */
const VARIABLES: Record<string, string> = {
  noteMoyenne: 'Note moyenne aux quiz',
  tauxReussite: 'Bonnes réponses',
  nbQuiz: 'Quiz passés',
  nbNotionsRatees: 'Notions ratées',
  nbErreurs: 'Erreurs aux quiz',
  progressionMoyenne: 'Progression',
  tempsPasseMinutes: 'Temps passé',
  nbCoursSuivis: 'Cours suivis',
  nbCoursTermines: 'Cours terminés',
  joursActifs30j: 'Jours actifs (30 j)',
  joursDepuisDerniereConnexion: 'Jours sans connexion',
};

/**
 * Back-office : supervision des modèles de Machine Learning du module User (ai-service), sur les vraies métriques
 * de GET /api/ai/user/metriques : risque d'échec (Random Forest), profils (K-Means) et recommandation (filtrage
 * collaboratif). Chaque modèle est évalué sur des apprenants qu'il n'a jamais vus et comparé à des références.
 * « Réentraîner » relance l'entraînement des trois modèles (POST /api/ai/user/entrainer).
 */
@Component({
  selector: 'app-admin-ia',
  imports: [MatIconModule, BwButton, EmptyState, BwChart, Kpi, PageHeader, Skeleton],
  templateUrl: './admin-ia.html',
  styleUrl: './admin-ia.scss',
})
export class AdminIa implements OnInit {
  private readonly api = inject(IaUserService);
  private readonly snack = inject(MatSnackBar);

  protected readonly m = signal<MetriquesIa | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly entrainement = signal(false);

  protected readonly dateEntrainement = computed(() => {
    const m = this.m();
    return m ? new Intl.DateTimeFormat('fr-FR', { dateStyle: 'long', timeStyle: 'short' }).format(new Date(m.dateEntrainement)) : '';
  });

  /** Random Forest face aux deux références, en validation croisée. */
  protected readonly comparaison = computed(() => {
    const c = this.m()?.risque.comparaison;
    if (!c) return null;
    const modeles: [keyof typeof c, string, 1 | 2 | 3][] = [
      ['classeMajoritaire', 'Classe majoritaire', 3],
      ['regressionLogistique', 'Régression logistique', 2],
      ['randomForest', 'Random Forest', 1],
    ];
    return {
      labels: ['Accuracy', 'F1 (échec)', 'AUC'],
      series: modeles.map(([cle, label, color]): ChartSerie => {
        const v = c[cle].validationCroisee;
        return { label, color, data: [v.accuracy, v.f1, v.auc].map((x) => Math.round(x * 100)) };
      }),
    };
  });

  protected readonly importances = computed(() => {
    const imp = this.m()?.risque.importances;
    if (!imp) return null;
    const lignes = Object.entries(imp).slice(0, 8);
    return {
      labels: lignes.map(([v]) => VARIABLES[v] ?? v),
      series: [{ label: 'Importance (%)', data: lignes.map(([, x]) => Math.round(x * 1000) / 10), color: 1 } as ChartSerie],
    };
  });

  protected readonly coude = computed(() => {
    const c = this.m()?.profils.courbes;
    return c ? { labels: c.map((p) => `k = ${p.k}`), series: [{ label: 'Inertie', data: c.map((p) => p.inertie), color: 2 } as ChartSerie] } : null;
  });

  protected readonly silhouette = computed(() => {
    const c = this.m()?.profils.courbes;
    return c ? { labels: c.map((p) => `k = ${p.k}`), series: [{ label: 'Silhouette', data: c.map((p) => p.silhouette), color: 1 } as ChartSerie] } : null;
  });

  protected readonly recommandation = computed(() => {
    const ev = this.m()?.recommandation.evaluation;
    if (!ev) return null;
    const methodes: [keyof typeof ev & ('hybride' | 'collaboratifSeul' | 'popularite'), string, 1 | 2 | 3][] = [
      ['popularite', 'Popularité', 3],
      ['collaboratifSeul', 'Collaboratif seul', 2],
      ['hybride', 'Hybride (retenu)', 1],
    ];
    return {
      labels: ['Trouvé dans le top 3', 'Trouvé dans le top 5', 'Catalogue couvert (top 3)'],
      series: methodes.map(([cle, label, color]): ChartSerie => {
        const r = ev[cle];
        return { label, color, data: [r.reussiteA3, r.reussiteA5, r.couvertureA3].map((x) => Math.round(x * 100)) };
      }),
    };
  });

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.api.metriques().subscribe({
      next: (m) => this.m.set(m),
      error: (err) => this.erreur.set(errorMessage(err, 'Métriques indisponibles')),
    });
  }

  protected reentrainer(): void {
    this.entrainement.set(true);
    this.api.entrainer().subscribe({
      next: (m) => {
        this.m.set(m);
        this.entrainement.set(false);
        this.snack.open(`Modèles réentraînés en ${this.nombre(m.dureeSecondes, 1)} s`, 'OK', { duration: 4000 });
      },
      error: (err) => {
        this.entrainement.set(false);
        this.snack.open(errorMessage(err, 'Entraînement impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  /** 0.825 -> « 82,5 % ». */
  protected pct(x: number | undefined, decimales = 1): string {
    return x === undefined ? '—' : `${this.nombre(x * 100, decimales)} %`;
  }

  protected nombre(x: number | undefined, decimales = 2): string {
    return x === undefined ? '—' : new Intl.NumberFormat('fr-FR', { maximumFractionDigits: decimales }).format(x);
  }

  protected libelle(variable: string): string {
    return VARIABLES[variable] ?? variable;
  }

  protected pourcentEchec(nom: string): string {
    const t = this.m()?.profils.tauxEchecParGroupe?.[nom];
    return t === undefined ? '—' : this.pct(t, 0);
  }
}
