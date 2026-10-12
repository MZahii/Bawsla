import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, BwButton, EmptyState } from '../../../../shared';
import { Skeleton } from '../../../../shared/ui';
import { formatTaille } from '../../models/cours.constantes';
import { FicheAudio } from '../../components/fiche-audio';
import { FicheContenu } from '../../components/fiche-contenu';
import { ExplicationLecteur } from '../../components/explication-lecteur';
import { Chapitre, Cours, Fiche } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';
import { analyserLienVideo } from '../../services/video-lien';

type Etat = 'chargement' | 'ok' | 'introuvable';

/**
 * Lecteur de cours : plan des chapitres à gauche ; à droite, pour le chapitre choisi, la vidéo (YouTube ou .mp4,
 * si elle existe), le texte du chapitre, le support PDF du cours et l'emplacement du résumé IA.
 * Les chapitres viennent de cours-service. « Marquer comme terminé » et le résumé IA sont désactivés : ils seront
 * branchés avec la progression (lot 2) et l'IA. Rien n'est simulé.
 */
@Component({
  selector: 'app-lecteur',
  imports: [RouterLink, MatIconModule, MatSnackBarModule, MatTooltipModule, AiBox, BwButton, EmptyState, Skeleton, FicheAudio, FicheContenu, ExplicationLecteur],
  templateUrl: './lecteur.html',
  styleUrl: './lecteur.scss',
})
export class Lecteur implements OnInit {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);
  private readonly sanitizer = inject(DomSanitizer);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours | null>(null);
  protected readonly chapitres = signal<Chapitre[]>([]);
  /** Dernière fiche de révision VALIDEE par le formateur ; null s'il n'y en a pas encore. */
  protected readonly fiche = signal<Fiche | null>(null);
  protected readonly index = signal(0);
  protected readonly planOuvert = signal(true);
  protected readonly formatTaille = formatTaille;

  protected readonly chapitre = computed<Chapitre | undefined>(() => this.chapitres()[this.index()]);
  /** Texte du chapitre, sans lignes vides en début et fin ; les retours à la ligne sont conservés à l'affichage. */
  protected readonly contenu = computed(() => (this.chapitre()?.contenu ?? '').trim());

  private readonly video = computed(() => analyserLienVideo(this.chapitre()?.videoUrl));
  protected readonly videoMp4 = computed(() => {
    const v = this.video();
    return v?.type === 'mp4' ? v.url : null;
  });
  /** Adresse d'intégration YouTube reconstruite à partir de l'identifiant seul (jamais l'URL saisie telle quelle). */
  protected readonly videoYoutube = computed<SafeResourceUrl | null>(() => {
    const v = this.video();
    return v?.type === 'youtube' ? this.sanitizer.bypassSecurityTrustResourceUrl(v.embed) : null;
  });
  protected readonly aVideo = computed(() => this.video() !== null);

  ngOnInit(): void {
    this.service.findById(this.id()).subscribe({
      next: (c) => {
        this.cours.set(c);
        this.chargerFiche(c.id);
        this.service.chapitres(c.id).subscribe({
          next: (l) => {
            this.chapitres.set(l);
            this.etat.set('ok');
          },
          error: (err) => {
            this.etat.set('ok');
            this.snack.open(errorMessage(err, 'Impossible de charger les chapitres'), 'OK', { duration: 5000 });
          },
        });
      },
      error: (err) => {
        this.etat.set('introuvable');
        if ((err as { status?: number })?.status !== 404) {
          this.snack.open(errorMessage(err, 'Impossible de charger le cours'), 'OK', { duration: 5000 });
        }
      },
    });
  }

  /** L'apprenant ne reçoit que la fiche validée ; `null` = pas encore de résumé (ce n'est pas une erreur). */
  private chargerFiche(coursId: number): void {
    this.service.fiche(coursId).subscribe({
      next: (f) => this.fiche.set(f),
      error: () => this.fiche.set(null),
    });
  }

  protected ouvrir(i: number): void {
    if (i >= 0 && i < this.chapitres().length) this.index.set(i);
  }

  /** Le PDF exige le JWT : on le télécharge avec HttpClient, puis on l'ouvre dans un nouvel onglet. */
  protected ouvrirPdf(): void {
    this.chargerPdf((blob) => {
      const url = URL.createObjectURL(blob);
      window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    });
  }

  protected telechargerPdf(): void {
    const nom = this.cours()?.fichierPdf ?? 'cours.pdf';
    this.chargerPdf((blob) => {
      const url = URL.createObjectURL(blob);
      const lien = document.createElement('a');
      lien.href = url;
      lien.download = nom;
      lien.click();
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    });
  }

  private chargerPdf(utiliser: (blob: Blob) => void): void {
    const c = this.cours();
    if (!c) return;
    this.service.pdf(c.id).subscribe({
      next: utiliser,
      error: (err) => this.snack.open(errorMessage(err, 'Impossible de charger le PDF'), 'OK', { duration: 5000 }),
    });
  }
}
