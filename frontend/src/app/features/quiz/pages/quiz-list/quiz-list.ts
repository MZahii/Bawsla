import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { COURS, QUIZ } from '../../../../core/demo/demo-data';
import { CERTIFICATS, ETAT_EXAMEN, HISTORIQUE_QUIZ, meta } from '../../../../core/demo/demo-plus';
import { BwButton } from '../../../../shared';
import { BwChart, Kpi, PageHeader } from '../../../../shared/ui';

type EtatExamen = 'verrouille' | 'disponible' | 'reussi';

/**
 * Espace apprenant : « Évaluations ». Pour chaque cours suivi, les quiz de chapitre puis l'examen final,
 * qui se débloque quand tous les chapitres sont terminés et donne le certificat.
 * La comparaison se fait avec les autres apprenants du même cours (pas de classe).
 * À brancher : QuizService.findForApprenant(), l'historique des tentatives et l'état des examens finaux.
 */
@Component({
  selector: 'app-quiz-list',
  imports: [RouterLink, MatIconModule, BwButton, PageHeader, Kpi, BwChart],
  templateUrl: './quiz-list.html',
  styleUrl: './quiz-list.scss',
})
export class QuizList {
  protected readonly parcours = COURS.filter((c) => c.inscrit).map((c) => {
    const restants = c.chapitres.filter((ch) => !ch.fait).length;
    const examen = ETAT_EXAMEN[c.id] ?? { tentativesFaites: 0, meilleurScore: null };
    const certificat = CERTIFICATS.find((x) => x.coursId === c.id);
    const etat: EtatExamen = certificat ? 'reussi' : restants === 0 ? 'disponible' : 'verrouille';
    return {
      cours: c,
      image: meta(c.id).image,
      faits: c.chapitres.length - restants,
      restants,
      quiz: QUIZ.filter((q) => q.coursId === c.id),
      examen: { ...c.examen, ...examen, etat, restantes: c.examen.tentatives - examen.tentativesFaites },
      certificat,
    };
  });

  protected readonly historique = HISTORIQUE_QUIZ;
  protected readonly moyenne = Math.round(HISTORIQUE_QUIZ.reduce((t, h) => t + h.score, 0) / HISTORIQUE_QUIZ.length);
  protected readonly examensDispo = this.parcours.filter((p) => p.examen.etat === 'disponible').length;
  protected readonly nbCertificats = CERTIFICATS.length;
  protected readonly comparaison = {
    labels: HISTORIQUE_QUIZ.map((h) => h.titre),
    series: [
      { label: 'Toi', data: HISTORIQUE_QUIZ.map((h) => h.score) },
      { label: 'Autres apprenants du cours', data: HISTORIQUE_QUIZ.map((h) => h.autres) },
    ],
  };

  protected ton(score: number | null): string {
    if (score === null) return '';
    return score >= 70 ? 'ok' : score >= 50 ? 'warn' : 'ko';
  }
}
