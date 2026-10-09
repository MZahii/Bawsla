import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { CAP_IA, COURS, NOTIONS, PROFIL_APPRENTISSAGE, coursParId, notionParId } from '../../../../core/demo/demo-data';
import { ACTIVITE_JOURS, ACTIVITE_SEMAINES, BADGES, CERTIFICATS, OBJECTIF_SEMAINE, meta } from '../../../../core/demo/demo-plus';
import { AiBox, BwButton, CompassRose, LIBELLE_MAITRISE, niveauMaitrise } from '../../../../shared';
import { BwChart, Heatmap, Kpi, PageHeader, ProgressRing } from '../../../../shared/ui';

/**
 * Espace étudiant : ma progression. Rose des notions (maîtrise par notion et cap conseillé par l'IA),
 * activité hebdomadaire, carte d'activité, badges et attestations.
 * À brancher : UserService.progression() et /api/ai/user/recommandation.
 */
@Component({
  selector: 'app-progres',
  imports: [RouterLink, MatIconModule, AiBox, BwButton, CompassRose, BwChart, Heatmap, Kpi, PageHeader, ProgressRing],
  templateUrl: './progres.html',
  styleUrl: './progres.scss',
})
export class Progres {
  protected readonly notions = [...NOTIONS].sort((a, b) => a.maitrise - b.maitrise);
  protected readonly cap = CAP_IA;
  protected readonly capNotion = notionParId(CAP_IA.notionId);
  protected readonly profil = PROFIL_APPRENTISSAGE;
  protected readonly objectif = OBJECTIF_SEMAINE;
  protected readonly objectifPct = Math.round((OBJECTIF_SEMAINE.fait / OBJECTIF_SEMAINE.cible) * 100);
  protected readonly jours = ACTIVITE_JOURS;
  protected readonly badges = BADGES;
  protected readonly certificats = CERTIFICATS.map((c) => ({ ...c, cours: coursParId(c.coursId)!, image: meta(c.coursId).image }));
  protected readonly coursSuivis = COURS.filter((c) => c.progression > 0);
  protected readonly niveau = niveauMaitrise;
  protected readonly libelle = LIBELLE_MAITRISE;

  protected readonly activite = {
    labels: ACTIVITE_SEMAINES.labels,
    series: [{ label: 'Minutes d’étude', data: ACTIVITE_SEMAINES.minutes }],
  };
  protected readonly radar = {
    labels: NOTIONS.map((n) => n.nom),
    series: [
      { label: 'Toi', data: NOTIONS.map((n) => n.maitrise) },
      { label: 'Autres apprenants', data: NOTIONS.map((n, i) => Math.round(55 + ((i * 17) % 30))) },
    ],
  };
}
