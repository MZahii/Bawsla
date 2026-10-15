import { Component, OnInit, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { errorMessage } from '../../../../shared/models/api-response.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Skeleton } from '../../../../shared/ui';
import { formatTaille, pourcent } from '../../models/cours.constantes';
import { StudioIaLecteur } from '../../components/studio-ia/studio-ia-lecteur';
import { Chapitre, Cours, Progression } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';
import { analyserLienVideo } from '../../services/video-lien';

type Etat = 'chargement' | 'ok' | 'introuvable';

/**
 * Lecteur de cours : plan des chapitres à gauche ; à droite, pour le chapitre choisi, la vidéo (YouTube ou .mp4,
 * si elle existe), le texte du chapitre, le support PDF du cours et l'emplacement du résumé IA.
 * Les chapitres viennent de cours-service. Un apprenant inscrit marque chaque chapitre comme terminé (ou annule) et voit sa progression ;
 * non inscrit, il est invité à s'inscrire (rien n'est enregistré ni simulé). Un formateur ou un ADMIN qui visite le cours n'a pas de progression.
 */
@Component({
  selector: 'app-lecteur',
  imports: [RouterLink, MatIconModule, MatSnackBarModule, BwButton, EmptyState, Skeleton, StudioIaLecteur],
  templateUrl: './lecteur.html',
  styleUrl: './lecteur.scss',
})
export class Lecteur implements OnInit {
  private readonly service = inject(CoursService);
  private readonly snack = inject(MatSnackBar);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly auth = inject(AuthService);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly etat = signal<Etat>('chargement');
  protected readonly cours = signal<Cours | null>(null);
  protected readonly chapitres = signal<Chapitre[]>([]);
  protected readonly index = signal(0);
  protected readonly planOuvert = signal(true);
  protected readonly formatTaille = formatTaille;
  protected readonly pourcent = pourcent;

  /** Ma progression (apprenant inscrit) ; null si je ne suis pas inscrit ou pas apprenant. */
  protected readonly progression = signal<Progression | null>(null);
  protected readonly estApprenant = computed(() => this.auth.role() === 'ETUDIANT');
  /** Vrai une fois la progression chargée : évite d'afficher « Inscris-toi » avant de savoir si l'apprenant l'est déjà. */
  protected readonly progressionConnue = signal(false);
  protected readonly action = signal(false);
  protected readonly termines = computed(() => new Set(this.progression()?.chapitresTermines ?? []));
  protected readonly chapitreTermine = computed(() => {
    const ch = this.chapitre();
    return !!ch && this.termines().has(ch.id);
  });
  protected readonly nbTermines = computed(() => this.progression()?.chapitresTermines.length ?? 0);

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
        if (this.estApprenant()) this.chargerProgression(c.id);
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


  private chargerProgression(coursId: number): void {
    this.service.maProgression(coursId).subscribe({
      next: (p) => {
        this.progression.set(p);
        this.progressionConnue.set(true);
      },
      error: () => this.progressionConnue.set(false), // inconnue : on ne propose ni « terminer » ni « s'inscrire » à tort
    });
  }

  protected sInscrire(): void {
    const c = this.cours();
    if (!c || this.action()) return;
    this.action.set(true);
    this.service.sInscrire(c.id).subscribe({
      next: () => {
        this.action.set(false);
        this.snack.open('Tu es inscrit à ce cours.', 'OK', { duration: 4000 });
        this.chargerProgression(c.id);
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Inscription impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  /** « Marquer ce chapitre comme terminé » ou « Annuler » (le serveur est idempotent : un double clic ne compte qu'une fois). */
  protected basculerTermine(): void {
    const c = this.cours();
    const ch = this.chapitre();
    if (!c || !ch || this.action()) return;
    this.action.set(true);
    const appel = this.chapitreTermine() ? this.service.annulerChapitre(c.id, ch.id) : this.service.terminerChapitre(c.id, ch.id);
    appel.subscribe({
      next: (p) => {
        this.action.set(false);
        this.progression.set(p);
        if (p.termine) this.snack.open('Bravo, tu as terminé tous les chapitres du cours.', 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.action.set(false);
        this.snack.open(errorMessage(err, 'Impossible d’enregistrer ta progression'), 'OK', { duration: 6000 });
      },
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
