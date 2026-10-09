import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { provideNativeDateAdapter } from '@angular/material/core';
import { RouterLink } from '@angular/router';

import { EXTRAIT_CHAPITRE, coursParId } from '../../../../core/demo/demo-data';
import { CATEGORIES, NIVEAUX, meta } from '../../../../core/demo/demo-plus';
import { AiBox, AiLoading, BwButton } from '../../../../shared';
import { Dropzone, PageHeader } from '../../../../shared/ui';

/**
 * Éditeur de cours en 5 étapes : informations, chapitres (réordonnables), contenu et résumé IA
 * (brouillon à relire), examen final (seuil, tentatives, certificat), publication et prix.
 * Sert à la création (/studio/cours/nouveau) et à la modification.
 * À brancher : CoursService.create/update, upload du PDF, /api/ai/cours/resume,
 * QuizService (réglages de l'examen final).
 */
@Component({
  selector: 'app-studio-editeur',
  imports: [
    RouterLink, FormsModule, DragDropModule, MatStepperModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatRadioModule,
    MatCheckboxModule, MatSlideToggleModule, MatDatepickerModule, MatIconModule, MatSnackBarModule, AiBox, AiLoading, BwButton, Dropzone, PageHeader,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './studio-editeur.html',
  styleUrl: './studio-editeur.scss',
})
export class StudioEditeur {
  protected readonly snack = inject(MatSnackBar);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly existant = computed(() => coursParId(this.id()));
  protected readonly categories = CATEGORIES;
  protected readonly niveaux = NIVEAUX;

  protected readonly titre = signal('');
  protected readonly description = signal('');
  protected readonly categorie = signal('Données');
  protected readonly niveau = signal('INTERMEDIAIRE');
  protected readonly chapitres = signal<{ titre: string; duree: number }[]>([
    { titre: 'Introduction', duree: 20 },
    { titre: 'Notions de base', duree: 30 },
  ]);
  protected readonly nouveauChapitre = signal('');
  protected readonly resumeEtat = signal<'vide' | 'generation' | 'brouillon' | 'valide'>('vide');
  protected readonly resume = signal(EXTRAIT_CHAPITRE.resume);
  protected readonly edition = signal(false);
  protected readonly visible = signal(true);
  protected readonly forum = signal(true);
  protected readonly datePublication = signal<Date | null>(new Date(2026, 9, 12));
  protected readonly payant = signal(false);
  protected readonly prix = signal(49);
  protected readonly examen = signal({ questions: 20, duree: 30, seuil: 70, tentatives: 3, melange: true });
  protected majExamen(champ: 'questions' | 'duree' | 'seuil' | 'tentatives', v: number): void {
    this.examen.update((e) => ({ ...e, [champ]: Number(v) || 0 }));
  }

  ngOnInit(): void {
    const c = this.existant();
    if (c) {
      this.titre.set(c.titre);
      this.description.set(c.description);
      this.categorie.set(c.categorie);
      this.niveau.set(c.niveau);
      this.chapitres.set(c.chapitres.map((ch) => ({ titre: ch.titre, duree: ch.duree })));
      this.resumeEtat.set('brouillon');
      this.payant.set(c.prix > 0);
      if (c.prix) this.prix.set(c.prix);
      this.examen.set({ ...c.examen, melange: true });
    }
  }

  protected readonly image = computed(() => (this.existant() ? [{ name: meta(this.id()).image.split('/').pop()!, size: 48000 }] : []));
  protected readonly dureeTotale = computed(() => this.chapitres().reduce((t, c) => t + c.duree, 0));
  protected readonly pret = computed(() => [
    { ok: this.titre().length > 5, texte: 'Un titre clair' },
    { ok: this.description().length > 20, texte: 'Une description d’au moins 20 caractères' },
    { ok: this.chapitres().length >= 2, texte: 'Au moins deux chapitres' },
    { ok: this.resumeEtat() === 'valide', texte: 'Le résumé IA relu et validé' },
    { ok: this.examen().questions >= 10, texte: 'Un examen final d’au moins 10 questions' },
    { ok: !this.payant() || this.prix() > 0, texte: 'Un prix si le cours est payant' },
  ]);

  protected deplacer(e: CdkDragDrop<unknown>): void {
    const l = [...this.chapitres()];
    moveItemInArray(l, e.previousIndex, e.currentIndex);
    this.chapitres.set(l);
  }
  protected ajouter(): void {
    const t = this.nouveauChapitre().trim();
    if (!t) return;
    this.chapitres.update((l) => [...l, { titre: t, duree: 25 }]);
    this.nouveauChapitre.set('');
  }
  protected retirer(i: number): void {
    this.chapitres.update((l) => l.filter((_, k) => k !== i));
  }
  protected generer(): void {
    this.resumeEtat.set('generation');
    setTimeout(() => this.resumeEtat.set('brouillon'), 1800);
  }
  protected valider(): void {
    this.resumeEtat.set('valide');
    this.edition.set(false);
  }
  protected publier(): void {
    this.snack.open(this.visible() ? 'Cours publié' : 'Brouillon enregistré', 'OK', { duration: 3000 });
  }
}
