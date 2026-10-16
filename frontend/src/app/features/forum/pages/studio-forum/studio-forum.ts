import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { AuthService } from '../../../../core/auth/auth.service';
import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, ChartSerie, Kpi, PageHeader } from '../../../../shared/ui';
import { ChampAide } from '../../components/champ-aide';
import { DiscussionLigne } from '../../components/discussion-ligne';
import { MotifChamp } from '../../components/motif-champ';
import {
  LIBELLES_CATEGORIE,
  LIBELLES_STATUT_SIGNALEMENT,
  MOTIFS,
  TempsRelatifPipe,
  depuisChampDate,
  messageErreur,
  pourcent,
  versChampDate,
} from '../../components/outils';
import { REGLES, erreurMotif, erreurTexte } from '../../components/validation';
import { VigieBox } from '../../components/vigie-box';
import {
  ApprenantBloque,
  CoursOption,
  DiscussionResume,
  FiltreVigie,
  IndexCours,
  ParametresCours,
  Signalement,
  StatutSignalement,
  Statistiques,
  Tendances,
  VigieAVerifier,
  VigieResponse,
} from '../../models';
import { ForumService } from '../../services/forum.service';
import { ModerationForumService } from '../../services/moderation.service';
import { VigieService } from '../../services/vigie.service';

/**
 * Studio formateur, onglet Forum (plan §7.6) : indicateurs de la semaine, puis À traiter, Réponses de La Vigie,
 * Signalements, Tendances, Index du cours et Réglages du forum, pour un cours dont l'utilisateur est le formateur
 * (l'admin voit tous les cours). Les droits sont revérifiés par forum-service.
 */
