import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { AsyncPipe } from '@angular/common';
import { Component, DestroyRef, OnInit, TemplateRef, computed, inject, input, numberAttribute, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { Router, RouterLink } from '@angular/router';
import { Subscription, catchError, of, switchMap, take, takeWhile, timer } from 'rxjs';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, AiLoading, Badge, BwButton, EmptyState } from '../../../../shared';
import { Dropzone, PageHeader, Skeleton } from '../../../../shared/ui';
import { CATEGORIES, NIVEAUX, formatDuree, formatTaille } from '../../models/cours.constantes';
import { Chapitre, ChapitreRequest, Cours, CoursRequest, EtapeIA, Fiche, FicheRequest, Niveau, StatutTraitement, Traitement } from '../../models/cours.model';
import { FicheAudio } from '../../components/fiche-audio';
import { FicheContenu } from '../../components/fiche-contenu';
import { CoursImage } from '../../pipes/cours-image';
import { CoursService } from '../../services/cours.service';

type Details = Record<string, string>;

const IMAGE_MAX = 2 * 1024 * 1024;
const PDF_MAX = 20 * 1024 * 1024;

interface FormChapitre {
  id: number | null;
  titre: string;
  dureeMinutes: number;
  contenu: string;
  videoUrl: string;
}

const CHAPITRE_VIDE: FormChapitre = { id: null, titre: '', dureeMinutes: 20, contenu: '', videoUrl: '' };

/** Fiche en cours de correction (les mots-clés sont saisis séparés par des virgules). */
interface BrouillonFiche {
  resume: string;
  motsCles: string;
  notions: { titre: string; definition: string; extraitSource: string }[];
}

/**
 * Éditeur de cours en 5 étapes : informations (image comprise), chapitres, contenu (PDF) et résumé IA,
 * examen final, publication et prix. Sert à la création (/studio/cours/nouveau) et à la modification
 * (/studio/cours/:id). Branché sur cours-service : informations, image, chapitres (ajout, modification, suppression,
 * ordre), PDF, prix et statut. « Résumé IA » et « Examen final » restent à brancher (badge « bientôt »).
 * Les chapitres, le PDF et la publication ne sont disponibles qu'une fois le cours créé.
 */
