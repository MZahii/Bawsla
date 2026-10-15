import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { IaApprenant, StatutSource } from '../../models/ia.model';
import { IaUserService } from '../../services/ia-user.service';

/** Sources de données présentées à l'apprenant, en mots simples (clés de IndicateursResponse.sources). */
const SOURCES: { cle: string; titre: string; icone: string }[] = [
  { cle: 'tentatives', titre: 'Tes résultats aux quiz', icone: 'quiz' },
  { cle: 'notionsRatees', titre: 'Les notions à revoir', icone: 'psychology' },
  { cle: 'inscriptions', titre: 'Ta progression dans les cours', icone: 'menu_book' },
  { cle: 'activite', titre: 'Ton activité sur Bawsla', icone: 'calendar_month' },
];

/**
 * Espace apprenant : ma progression, branchée sur le Machine Learning du module User (GET /api/users/me/ia).
 * Cours recommandés (filtrage collaboratif), profil d'apprentissage (K-Means) et indicateurs. Le profil n'est
 * calculé qu'avec des données complètes ; la page dit ce qui manque encore. Le risque d'échec n'y apparaît jamais.
 */
@Component({
  selector: 'app-progres',
  imports: [RouterLink, MatIconModule, AiBox, BwButton, EmptyState, Kpi, PageHeader, Skeleton],
  templateUrl: './progres.html',
  styleUrl: './progres.scss',
})
export class Progres implements OnInit {
  private readonly api = inject(IaUserService);

  protected readonly ia = signal<IaApprenant | null>(null);
  protected readonly erreur = signal<string | null>(null);

  protected readonly sources = computed(() => {
    const s = this.ia()?.sources ?? {};
    return SOURCES.map((x) => ({ ...x, statut: s[x.cle]?.statut ?? ('INDISPONIBLE' as StatutSource) }));
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

  protected pris(statut: StatutSource): boolean {
    return statut === 'DISPONIBLE';
  }
}