@Component({
  selector: 'app-studio-forum',
  imports: [
    FormsModule, RouterLink, MatIconModule, MatSelectModule, MatTabsModule, MatButtonToggleModule, MatSlideToggleModule,
    BwButton, EmptyState, BwChart, Kpi, PageHeader, DiscussionLigne, VigieBox, TempsRelatifPipe, ChampAide, MotifChamp,
  ],
  templateUrl: './studio-forum.html',
  styleUrl: './studio-forum.scss',
})
export class StudioForum implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly vigie = inject(VigieService);
  private readonly moderation = inject(ModerationForumService);
  private readonly auth = inject(AuthService);
  private readonly snack = inject(MatSnackBar);

  protected readonly pourcent = pourcent;
  protected readonly categories = LIBELLES_CATEGORIE;
  protected readonly motifs = Object.fromEntries(MOTIFS.map((m) => [m.code, m.libelle]));
  protected readonly libellesStatut = LIBELLES_STATUT_SIGNALEMENT;
  protected readonly versChampDate = versChampDate;
  protected readonly maxModele = REGLES.modele.max;
  protected readonly cours = signal<CoursOption[]>([]);
  protected readonly coursId = signal<number | null>(null);
  protected readonly stats = signal<Statistiques | null>(null);
  protected readonly sansReponse = signal<DiscussionResume[]>([]);
  protected readonly aVerifier = signal<VigieAVerifier[]>([]);
  protected readonly filtreVigie = signal<FiltreVigie>('A_VERIFIER');
  protected readonly reponsesVigie = signal<VigieAVerifier[]>([]);
  protected readonly bloques = signal<ApprenantBloque[]>([]);
  protected readonly statutFile = signal<StatutSignalement | 'TOUS'>('OUVERT');
  protected readonly file = signal<Signalement[]>([]);
  protected readonly motifDecision = signal<Partial<Record<number, string>>>({});
  protected readonly tendances = signal<Tendances | null>(null);
  protected readonly index = signal<IndexCours | null>(null);
  protected readonly reglages = signal<ParametresCours | null>(null);
  protected readonly occupe = signal(false);
  protected readonly erreur = signal<string | null>(null);

  protected readonly urgentes = computed(() => {
    const limite = Date.now() - 24 * 3600 * 1000;
    return this.sansReponse().filter((d) => new Date(d.creeLe).getTime() < limite);
  });
  protected readonly erreurModele = computed(() => erreurTexte(REGLES.modele, this.reglages()?.questionModele));
  protected readonly erreurFinExamen = computed(() => {
    const r = this.reglages();
    if (!r?.modeExamen || !r.modeExamenFin) {
      return null;
    }
    return new Date(r.modeExamenFin).getTime() > Date.now() ? null : 'La fin du mode examen doit être dans le futur';
  });
  protected readonly faibles = computed(() =>
    this.aVerifier().filter((v) => v.reponse.statut === 'HORS_COURS' || v.reponse.statut === 'ERREUR' || (v.reponse.confiance ?? 100) < 50),
  );

  ngOnInit(): void {
    const moi = this.auth.currentUser();
    this.forum.cours().subscribe({
      next: (tous) => {
        const miens = moi?.role === 'ADMIN' ? tous : tous.filter((c) => c.enseignantId === moi?.id);
        this.cours.set(miens);
        if (miens.length) {
          this.choisir(miens[0].id);
        }
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected choisir(coursId: number): void {
    this.coursId.set(coursId);
    this.erreur.set(null);
    this.moderation.statistiques(coursId, 7).subscribe({ next: (s) => this.stats.set(s), error: (e) => this.erreur.set(messageErreur(e)) });
    this.forum.lister({ coursId, statut: 'SANS_REPONSE', tri: 'recentes', taille: 50 }).subscribe({ next: (p) => this.sansReponse.set(p.contenu) });
    this.vigie.duCours(coursId, 'A_VERIFIER').subscribe({ next: (v) => this.aVerifier.set(v) });
    this.moderation.apprenantsBloques(coursId).subscribe({ next: (b) => this.bloques.set(b) });
    this.chargerVigie();
    this.chargerFile();
    this.tendances.set(null);
    this.moderation.tendances(coursId).subscribe({ next: (t) => this.tendances.set(t) });
    this.vigie.index(coursId).subscribe({ next: (i) => this.index.set(i) });
    this.moderation.parametresCours(coursId).subscribe({ next: (r) => this.reglages.set(r) });
  }

  // ------------------------------------------------------------ La Vigie

  protected chargerVigie(): void {
    this.vigie.duCours(this.coursId()!, this.filtreVigie()).subscribe({ next: (v) => this.reponsesVigie.set(v) });
  }

  protected decisionVigie(appel: 'valider' | 'corriger' | 'rejeter', r: VigieResponse, saisie?: { texte?: string; motif: string }): void {
    this.occupe.set(true);
    const action =
      appel === 'valider'
        ? this.vigie.valider(r.id)
        : appel === 'corriger'
          ? this.vigie.corriger(r.id, saisie!.texte!, saisie!.motif)
          : this.vigie.rejeter(r.id, saisie!.motif);
    action.subscribe({
      next: () => {
        this.occupe.set(false);
        this.toast(appel === 'valider' ? 'Réponse validée.' : appel === 'corriger' ? 'Correction publiée.' : 'Réponse rejetée.');
        this.chargerVigie();
        this.vigie.duCours(this.coursId()!, 'A_VERIFIER').subscribe({ next: (v) => this.aVerifier.set(v) });
      },
      error: (e) => {
        this.occupe.set(false);
        this.toast(messageErreur(e));
      },
    });
  }

  protected regenerer(discussionId: number): void {
    this.vigie.regenerer(discussionId).subscribe({
      next: () => {
        this.toast('La Vigie prépare une nouvelle réponse.');
        setTimeout(() => this.chargerVigie(), 3000);
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Signalements

  protected chargerFile(): void {
    this.moderation.file(this.coursId()!, this.statutFile()).subscribe({ next: (f) => this.file.set(f) });
  }

  protected motif(id: number, texte: string): void {
    this.motifDecision.set({ ...this.motifDecision(), [id]: texte });
  }

  protected motifInvalide(id: number): boolean {
    return erreurMotif(this.motifDecision()[id]) !== null;
  }

  protected decider(s: Signalement, statut: StatutSignalement, masquer: boolean, escalader = false): void {
    const motif = (this.motifDecision()[s.id] ?? '').trim();
    if (erreurMotif(motif)) {
      return;
    }
    this.moderation.decider(s.id, { statut, masquer, escalader, motif }).subscribe({
      next: () => {
        this.toast(escalader ? 'Transmis à l’administration.' : 'Décision enregistrée.');
        this.chargerFile();
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Tendances, index, réglages

  protected ajouterFaq(d: DiscussionResume): void {
    this.moderation.ajouterFaq(this.coursId()!, d.id).subscribe({
      next: () => {
        this.toast('Ajouté à la FAQ du cours, épinglé en tête du forum.');
        this.moderation.tendances(this.coursId()!).subscribe({ next: (t) => this.tendances.set(t) });
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected reindexer(): void {
    this.vigie.reindexer(this.coursId()!).subscribe({
      next: (i) => {
        this.index.set(i);
        this.toast('Réindexation lancée.');
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected modifierReglage(cle: keyof ParametresCours, valeur: unknown): void {
    this.reglages.update((r) => (r ? { ...r, [cle]: valeur } : r));
  }

  protected enregistrerReglages(): void {
    const r = this.reglages();
    if (!r || this.erreurModele() || this.erreurFinExamen()) {
      return;
    }
    const corps = { ...r, modeExamenFin: r.modeExamen ? depuisChampDate(r.modeExamenFin) : undefined };
    this.moderation.modifierParametresCours(this.coursId()!, corps).subscribe({
      next: (x) => {
        this.reglages.set(x);
        this.toast('Réglages du forum enregistrés.');
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected libellesChapitres(t: Tendances): string[] {
    return t.parChapitre.map((s) => s.libelle);
  }

  protected seriesChapitres(t: Tendances): ChartSerie[] {
    return [{ label: 'Questions', data: t.parChapitre.map((s) => s.valeur), color: 1 }];
  }

  private toast(message: string): void {
    this.snack.open(message, 'OK', { duration: 4000 });
  }
}
