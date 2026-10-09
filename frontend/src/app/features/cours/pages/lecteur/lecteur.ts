import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { EXTRAIT_CHAPITRE, QUIZ, coursParId } from '../../../../core/demo/demo-data';
import { meta } from '../../../../core/demo/demo-plus';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { VideoPlayer } from '../../../../shared/ui';

/**
 * Lecteur de cours : liste des chapitres à gauche, vidéo ou lecture au centre, onglets
 * Résumé IA / Contenu / Notes / Ressources. « Marquer comme terminé » fait avancer la progression.
 * À brancher : contenu du chapitre (GET /api/cours/{id}/texte), résumé (/api/ai/cours/resume), progression.
 */
@Component({
  selector: 'app-lecteur',
  imports: [RouterLink, FormsModule, MatIconModule, MatTabsModule, MatTooltipModule, MatSnackBarModule, AiBox, BwButton, EmptyState, VideoPlayer],
  templateUrl: './lecteur.html',
  styleUrl: './lecteur.scss',
})
export class Lecteur {
  private readonly snack = inject(MatSnackBar);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly cours = computed(() => coursParId(this.id()));
  protected readonly image = computed(() => meta(this.id()).image);
  protected readonly faits = signal<boolean[] | null>(null);
  protected readonly etat = computed(() => this.faits() ?? this.cours()?.chapitres.map((c) => c.fait) ?? []);
  protected readonly courant = signal<number | null>(null);
  protected readonly index = computed(() => this.courant() ?? Math.max(0, this.etat().findIndex((f) => !f)));
  protected readonly chapitre = computed(() => this.cours()?.chapitres[this.index()]);
  protected readonly pourcent = computed(() => Math.round((this.etat().filter(Boolean).length / Math.max(1, this.etat().length)) * 100));
  protected readonly quiz = computed(() => QUIZ.find((q) => q.coursId === this.id()));
  protected readonly extrait = EXTRAIT_CHAPITRE;
  protected readonly planOuvert = signal(true);
  protected readonly notes = signal('Différence ON / WHERE avec LEFT JOIN : le filtre sur la table de droite va dans le ON.');

  protected readonly ressources = [
    { icone: 'picture_as_pdf', nom: 'Support du chapitre.pdf', taille: '1,2 Mo' },
    { icone: 'code', nom: 'script-jointures.sql', taille: '4 Ko' },
    { icone: 'link', nom: 'Documentation MySQL : JOIN', taille: 'lien externe' },
  ];

  protected ouvrir(i: number): void {
    this.courant.set(i);
  }
  protected terminer(): void {
    const e = [...this.etat()];
    e[this.index()] = true;
    this.faits.set(e);
    this.snack.open(`Chapitre « ${this.chapitre()?.titre} » terminé`, 'OK', { duration: 3000 });
    const suivant = e.findIndex((f) => !f);
    if (suivant >= 0) this.courant.set(suivant);
    else {
      // Aperçu : tous les chapitres sont terminés, l'examen final se débloque (à brancher : progression côté cours-service).
      const c = this.cours();
      if (c) {
        c.chapitres.forEach((ch) => (ch.fait = true));
        c.progression = 100;
      }
      this.snack.open('Cours terminé : ton examen final est débloqué', 'OK', { duration: 4000 });
    }
  }
}
