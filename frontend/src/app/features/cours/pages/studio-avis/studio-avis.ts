import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';

import { coursParId } from '../../../../core/demo/demo-data';
import { AVIS, AvisCours, REPARTITION_NOTES, coursDuFormateur } from '../../../../core/demo/demo-plus';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader, Rating } from '../../../../shared/ui';

type Filtre = 'tous' | 'sansReponse' | 'negatifs';

/**
 * Studio : les avis des apprenants sur les cours du formateur, avec réponse publique.
 * Une réponse s'affiche sous l'avis sur la fiche du cours.
 * À brancher : CoursService.avis(coursId) et POST /api/cours/avis/{id}/reponse (à décrire dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-studio-avis',
  imports: [FormsModule, MatButtonToggleModule, MatIconModule, MatSelectModule, AiBox, BwButton, EmptyState, Avatar, Kpi, PageHeader, Rating],
  templateUrl: './studio-avis.html',
  styleUrl: './studio-avis.scss',
})
export class StudioAvis {
  protected readonly cours = coursDuFormateur().filter((c) => AVIS.some((a) => a.coursId === c.id));
  protected readonly coursChoisi = signal<number | null>(null);
  protected readonly filtre = signal<Filtre>('tous');
  protected readonly avis = signal<AvisCours[]>(AVIS.map((a) => ({ ...a })));
  protected readonly enEdition = signal<number | null>(null);
  protected readonly brouillon = signal('');
  protected readonly repartition = REPARTITION_NOTES;
  protected readonly Math = Math;

  protected readonly visibles = computed(() =>
    this.avis().filter((a) => {
      if (this.coursChoisi() && a.coursId !== this.coursChoisi()) return false;
      if (this.filtre() === 'sansReponse') return !a.reponse;
      if (this.filtre() === 'negatifs') return a.note <= 3;
      return true;
    }),
  );
  protected readonly sansReponse = computed(() => this.avis().filter((a) => !a.reponse).length);
  protected readonly moyenne = computed(() => {
    const l = this.avis();
    return (l.reduce((t, a) => t + a.note, 0) / l.length).toFixed(1).replace('.', ',');
  });

  protected titre(id: number): string {
    return coursParId(id)?.titre ?? '';
  }

  protected repondre(a: AvisCours): void {
    this.enEdition.set(a.id);
    this.brouillon.set(a.reponse ?? '');
  }

  /** Brouillon de réponse proposé par l'IA, à relire avant publication. */
  protected suggerer(a: AvisCours): void {
    this.brouillon.set(
      a.note <= 3
        ? `Merci pour ce retour honnête. Tu as raison, ce passage mérite plus d’exemples : je l’enrichis cette semaine et je te préviens dès que c’est en ligne.`
        : `Merci beaucoup pour ton avis ! Ravie que le cours t’aide. N’hésite pas à poser tes questions sur le forum du cours.`,
    );
  }

  protected publier(id: number): void {
    const texte = this.brouillon().trim();
    if (!texte) return;
    this.avis.update((l) => l.map((a) => (a.id === id ? { ...a, reponse: texte } : a)));
    this.enEdition.set(null);
  }
}
