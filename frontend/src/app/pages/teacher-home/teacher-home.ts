import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { coursParId } from '../../core/demo/demo-data';
import {
  APPRENANTS,
  AVIS,
  BROUILLONS_IA,
  EVENEMENTS_CALENDRIER,
  INSCRIPTIONS_SEMAINES,
  LIBELLE_RISQUE,
  STATS_COURS_FORMATEUR,
  meta,
} from '../../core/demo/demo-plus';
import { BwButton } from '../../shared';
import { Avatar, BwChart, Kpi, Rating, Timeline, TimelineItem } from '../../shared/ui';

const CLE_ATTENTE = 'bawsla.formateur.enAttente';

/**
 * Tableau de bord du studio formateur : inscriptions, achèvement, note moyenne, certificats délivrés,
 * statistiques par cours, nouveaux avis, apprenants qui risquent d'abandonner, contenus IA à valider.
 * Un formateur qui vient de s'inscrire voit que son profil attend la validation de l'admin.
 * À brancher : CoursService (statistiques par cours, avis), UserService (apprenants), QuizService (certificats).
 */
@Component({
  selector: 'app-teacher-home',
  imports: [RouterLink, MatIconModule, BwButton, Avatar, BwChart, Kpi, Rating, Timeline],
  templateUrl: './teacher-home.html',
  styleUrl: './teacher-home.scss',
})
export class TeacherHome {
  private readonly auth = inject(AuthService);
  protected readonly nom = computed(() => {
    const u = this.auth.currentUser();
    return u?.nom ? `${u.prenom} ${u.nom}`.trim() : 'Imen Ayari';
  });
  protected readonly enAttente = signal(lire(CLE_ATTENTE) === '1');

  protected readonly stats = STATS_COURS_FORMATEUR.map((s) => ({ ...s, titre: coursParId(s.coursId)?.titre ?? '', image: meta(s.coursId).image, statut: meta(s.coursId).statut }));
  protected readonly inscriptions = {
    labels: INSCRIPTIONS_SEMAINES.labels,
    series: [{ label: 'Nouvelles inscriptions', data: INSCRIPTIONS_SEMAINES.valeurs }],
  };
  protected readonly brouillons = BROUILLONS_IA;
  protected readonly nouveauxAvis = AVIS.filter((a) => !a.reponse).slice(0, 3).map((a) => ({ ...a, cours: coursParId(a.coursId)?.titre ?? '' }));
  protected readonly aSuivre = APPRENANTS.filter((e) => e.risque === 'eleve').sort((a, b) => a.moyenne - b.moyenne).slice(0, 4);
  protected readonly libelle = LIBELLE_RISQUE;
  protected readonly evenements = EVENEMENTS_CALENDRIER.filter((e) => e.jour >= 9).slice(0, 4);
  protected readonly activite: TimelineItem[] = [
    { quand: 'il y a 12 min', titre: '3 nouvelles inscriptions', detail: 'Angular, de zéro aux signaux', icone: 'person_add', ton: 'neutre' },
    { quand: 'il y a 40 min', titre: 'Lina G. a obtenu son certificat', detail: 'Bases de données relationnelles, 88 %', icone: 'workspace_premium', ton: 'accent' },
    { quand: 'il y a 2 h', titre: 'L’IA a généré 7 questions', detail: '2 écartées par la vérification', icone: 'auto_awesome', ton: 'ia' },
    { quand: 'hier', titre: 'Nouvel avis à 2 étoiles', detail: 'Le chapitre Normalisation va trop vite', icone: 'reviews', ton: 'alerte' },
  ];

  protected masquerAttente(): void {
    this.enAttente.set(false);
    try {
      localStorage.removeItem(CLE_ATTENTE);
    } catch {
      /* ignoré */
    }
  }
}

function lire(cle: string): string | null {
  try {
    return localStorage.getItem(cle);
  } catch {
    return null;
  }
}
