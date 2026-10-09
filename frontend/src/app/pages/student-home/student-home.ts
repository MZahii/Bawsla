import { Component, computed, inject } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { lireOnboarding } from '../../core/profil/onboarding';
import { COURS, DISCUSSIONS, RECOMMANDATIONS, coursParId } from '../../core/demo/demo-data';
import { ACTIVITE_JOURS, CERTIFICATS, ECHEANCES, OBJECTIF_SEMAINE, meta, tuile } from '../../core/demo/demo-plus';
import { BwButton } from '../../shared';
import { CourseTile, Heatmap, ProgressRing, SectionHead } from '../../shared/ui';

/**
 * Accueil de l'espace apprenant : reprendre où on s'est arrêté, examen final disponible,
 * recommandations de l'IA (d'après le questionnaire d'accueil puis les quiz), rappels,
 * objectif de la semaine, activité et forum.
 * À brancher : CoursService (inscriptions), /api/ai/user/recommandations, QuizService (examens).
 */
@Component({
  selector: 'app-student-home',
  imports: [RouterLink, MatIconModule, BwButton, CourseTile, Heatmap, ProgressRing, SectionHead],
  templateUrl: './student-home.html',
  styleUrl: './student-home.scss',
})
export class StudentHome {
  private readonly auth = inject(AuthService);
  protected readonly prenom = computed(() => this.auth.currentUser()?.prenom || 'toi');

  protected readonly enCours = COURS.filter((c) => c.inscrit && c.progression > 0 && c.progression < 100);
  /** Cours terminés dont l'examen final n'est pas encore réussi. */
  protected readonly examenPret = COURS.find((c) => c.inscrit && c.progression >= 100 && !CERTIFICATS.some((x) => x.coursId === c.id));
  protected readonly onboarding = lireOnboarding();
  protected readonly sousTitreReco = this.onboarding?.objectif
    ? `Choisi par l’IA d’après ton objectif (« ${this.onboarding.objectif} ») et tes derniers quiz`
    : 'Choisi par l’IA à partir de tes derniers quiz';
  protected readonly reprendre = this.enCours[0];
  protected readonly image = meta(this.reprendre.id).image;
  protected readonly prochain = this.reprendre.chapitres.find((c) => !c.fait);
  protected readonly faits = this.reprendre.chapitres.filter((c) => c.fait).length;

  protected readonly recommandations = RECOMMANDATIONS.map((r) => {
    const c = coursParId(r.coursId)!;
    const lien = c.inscrit && c.progression >= 100 ? ['/app/evaluations/examen', c.id] : ['/app/explorer', c.id];
    return { ...r, lien, tuile: tuile(c, c.inscrit) };
  });
  protected readonly tuilesEnCours = this.enCours.slice(1, 4).map((c) => tuile(c, true));
  protected readonly echeances = ECHEANCES;
  protected readonly objectif = OBJECTIF_SEMAINE;
  protected readonly objectifPct = Math.round((OBJECTIF_SEMAINE.fait / OBJECTIF_SEMAINE.cible) * 100);
  protected readonly jours = ACTIVITE_JOURS;
  protected readonly discussions = DISCUSSIONS.slice(0, 3);
  protected readonly icones: Record<string, string> = { quiz: 'quiz', rappel: 'flag', live: 'videocam', examen: 'assignment' };
  protected readonly date = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
}
