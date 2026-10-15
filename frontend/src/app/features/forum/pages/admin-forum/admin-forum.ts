import { SlicePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { BwChart, ChartSerie, Kpi, PageHeader } from '../../../../shared/ui';
import { LIBELLES_CATEGORIE, MOTIFS, TempsRelatifPipe, messageErreur, pourcent } from '../../components/outils';
import {
  ActionModeration,
  AdminStatistiques,
  CategorieModeration,
  HistoriqueMembre,
  JournalModeration,
  PageResponse,
  Sanction,
  Signalement,
  StatutSignalement,
  Supervision,
  TypeSanction,
} from '../../models';
import { FiltresJournal, ModerationForumService } from '../../services/moderation.service';

/** Libellés et aides des réglages globaux (clés de ParametresService côté serveur). */
const REGLAGES: { cle: string; libelle: string; aide: string }[] = [
  { cle: 'moderation.seuil-masquage', libelle: 'Seuil de masquage automatique', aide: 'Score de l’IA (0 à 1) à partir duquel un message est masqué. 0,85 par défaut.' },
  { cle: 'moderation.seuil-signalement', libelle: 'Seuil de signalement au formateur', aide: 'Score à partir duquel le message est signalé sans être masqué. 0,5 par défaut.' },
  { cle: 'moderation.mots-interdits', libelle: 'Mots interdits', aide: 'Séparés par des virgules ; comparés sans accents ni majuscules.' },
  { cle: 'flood.questions-par-heure', libelle: 'Questions par heure et par membre', aide: 'Limite anti-flood.' },
  { cle: 'flood.messages-par-heure', libelle: 'Messages par heure et par membre', aide: 'Limite anti-flood (réponses et commentaires).' },
  { cle: 'vote.points-minimum-negatif', libelle: 'Points requis pour voter contre', aide: 'Évite les abus de votes négatifs.' },
  { cle: 'similarite.seuil', libelle: 'Seuil « question similaire »', aide: 'Cosinus minimal pour proposer une question proche.' },
  { cle: 'similarite.seuil-doublon', libelle: 'Seuil « doublon probable »', aide: 'Cosinus à partir duquel la publication demande confirmation.' },
  { cle: 'vigie.seuil-pertinence', libelle: 'Seuil de pertinence de La Vigie', aide: 'En dessous, La Vigie s’abstient (« Le cours ne traite pas ce point »).' },
  { cle: 'vigie.reponse-generale-active', libelle: 'Réponse générale hors cours (plateforme)', aide: 'true ou false : coupe le niveau 3 sur toute la plateforme.' },
];

const ACTIONS: ActionModeration[] = [
  'MASQUER', 'MASQUAGE_AUTO', 'RETABLIR', 'VERROUILLER', 'DEVERROUILLER', 'EPINGLER', 'DESEPINGLER', 'DEPLACER', 'FUSIONNER',
  'MODIFIER_ETIQUETTES', 'SUPPRIMER', 'DECIDER_SIGNALEMENT', 'ESCALADER', 'SANCTIONNER', 'LEVER_SANCTION', 'VALIDER_VIGIE',
  'CORRIGER_VIGIE', 'REJETER_VIGIE', 'MODIFIER_PARAMETRES',
];

/**
 * Espace admin du forum (plan §7.7) : santé de la plateforme, file de modération globale (escalades et décisions
 * de l'IA), membres et sanctions motivées, supervision de La Vigie (coût, fiabilité, sécurité), réglages globaux,
 * journal de modération avec export CSV. Réservé à l'ADMIN, revérifié par forum-service.
 */
@Component({
  selector: 'app-admin-forum',
  imports: [
    FormsModule, SlicePipe, RouterLink, MatIconModule, MatSelectModule, MatTabsModule, MatButtonToggleModule, MatSlideToggleModule,
    BwButton, EmptyState, BwChart, Kpi, PageHeader, TempsRelatifPipe,
  ],
  templateUrl: './admin-forum.html',
  styleUrl: './admin-forum.scss',
})
export class AdminForum implements OnInit {
  private readonly moderation = inject(ModerationForumService);
  private readonly snack = inject(MatSnackBar);

  protected readonly pourcent = pourcent;
  protected readonly categories = LIBELLES_CATEGORIE;
  protected readonly listeCategories = Object.keys(LIBELLES_CATEGORIE).filter((c) => c !== 'OK') as CategorieModeration[];
  protected readonly motifs = Object.fromEntries(MOTIFS.map((m) => [m.code, m.libelle]));
  protected readonly reglagesConnus = REGLAGES;
  protected readonly actions = ACTIONS;

  protected readonly periode = signal(30);
  protected readonly stats = signal<AdminStatistiques | null>(null);
  protected readonly supervision = signal<Supervision | null>(null);
  protected readonly statutFile = signal<StatutSignalement | 'TOUS'>('OUVERT');
  protected readonly categorie = signal<CategorieModeration | null>(null);
  protected readonly escalades = signal(false);
  protected readonly file = signal<Signalement[]>([]);
  protected readonly motifDecision = signal<Partial<Record<number, string>>>({});
  protected readonly membreId = signal<number | null>(null);
  protected readonly historique = signal<HistoriqueMembre | null>(null);
  protected readonly sanctions = signal<Sanction[]>([]);
  protected readonly typeSanction = signal<TypeSanction>('LECTURE_SEULE');
  protected readonly duree = signal<number | null>(168);
  protected readonly motifSanction = signal('');
  protected readonly valeurs = signal<Partial<Record<string, string>>>({});
  protected readonly journal = signal<PageResponse<JournalModeration> | null>(null);
  protected readonly filtresJournal = signal<FiltresJournal>({});
  protected readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    this.chargerVue();
    this.chargerFile();
    this.chargerSanctions();
    this.moderation.parametresGlobaux().subscribe({ next: (p) => this.valeurs.set(p.valeurs) });
    this.chargerJournal(0);
  }

  // ------------------------------------------------------------ Vue d'ensemble et supervision

  protected chargerVue(): void {
    this.moderation.statistiquesAdmin(this.periode()).subscribe({ next: (s) => this.stats.set(s), error: (e) => this.erreur.set(messageErreur(e)) });
    this.moderation.supervision(this.periode()).subscribe({ next: (s) => this.supervision.set(s) });
  }

  protected serieSemaines(s: AdminStatistiques): ChartSerie[] {
    return [{ label: 'Questions', data: s.global.parSemaine.map((x) => x.valeur), color: 1 }];
  }

  protected semaines(s: AdminStatistiques): string[] {
    return s.global.parSemaine.map((x) => x.libelle);
  }

  protected serieNiveaux(s: Supervision): ChartSerie[] {
    return [{ label: 'Réponses', data: [s.parNiveau.COURS ?? 0, s.parNiveau.FORUM ?? 0, s.parNiveau.GENERAL ?? 0] }];
  }

  protected serieJours(s: Supervision): ChartSerie[] {
    return [{ label: 'Appels', data: s.appelsParJour.map((x) => x.valeur), color: 3 }];
  }

  protected jours(s: Supervision): string[] {
    return s.appelsParJour.map((x) => x.libelle);
  }

  protected fournisseurs(s: Supervision): { nom: string; n: number }[] {
    return Object.entries(s.parFournisseur).map(([nom, n]) => ({ nom, n }));
  }

  protected reindexerTout(): void {
    this.moderation.reindexerTout().subscribe({ next: (n) => this.toast(`${n} cours en cours de réindexation.`), error: (e) => this.toast(messageErreur(e)) });
  }

  // ------------------------------------------------------------ Modération

  protected chargerFile(): void {
    this.moderation.fileAdmin(this.statutFile(), this.categorie(), this.escalades()).subscribe({ next: (f) => this.file.set(f) });
  }

  protected motif(id: number, texte: string): void {
    this.motifDecision.set({ ...this.motifDecision(), [id]: texte });
  }

  protected decider(s: Signalement, statut: StatutSignalement, masquer: boolean): void {
    const motif = (this.motifDecision()[s.id] ?? '').trim();
    if (!motif) {
      this.toast('Le motif est obligatoire.');
      return;
    }
    this.moderation.decider(s.id, { statut, masquer, escalader: false, motif }).subscribe({
      next: () => {
        this.toast('Décision enregistrée.');
        this.chargerFile();
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Membres et sanctions

  protected voirMembre(id: number | undefined): void {
    if (!id) {
      return;
    }
    this.membreId.set(id);
    this.moderation.historique(id).subscribe({ next: (h) => this.historique.set(h), error: (e) => this.toast(messageErreur(e)) });
  }

  protected chargerSanctions(): void {
    this.moderation.sanctions(true).subscribe({ next: (s) => this.sanctions.set(s) });
  }

  protected sanctionner(): void {
    const userId = this.membreId();
    const motif = this.motifSanction().trim();
    if (!userId || !motif) {
      this.toast('Choisis un membre et écris le motif.');
      return;
    }
    const type = this.typeSanction();
    this.moderation
      .sanctionner({ userId, type, motif, dureeHeures: type === 'AVERTISSEMENT' ? undefined : (this.duree() ?? undefined) })
      .subscribe({
        next: () => {
          this.toast('Sanction enregistrée ; le membre est prévenu.');
          this.motifSanction.set('');
          this.chargerSanctions();
          this.voirMembre(userId);
        },
        error: (e) => this.toast(messageErreur(e)),
      });
  }

  protected lever(s: Sanction): void {
    this.moderation.lever(s.id).subscribe({
      next: () => {
        this.toast('Sanction levée.');
        this.chargerSanctions();
        if (this.membreId()) {
          this.voirMembre(this.membreId()!);
        }
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Réglages

  protected modifierValeur(cle: string, valeur: string): void {
    this.valeurs.set({ ...this.valeurs(), [cle]: valeur });
  }

  protected enregistrerReglages(): void {
    this.moderation.modifierParametresGlobaux(this.valeurs() as Record<string, string>).subscribe({
      next: (p) => {
        this.valeurs.set(p.valeurs);
        this.toast('Réglages enregistrés.');
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Journal

  protected filtrerJournal(modif: Partial<FiltresJournal>): void {
    this.filtresJournal.set({ ...this.filtresJournal(), ...modif });
    this.chargerJournal(0);
  }

  protected chargerJournal(page: number): void {
    this.moderation.journal({ ...this.filtresJournal(), page }).subscribe({ next: (j) => this.journal.set(j) });
  }

  protected exporter(): void {
    this.moderation.journalCsv(this.filtresJournal()).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'journal-moderation.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  private toast(message: string): void {
    this.snack.open(message, 'OK', { duration: 4000 });
  }
}
