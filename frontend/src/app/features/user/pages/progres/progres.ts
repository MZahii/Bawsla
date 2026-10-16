import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { IaApprenant, StatutSource } from '../../models/ia.model';
import { IaGeneree } from '../../components/ia-generee/ia-generee';
import { IaGenerativeService } from '../../services/ia-generative.service';
import { IaUserService } from '../../services/ia-user.service';

/** Sources de données présentées à l'apprenant, en mots simples (clés de IndicateursResponse.sources). */
/** `sinon` : clé dont reprendre le statut quand la source est absente (temps passé, lu avec les inscriptions). */
const SOURCES: { cle: string; titre: string; icone: string; sinon?: string }[] = [
  { cle: 'tentatives', titre: 'Tes résultats aux quiz', icone: 'quiz' },
  { cle: 'notionsRatees', titre: 'Les notions à revoir', icone: 'psychology' },
  { cle: 'inscriptions', titre: 'Ta progression dans les cours', icone: 'menu_book' },
  { cle: 'tempsPasse', titre: 'Ton temps passé dans les cours', icone: 'schedule', sinon: 'inscriptions' },
  { cle: 'activite', titre: 'Ton activité sur Bawsla', icone: 'calendar_month' },
];

/**
 * Espace apprenant : ma progression, branchée sur le Machine Learning du module User (GET /api/users/me/ia).
 * Cours recommandés (filtrage collaboratif), profil d'apprentissage (K-Means) et indicateurs. Le profil n'est
 * calculé qu'avec des données complètes ; la page dit ce qui manque encore. Le risque d'échec n'y apparaît jamais.
 * IA générative (LLM) : le conseil de la boussole et le plan de la semaine, rédigés à la demande à partir de ces
 * résultats (GET/POST /api/users/me/ia/conseil et /plan).
 */
@Component({
  selector: 'app-progres',
  imports: [RouterLink, MatIconModule, AiBox, BwButton, EmptyState, Kpi, PageHeader, Skeleton, IaGeneree],
  templateUrl: './progres.html',
  styleUrl: './progres.scss',
})
export class Progres implements OnInit {
  private readonly api = inject(IaUserService);
  private readonly iaGen = inject(IaGenerativeService);

  // IA générative (LLM) : conseil et plan, générés à la demande.
  protected readonly lireConseil = () => this.iaGen.dernierConseil();
  protected readonly genererConseil = () => this.iaGen.genererConseil();
  protected readonly lirePlan = () => this.iaGen.dernierPlan();
  protected readonly genererPlan = () => this.iaGen.genererPlan();

  protected readonly ia = signal<IaApprenant | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly sources = computed(() => {
    const s = this.ia()?.sources ?? {};
    return SOURCES.map((x) => ({ ...x, statut: s[x.cle]?.statut ?? (x.sinon ? s[x.sinon]?.statut : undefined) ?? ('INDISPONIBLE' as StatutSource) }));
  });

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.ia.set(null);
    this.api.monIa().subscribe({
      next: (r) => this.ia.set(r),
      error: (err) => this.erreur.set(errorMessage(err, 'Ta progression est indisponible pour le moment')),
    });
  }

  /** Valeur d'un indicateur, ou « — » si sa source n'est pas encore disponible. */
  protected valeur(x: number | undefined, format: 'pct' | 'nombre' = 'nombre'): string {
    if (x === undefined || x === null) return '—';
    return format === 'pct' ? `${Math.round(x * 100)} %` : String(x);
  }

  /** 95 → « 1 h 35 », 40 → « 40 min ». */
  protected heures(minutes: number): string {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h === 0 ? `${m} min` : m === 0 ? `${h} h` : `${h} h ${String(m).padStart(2, '0')}`;
  }

  protected pris(statut: StatutSource): boolean {
    return statut === 'DISPONIBLE';
  }
}