@Component({
  selector: 'app-studio-editeur',
  imports: [
    RouterLink, FormsModule, AsyncPipe, DragDropModule, MatStepperModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatRadioModule,
    MatCheckboxModule, MatSlideToggleModule, MatDialogModule, MatIconModule, MatSnackBarModule, AiBox, AiLoading, Badge, BwButton, FicheAudio, FicheContenu,
    Dropzone, EmptyState, PageHeader, Skeleton, CoursImage,
  ],
  templateUrl: './studio-editeur.html',
  styleUrl: './studio-editeur.scss',
})
export class StudioEditeur implements OnInit {
  private readonly service = inject(CoursService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  protected readonly snack = inject(MatSnackBar);
  private readonly dialogChapitre = viewChild.required<TemplateRef<unknown>>('dialogChapitre');
  private readonly dialogSuppression = viewChild.required<TemplateRef<unknown>>('dialogSuppression');
  private ref?: MatDialogRef<unknown>;

  readonly id = input(0, { transform: numberAttribute });
  /** Étape à ouvrir (0 = Informations), par exemple après la création du cours. */
  readonly etape = input(0, { transform: numberAttribute });

  protected readonly existant = signal<Cours | null>(null);
  protected readonly chargement = signal(false);
  protected readonly introuvable = signal(false);
  protected readonly envoi = signal(false);
  /** Messages de validation du backend (details), par champ : titre, description, categorie, prix… */
  protected readonly erreurs = signal<Details>({});
  protected readonly niveaux = NIVEAUX;
  protected readonly formatTaille = formatTaille;

  // ---- Étape Informations
  protected readonly titre = signal('');
  protected readonly description = signal('');
  protected readonly categorie = signal<string>(CATEGORIES[0].nom);
  protected readonly niveau = signal<Niveau>('INTERMEDIAIRE');
  /** Catégories connues, plus celle du cours si elle n'est pas dans la liste (cours créé ailleurs). */
  protected readonly categories = computed(() => {
    const actuelle = this.categorie();
    return !actuelle || CATEGORIES.some((c) => c.nom === actuelle) ? [...CATEGORIES] : [...CATEGORIES, { nom: actuelle, icone: 'folder' }];
  });

  // ---- Image
  protected readonly imageEnAttente = signal<File | null>(null);
  protected readonly apercuLocal = signal<string | null>(null);
  protected readonly imageEnvoi = signal(false);
  protected readonly erreurImage = signal<string | null>(null);

  // ---- Chapitres
  protected readonly chapitres = signal<Chapitre[]>([]);
  protected readonly chapitresEtat = signal<'chargement' | 'ok' | 'erreur'>('ok');
  protected readonly formChapitre = signal<FormChapitre>({ ...CHAPITRE_VIDE });
  protected readonly erreursChapitre = signal<Details>({});
  protected readonly chapitreEnvoi = signal(false);
  protected readonly chapitreASupprimer = signal<Chapitre | null>(null);
  protected readonly dureeTotale = computed(() => this.chapitres().reduce((t, c) => t + c.dureeMinutes, 0));
  protected readonly dureeLibelle = computed(() => formatDuree(this.dureeTotale()) || '0 min');

  // ---- Contenu (PDF)
  protected readonly pdfEnvoi = signal(false);
  protected readonly erreurPdf = signal<string | null>(null);
  /** Résultat de l'extraction du texte : null tant qu'on ne sait pas, true si le PDF ne contient aucun texte. */
  protected readonly pdfScanne = signal<boolean | null>(null);
  protected readonly pdfPages = signal(0);

  // ---- Résumé IA : fiche de révision générée par l'IA, relue par le formateur, puis validée
  protected readonly fiche = signal<Fiche | null>(null);
  protected readonly ficheEtat = signal<'chargement' | 'ok' | 'erreur'>('chargement');
  /** Dernier lancement de l'IA sur ce cours (étapes, durées, erreurs). */
  protected readonly traitement = signal<Traitement | null>(null);
  /** true tant qu'on attend la fin d'une génération (le traitement tourne côté serveur). */
  protected readonly attente = signal(false);
  protected readonly lancement = signal(false);
  protected readonly ficheEnvoi = signal(false);
  protected readonly brouillon = signal<BrouillonFiche | null>(null);
  protected readonly etapesIA: { etape: EtapeIA; libelle: string }[] = [
    { etape: 'TEXTE', libelle: 'Lecture du cours' },
    { etape: 'FICHE', libelle: 'Rédaction de la fiche' },
    { etape: 'AUDIO', libelle: 'Création de l’audio' },
  ];
  /** Message d'une étape en échec dans le dernier lancement (affiché tant qu'aucune nouvelle génération n'a commencé). */
  protected readonly echecIA = computed(() => this.traitement()?.etapes.find((e) => e.statut === 'ECHEC') ?? null);
  private suivi?: Subscription;
  private readonly destroyRef = inject(DestroyRef);
  protected readonly examen = signal({ questions: 20, duree: 30, seuil: 70, tentatives: 3, melange: true });

  // ---- Publication
  protected readonly payant = signal(false);
  protected readonly prix = signal(49);
  protected readonly visible = signal(true);
  protected readonly pret = computed(() => [
    { ok: this.titre().trim().length > 5, texte: 'Un titre clair' },
    { ok: this.description().trim().length > 20, texte: 'Une description d’au moins 20 caractères' },
    { ok: this.chapitres().length >= 1, texte: 'Au moins un chapitre (obligatoire pour publier)' },
    { ok: !!this.existant()?.aPdf, texte: 'Un support PDF' },
    { ok: !!this.existant()?.imageUrl, texte: 'Une image de couverture' },
    { ok: !this.payant() || this.prix() > 0, texte: 'Un prix si le cours est payant' },
  ]);
  protected readonly publicationBloquee = computed(() => this.visible() && this.chapitres().length === 0);

  ngOnInit(): void {
    if (this.id() > 0) this.charger();
    this.destroyRef.onDestroy(() => this.arreterSuivi());
  }

  private charger(): void {
    this.chargement.set(true);
    this.service.findById(this.id()).subscribe({
      next: (c) => {
        this.appliquer(c);
        this.chargement.set(false);
        this.chargerChapitres();
        if (c.aPdf) this.analyserPdf();
        this.chargerFiche();
        this.reprendreSuivi();
      },
      error: (err) => {
        this.chargement.set(false);
        this.introuvable.set(true);
        this.snack.open(errorMessage(err, 'Cours introuvable'), 'OK', { duration: 5000 });
      },
    });
  }

  /** Remplit les champs à partir du cours renvoyé par le backend. */
  private appliquer(c: Cours): void {
    this.existant.set(c);
    this.titre.set(c.titre);
    this.description.set(c.description ?? '');
    if (c.categorie) this.categorie.set(c.categorie);
    if (c.niveau) this.niveau.set(c.niveau);
    this.payant.set(c.prix > 0);
    if (c.prix > 0) this.prix.set(c.prix);
    this.visible.set(c.statut === 'PUBLIE');
  }

  private corps(): CoursRequest | null {
    if (this.payant() && !(this.prix() > 0)) {
      this.erreurs.set({ prix: 'Indique un prix supérieur à 0' });
      return null;
    }
    return {
      titre: this.titre().trim(),
      description: this.description().trim() || undefined,
      categorie: this.categorie() || undefined,
      niveau: this.niveau(),
      prix: this.payant() ? this.prix() : 0,
    };
  }

  private erreurApi(err: unknown, fallback: string): void {
    const details = (err as { error?: { details?: Details } })?.error?.details;
    if (details) {
      this.erreurs.set(details);
      this.snack.open('Corrige les champs signalés', 'OK', { duration: 4000 });
    } else {
      this.snack.open(errorMessage(err, fallback), 'OK', { duration: 5000 });
    }
  }

  // ------------------------------------------------------------------ informations

  /** Étape Informations : crée le cours (puis ouvre l'étape Chapitres) ou enregistre les changements. */
  protected enregistrer(): void {
    if (this.envoi()) return;
    this.erreurs.set({});
    const body = this.corps();
    if (!body) return;
    this.envoi.set(true);
    const existant = this.existant();
    const appel = existant ? this.service.update(existant.id, body) : this.service.create(body);
    appel.subscribe({
      next: (c) => {
        if (existant) {
          this.envoi.set(false);
          this.appliquer(c);
          this.snack.open('Cours enregistré', 'OK', { duration: 3000 });
          return;
        }
        this.envoyerImageEnAttente(c);
      },
      error: (err) => {
        this.envoi.set(false);
        this.erreurApi(err, 'Enregistrement impossible');
      },
    });
  }

  /** À la création : envoie l'image choisie avant que le cours existe, puis ouvre l'étape Chapitres. */
  private envoyerImageEnAttente(cours: Cours): void {
    const fichier = this.imageEnAttente();
    const terminer = () => {
      this.envoi.set(false);
      this.snack.open('Cours créé : ajoute maintenant ses chapitres', 'OK', { duration: 4000 });
      this.router.navigate(['/studio/cours', cours.id], { queryParams: { etape: 1 } });
    };
    if (!fichier) return terminer();
    this.service.envoyerImage(cours.id, fichier).subscribe({
      next: terminer,
      error: (err) => {
        this.snack.open(errorMessage(err, 'Image non enregistrée : renvoie-la depuis le cours'), 'OK', { duration: 6000 });
        terminer();
      },
    });
  }

  // ------------------------------------------------------------------ image

  protected choisirImage(fichiers: File[]): void {
    const f = fichiers[0];
    this.erreurImage.set(null);
    if (!f) return;
    if (f.type !== 'image/jpeg' && f.type !== 'image/png') {
      this.erreurImage.set('Format non pris en charge : JPG ou PNG uniquement.');
      return;
    }
    if (f.size > IMAGE_MAX) {
      this.erreurImage.set('L’image dépasse 2 Mo.');
      return;
    }
    const cours = this.existant();
    if (!cours) {
      this.imageEnAttente.set(f);
      this.apercuLocal.set(URL.createObjectURL(f));
      return;
    }
    this.imageEnvoi.set(true);
    this.service.envoyerImage(cours.id, f).subscribe({
      next: (c) => {
        this.existant.set(c);
        this.imageEnvoi.set(false);
        this.snack.open('Image enregistrée', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.imageEnvoi.set(false);
        this.erreurImage.set(errorMessage(err, 'Envoi de l’image impossible'));
      },
    });
  }

  // ------------------------------------------------------------------ chapitres

  private chargerChapitres(): void {
    const cours = this.existant();
    if (!cours) return;
    this.chapitresEtat.set('chargement');
    this.service.chapitres(cours.id).subscribe({
      next: (l) => {
        this.chapitres.set(l);
        this.chapitresEtat.set('ok');
      },
      error: (err) => {
        this.chapitresEtat.set('erreur');
        this.snack.open(errorMessage(err, 'Impossible de charger les chapitres'), 'OK', { duration: 5000 });
      },
    });
  }

  protected rechargerChapitres(): void {
    this.chargerChapitres();
  }

  protected nouveauChapitre(): void {
    this.formChapitre.set({ ...CHAPITRE_VIDE });
    this.ouvrirFormulaire();
  }

  protected modifierChapitre(c: Chapitre): void {
    this.formChapitre.set({ id: c.id, titre: c.titre, dureeMinutes: c.dureeMinutes, contenu: c.contenu ?? '', videoUrl: c.videoUrl ?? '' });
    this.ouvrirFormulaire();
  }

  private ouvrirFormulaire(): void {
    this.erreursChapitre.set({});
    this.ref = this.dialog.open(this.dialogChapitre(), { width: '640px', maxWidth: 'calc(100vw - 32px)', autoFocus: 'first-tabbable' });
  }

  protected majFormChapitre<K extends keyof FormChapitre>(champ: K, valeur: FormChapitre[K]): void {
    this.formChapitre.update((f) => ({ ...f, [champ]: valeur }));
  }

  protected enregistrerChapitre(): void {
    const cours = this.existant();
    if (!cours || this.chapitreEnvoi()) return;
    const f = this.formChapitre();
    const body: ChapitreRequest = {
      titre: f.titre.trim(),
      contenu: f.contenu.trim() || undefined,
      dureeMinutes: Number(f.dureeMinutes) || 0,
      videoUrl: f.videoUrl.trim() || undefined,
    };
    this.erreursChapitre.set({});
    this.chapitreEnvoi.set(true);
    const appel = f.id === null ? this.service.creerChapitre(cours.id, body) : this.service.modifierChapitre(cours.id, f.id, body);
    appel.subscribe({
      next: (c) => {
        this.chapitres.update((l) => (f.id === null ? [...l, c] : l.map((x) => (x.id === c.id ? c : x))));
        this.chapitreEnvoi.set(false);
        this.ref?.close();
        this.snack.open(f.id === null ? 'Chapitre ajouté' : 'Chapitre modifié', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.chapitreEnvoi.set(false);
        const details = (err as { error?: { details?: Details } })?.error?.details;
        if (details) this.erreursChapitre.set(details);
        else this.snack.open(errorMessage(err, 'Enregistrement du chapitre impossible'), 'OK', { duration: 5000 });
      },
    });
  }

  protected demanderSuppressionChapitre(c: Chapitre): void {
    this.chapitreASupprimer.set(c);
    this.ref = this.dialog.open(this.dialogSuppression(), { width: '440px', maxWidth: 'calc(100vw - 32px)' });
  }

  protected supprimerChapitre(): void {
    const cours = this.existant();
    const c = this.chapitreASupprimer();
    if (!cours || !c) return;
    this.chapitreEnvoi.set(true);
    this.service.supprimerChapitre(cours.id, c.id).subscribe({
      next: () => {
        this.chapitres.update((l) => l.filter((x) => x.id !== c.id).map((x, i) => ({ ...x, ordre: i + 1 })));
        this.chapitreEnvoi.set(false);
        this.ref?.close();
        this.snack.open('Chapitre supprimé', 'OK', { duration: 3000 });
      },
      error: (err) => {
        this.chapitreEnvoi.set(false);
        this.ref?.close();
        this.snack.open(errorMessage(err, 'Suppression impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  /** Glisser-déposer : on affiche le nouvel ordre tout de suite, puis on l'enregistre ; en cas d'échec on revient en arrière. */
  protected deplacer(e: CdkDragDrop<unknown>): void {
    const cours = this.existant();
    if (!cours || e.previousIndex === e.currentIndex) return;
    const avant = this.chapitres();
    const apres = [...avant];
    moveItemInArray(apres, e.previousIndex, e.currentIndex);
    this.chapitres.set(apres.map((c, i) => ({ ...c, ordre: i + 1 })));
    this.service.ordonnerChapitres(cours.id, apres.map((c) => c.id)).subscribe({
      next: (l) => this.chapitres.set(l),
      error: (err) => {
        this.chapitres.set(avant);
        this.snack.open(errorMessage(err, 'Le nouvel ordre n’a pas pu être enregistré'), 'OK', { duration: 5000 });
      },
    });
  }

  // ------------------------------------------------------------------ PDF

  protected choisirPdf(fichiers: File[]): void {
    const f = fichiers[0];
    const cours = this.existant();
    this.erreurPdf.set(null);
    if (!f || !cours) return;
    if (f.type !== 'application/pdf' && !f.name.toLowerCase().endsWith('.pdf')) {
      this.erreurPdf.set('Format non pris en charge : PDF uniquement.');
      return;
    }
    if (f.size > PDF_MAX) {
      this.erreurPdf.set('Le PDF dépasse 20 Mo.');
      return;
    }
    this.pdfEnvoi.set(true);
    this.pdfScanne.set(null);
    this.service.envoyerPdf(cours.id, f).subscribe({
      next: (c) => {
        this.existant.set(c);
        this.pdfEnvoi.set(false);
        this.snack.open('PDF enregistré', 'OK', { duration: 3000 });
        this.analyserPdf();
      },
      error: (err) => {
        this.pdfEnvoi.set(false);
        this.erreurPdf.set(errorMessage(err, 'Envoi du PDF impossible'));
      },
    });
  }

  /** Demande le texte extrait pour savoir si le PDF est exploitable (un PDF scanné ne contient pas de texte). */
  private analyserPdf(): void {
    const cours = this.existant();
    if (!cours) return;
    this.service.texte(cours.id).subscribe({
      next: (t) => {
        this.pdfScanne.set(t.estScanne ?? null);
        this.pdfPages.set(t.pages?.length ?? 0);
      },
      error: () => this.pdfScanne.set(null),
    });
  }

  /** Le PDF exige le JWT : on le télécharge avec HttpClient, puis on l'ouvre dans un nouvel onglet. */
  protected ouvrirPdf(): void {
    const cours = this.existant();
    if (!cours) return;
    this.service.pdf(cours.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60_000);
      },
      error: (err) => this.snack.open(errorMessage(err, 'Impossible d’ouvrir le PDF'), 'OK', { duration: 5000 }),
    });
  }

  // ------------------------------------------------------------------ examen final (pas encore branché)

  protected majExamen(champ: 'questions' | 'duree' | 'seuil' | 'tentatives', v: number): void {
    this.examen.update((e) => ({ ...e, [champ]: Number(v) || 0 }));
  }

  // ------------------------------------------------------------------ résumé IA (fiche de révision)

  protected chargerFiche(): void {
    const cours = this.existant();
    if (!cours) return;
    this.ficheEtat.set('chargement');
    this.service.fiche(cours.id).subscribe({
      next: (f) => {
        this.fiche.set(f);
        this.ficheEtat.set('ok');
      },
      error: (err) => {
        // 404 : le cours n'a pas encore de fiche, ce n'est pas une erreur.
        if ((err as { status?: number })?.status === 404) {
          this.fiche.set(null);
          this.ficheEtat.set('ok');
        } else {
          this.ficheEtat.set('erreur');
          this.snack.open(errorMessage(err, 'Impossible de charger la fiche'), 'OK', { duration: 5000 });
        }
      },
    });
  }

  /** Lance la génération (la première fois comme les suivantes : l'historique des fiches est conservé côté serveur). */
  protected genererFiche(): void {
    const cours = this.existant();
    if (!cours || this.lancement() || this.attente()) return;
    this.lancement.set(true);
    this.brouillon.set(null);
    this.service.genererFiche(cours.id).subscribe({
      next: (t) => {
        this.lancement.set(false);
        this.traitement.set(t);
        this.suivre();
      },
      error: (err) => {
        this.lancement.set(false);
        this.snack.open(errorMessage(err, 'Impossible de lancer la génération'), 'OK', { duration: 7000 });
      },
    });
  }

  /** Au chargement du cours : retrouve un traitement encore en cours (page rechargée) ou le dernier échec. */
  private reprendreSuivi(): void {
    const cours = this.existant();
    if (!cours) return;
    this.service.traitement(cours.id).subscribe({
      next: (t) => {
        this.traitement.set(t);
        if (t.enCours) this.suivre();
      },
      error: () => undefined,
    });
  }

  /** Interroge le serveur toutes les 2 s jusqu'à la fin du traitement (au plus 5 min), puis recharge la fiche. */
  private suivre(): void {
    const cours = this.existant();
    if (!cours) return;
    this.arreterSuivi();
    this.attente.set(true);
    this.suivi = timer(0, 2000)
      .pipe(
        switchMap(() => this.service.traitement(cours.id).pipe(catchError(() => of(null)))),
        takeWhile((t) => t === null || t.enCours, true),
        take(150),
      )
      .subscribe({
        next: (t) => {
          if (!t) return;
          this.traitement.set(t);
          if (!t.enCours) this.finDeTraitement();
        },
        complete: () => this.attente.set(false),
      });
  }

  private finDeTraitement(): void {
    this.attente.set(false);
    this.chargerFiche();
    const echec = this.echecIA();
    if (echec) {
      this.snack.open(echec.erreur ?? 'La génération a échoué.', 'OK', { duration: 8000 });
    }
  }

  private arreterSuivi(): void {
    this.suivi?.unsubscribe();
    this.suivi = undefined;
  }

  /** « Annuler » de l'attente : on arrête de surveiller ; le traitement, lui, continue sur le serveur. */
  protected arreterAttente(): void {
    this.arreterSuivi();
    this.attente.set(false);
    this.snack.open('La génération continue en arrière-plan. Reviens sur cette page dans un instant.', 'OK', { duration: 6000 });
  }

  protected statutEtape(etape: EtapeIA): StatutTraitement {
    return this.traitement()?.etapes.find((e) => e.etape === etape)?.statut ?? 'EN_ATTENTE';
  }

  protected libelleEtape(etape: EtapeIA): string {
    return this.etapesIA.find((e) => e.etape === etape)?.libelle ?? etape;
  }

  protected iconeEtape(statut: StatutTraitement): string {
    switch (statut) {
      case 'TERMINE': return 'check_circle';
      case 'EN_COURS': return 'autorenew';
      case 'ECHEC': return 'error';
      case 'ANNULE': return 'block';
      default: return 'radio_button_unchecked';
    }
  }

  // ---- Correction par le formateur

  protected modifierFiche(): void {
    const f = this.fiche();
    if (!f) return;
    this.brouillon.set({
      resume: f.resume,
      motsCles: f.motsCles.join(', '),
      notions: f.notions.map((n) => ({ titre: n.titre, definition: n.definition, extraitSource: n.extraitSource ?? '' })),
    });
  }

  protected annulerCorrection(): void {
    this.brouillon.set(null);
  }

  protected majBrouillon(champ: 'resume' | 'motsCles', valeur: string): void {
    this.brouillon.update((b) => (b ? { ...b, [champ]: valeur } : b));
  }

  protected majNotion(index: number, champ: 'titre' | 'definition' | 'extraitSource', valeur: string): void {
    this.brouillon.update((b) => (b ? { ...b, notions: b.notions.map((n, i) => (i === index ? { ...n, [champ]: valeur } : n)) } : b));
  }

  protected ajouterNotion(): void {
    this.brouillon.update((b) => (b ? { ...b, notions: [...b.notions, { titre: '', definition: '', extraitSource: '' }] } : b));
  }

  protected retirerNotion(index: number): void {
    this.brouillon.update((b) => (b ? { ...b, notions: b.notions.filter((_, i) => i !== index) } : b));
  }

  /** Enregistre la correction : le serveur recalcule le score de fidélité avec le texte du cours. */
  protected enregistrerCorrection(): void {
    const cours = this.existant();
    const b = this.brouillon();
    if (!cours || !b || this.ficheEnvoi()) return;
    const corps: FicheRequest = {
      resume: b.resume.trim(),
      motsCles: b.motsCles.split(/[,\n]/).map((m) => m.trim()).filter(Boolean),
      notions: b.notions
        .filter((n) => n.titre.trim() || n.definition.trim())
        .map((n) => ({ titre: n.titre.trim(), definition: n.definition.trim(), extraitSource: n.extraitSource.trim() })),
    };
    this.ficheEnvoi.set(true);
    this.service.corrigerFiche(cours.id, corps).subscribe({
      next: (f) => {
        this.ficheEnvoi.set(false);
        this.fiche.set(f);
        this.brouillon.set(null);
        this.snack.open(`Fiche enregistrée. Fidélité recalculée : ${f.scoreFidelite ?? '—'} / 100`, 'OK', { duration: 5000 });
      },
      error: (err) => {
        this.ficheEnvoi.set(false);
        this.snack.open(errorMessage(err, 'Enregistrement de la fiche impossible'), 'OK', { duration: 7000 });
      },
    });
  }

  /** Rend la fiche visible des apprenants. Si son audio manque, le serveur le produit en arrière-plan : on le suit. */
  protected validerFiche(): void {
    const cours = this.existant();
    if (!cours || this.ficheEnvoi()) return;
    this.ficheEnvoi.set(true);
    this.service.validerFiche(cours.id).subscribe({
      next: (f) => {
        this.ficheEnvoi.set(false);
        this.fiche.set(f);
        this.snack.open('Fiche validée : les apprenants peuvent la lire.', 'OK', { duration: 4000 });
        if (!f.aAudio) this.reprendreSuivi();
      },
      error: (err) => {
        this.ficheEnvoi.set(false);
        this.snack.open(errorMessage(err, 'Validation impossible'), 'OK', { duration: 6000 });
      },
    });
  }

  // ------------------------------------------------------------------ publication

  /** Enregistre les informations et le prix, puis passe le cours en PUBLIE ou en BROUILLON selon l'interrupteur. */
  protected publier(): void {
    const cours = this.existant();
    if (!cours || this.envoi()) return;
    this.erreurs.set({});
    const body = this.corps();
    if (!body) return;
    const statut = this.visible() ? 'PUBLIE' : 'BROUILLON';
    this.envoi.set(true);
    this.service.update(cours.id, body).subscribe({
      next: () =>
        this.service.changerStatut(cours.id, statut).subscribe({
          next: (c) => {
            this.envoi.set(false);
            this.appliquer(c);
            this.snack.open(statut === 'PUBLIE' ? 'Cours publié' : 'Brouillon enregistré', 'OK', { duration: 3000 });
            this.router.navigate(['/studio/cours']);
          },
          error: (err) => {
            this.envoi.set(false);
            this.snack.open(errorMessage(err, 'Changement de statut impossible'), 'OK', { duration: 6000 });
          },
        }),
      error: (err) => {
        this.envoi.set(false);
        this.erreurApi(err, 'Enregistrement impossible');
      },
    });
  }
}
