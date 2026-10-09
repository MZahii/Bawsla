import { Component, signal } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';

import { ERREURS_IA, MODELES_IA, QUOTA_IA, REQUETES_IA } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { BwChart, Kpi, PageHeader, ProgressRing } from '../../../../shared/ui';

/**
 * Back-office : supervision de l'IA. Métriques de chaque modèle (une par module), volume de
 * requêtes par module, quota mensuel, erreurs récentes et interrupteurs par fonctionnalité.
 * À brancher : GET /api/ai/admin/metriques (ai-service, app/core) et les journaux d'appels LLM.
 */
@Component({
  selector: 'app-admin-ia',
  imports: [MatIconModule, MatTableModule, MatSlideToggleModule, MatButtonToggleModule, BwButton, BwChart, Kpi, PageHeader, ProgressRing],
  templateUrl: './admin-ia.html',
})
export class AdminIa {
  protected readonly modeles = MODELES_IA;
  protected readonly quota = QUOTA_IA;
  protected readonly quotaPct = Math.round((QUOTA_IA.utilise / QUOTA_IA.limite) * 100);
  protected readonly erreurs = ERREURS_IA;
  protected readonly colErreurs = ['quand', 'module', 'type', 'detail'];
  protected readonly vue = signal<'stacked' | 'line'>('stacked');
  protected readonly requetes = {
    labels: REQUETES_IA.labels,
    series: [
      { label: 'Cours', data: REQUETES_IA.cours },
      { label: 'Quiz', data: REQUETES_IA.quiz },
      { label: 'Forum', data: REQUETES_IA.forum },
    ],
  };
  protected readonly total = REQUETES_IA.cours.reduce((t, v, i) => t + v + REQUETES_IA.quiz[i] + REQUETES_IA.forum[i], 0);
  protected readonly fonctions = [
    { nom: 'Résumés de chapitres et audio', module: 'Cours', actif: true },
    { nom: 'Génération de QCM', module: 'Quiz', actif: true },
    { nom: 'Remédiation après un quiz', module: 'Quiz', actif: true },
    { nom: 'Réponses du forum (RAG)', module: 'Forum', actif: true },
    { nom: 'Prédiction du risque d’échec', module: 'User', actif: false },
  ];
}
