import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { AiBox, Badge, BwButton, EmptyState } from '../../../../shared';
import { Avatar, Skeleton } from '../../../../shared/ui';
import { ForumEditeur } from '../../components/editeur';
import { ForumMarkdown } from '../../components/markdown';
import { LIBELLES_STATUT, MOTIFS, TempsRelatifPipe, messageErreur } from '../../components/outils';
import { VigieBox } from '../../components/vigie-box';
import {
  Commentaire,
  DiscussionDetail,
  MotifSignalement,
  Reponse,
  ResumeFil,
  Similaire,
  TypeReaction,
  VersionDiscussion,
  VigieFil,
  VigieResponse,
} from '../../models';
import { ForumService } from '../../services/forum.service';
import { ModerationForumService, OutilFil } from '../../services/moderation.service';
import { VigieService } from '../../services/vigie.service';

const REACTIONS: { type: TypeReaction; emoji: string; libelle: string }[] = [
  { type: 'MERCI', emoji: '👍', libelle: 'Merci' },
  { type: 'APPRIS', emoji: '💡', libelle: 'J’ai appris' },
  { type: 'PILE', emoji: '🎯', libelle: 'Pile ce qu’il fallait' },
];

/**
 * Fil de discussion (plan §7.3), commun aux trois espaces (base /app, /studio ou /admin) : question, « En bref »,
 * La Vigie (sources, confiance, Guide-moi, réponse générale à la demande, décisions du formateur), réponses
 * (meilleure réponse, approbation, votes, réactions, commentaires, citer), éditeur de réponse, outils de modération,
 * discussions liées. La réponse de La Vigie en attente est relue toutes les 3 secondes.
 */
@Component({
  selector: 'app-forum-thread',
  imports: [
    FormsModule, NgTemplateOutlet, RouterLink, MatIconModule, MatMenuModule, MatCheckboxModule, MatTooltipModule,
    AiBox, Badge, BwButton, EmptyState, Avatar, Skeleton, ForumEditeur, ForumMarkdown, VigieBox, TempsRelatifPipe,
  ],
  templateUrl: './forum-thread.html',
  styleUrl: './forum-thread.scss',
})
export class ForumThread {
  /** Paramètre de route :id (liaison des entrées du routeur). */
  readonly id = input.required<string>();

  private readonly forum = inject(ForumService);
  private readonly vigieApi = inject(VigieService);
  private readonly moderation = inject(ModerationForumService);
  private readonly auth = inject(AuthService);
  protected readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);

  protected readonly base = this.router.url.startsWith('/studio') ? '/studio/forum' : this.router.url.startsWith('/admin') ? '/admin/forum' : '/app/forum';
  protected readonly apprenant = computed(() => this.auth.role() === 'ETUDIANT');
  protected readonly reactions = REACTIONS;
  protected readonly motifs = MOTIFS;
  protected readonly statuts = LIBELLES_STATUT;

  protected readonly d = signal<DiscussionDetail | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly vigie = signal<VigieFil | null>(null);
  protected readonly vigieOccupee = signal(false);
  protected readonly resume = signal<ResumeFil | null>(null);
  protected readonly liees = signal<Similaire[]>([]);
  protected readonly versions = signal<VersionDiscussion[] | null>(null);
  protected readonly reponse = signal('');
  protected readonly anonyme = signal(false);
  protected readonly envoi = signal(false);
  protected readonly commentairesOuverts = signal<Set<string>>(new Set());
  protected readonly commentaire = signal<Partial<Record<string, string>>>({});
  protected readonly edition = signal<{ titre: string; contenu: string } | null>(null);
  protected readonly editionReponse = signal<{ id: number; contenu: string } | null>(null);
  protected readonly cible = signal<number | null>(null);

  protected readonly moderateur = computed(() => this.d()?.droits.moderer ?? false);
  protected readonly reponsesTriees = computed(() => this.d()?.reponses ?? []);
  protected readonly participants = computed(() => {
    const d = this.d();
    if (!d) {
      return [];
    }
    const noms = new Map<string, boolean>();
    for (const a of [d.auteur, ...d.reponses.map((r) => r.auteur)]) {
      if (a.nom && !noms.has(a.nom)) {
        noms.set(a.nom, a.formateurDuCours);
      }
    }
    return [...noms.entries()].map(([nom, formateur]) => ({ nom, formateur }));
  });

  private sondage?: ReturnType<typeof setInterval>;
  private essais = 0;

  constructor() {
    effect(() => {
      const id = Number(this.id());
      untracked(() => this.charger(id));
    });
    inject(DestroyRef).onDestroy(() => this.arreterSondage());
  }

  // ------------------------------------------------------------ Chargement

  private charger(id: number): void {
    this.erreur.set(null);
    this.forum.lire(id).subscribe({
      next: (d) => {
        this.d.set(d);
        this.surligner();
      },
      error: (e) => this.erreur.set(messageErreur(e, 'Cette discussion est introuvable.')),
    });
    this.chargerVigie(id);
    this.vigieApi.resume(id).subscribe({ next: (r) => this.resume.set(r), error: () => this.resume.set(null) });
    this.forum.liees(id).subscribe({ next: (r) => this.liees.set(r ?? []), error: () => this.liees.set([]) });
  }

  private recharger(): void {
    const d = this.d();
    if (d) {
      this.forum.lire(d.id).subscribe({ next: (x) => this.d.set(x) });
    }
  }

  private chargerVigie(id: number): void {
    this.vigieApi.fil(id).subscribe({
      next: (v) => {
        this.vigie.set(v);
        if (v.principale?.statut === 'EN_ATTENTE') {
          this.demarrerSondage(id);
        } else {
          this.arreterSondage();
        }
      },
      error: () => this.vigie.set(null),
    });
  }

  private demarrerSondage(id: number): void {
    if (this.sondage) {
      return;
    }
    this.essais = 0;
    this.sondage = setInterval(() => {
      if (++this.essais > 40) {
        this.arreterSondage();
        return;
      }
      this.chargerVigie(id);
    }, 3000);
  }

  private arreterSondage(): void {
    if (this.sondage) {
      clearInterval(this.sondage);
      this.sondage = undefined;
    }
  }

  /** Ancre #r-123 : défile jusqu'à la réponse et la surligne un instant. */
  private surligner(): void {
    const ancre = location.hash.match(/^#r-(\d+)$/);
    if (ancre) {
      this.cible.set(Number(ancre[1]));
      setTimeout(() => document.getElementById(`r-${ancre[1]}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 100);
      setTimeout(() => this.cible.set(null), 3000);
    }
  }

  // ------------------------------------------------------------ Actions sur la question

  protected voter(cibleType: 'DISCUSSION' | 'REPONSE', cibleId: number, actuel: number, valeur: 1 | -1): void {
    const nouveau = actuel === valeur ? 0 : valeur;
    this.appliquerVote(cibleType, cibleId, nouveau, nouveau - actuel);
    this.forum.voter({ cibleType, cibleId, valeur: nouveau }).subscribe({
      next: (v) => this.appliquerVote(cibleType, cibleId, v.monVote, 0, v.score),
      error: (e) => {
        this.appliquerVote(cibleType, cibleId, actuel, actuel - nouveau);
        this.toast(messageErreur(e));
      },
    });
  }

  /** Mise à jour optimiste du score et de mon vote. */
  private appliquerVote(cibleType: string, cibleId: number, monVote: number, delta: number, score?: number): void {
    this.d.update((d) => {
      if (!d) {
        return d;
      }
      if (cibleType === 'DISCUSSION') {
        return { ...d, monVote, score: score ?? d.score + delta };
      }
      return { ...d, reponses: d.reponses.map((r) => (r.id === cibleId ? { ...r, monVote, score: score ?? r.score + delta } : r)) };
    });
  }

  protected suivre(): void {
    const d = this.d()!;
    this.forum.suivre(d.id, !d.suivie).subscribe({
      next: (e) => this.d.set({ ...d, suivie: e.suivie, nbAbonnes: e.nbAbonnes }),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected favori(): void {
    const d = this.d()!;
    this.forum.favori(d.id, !d.favori).subscribe({
      next: (e) => this.d.set({ ...d, favori: e.favori }),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected partager(reponseId?: number): void {
    const lien = `${location.origin}${this.base}/${this.d()!.id}${reponseId ? `#r-${reponseId}` : ''}`;
    navigator.clipboard?.writeText(lien).then(() => this.toast('Lien copié.'));
  }

  protected signaler(cibleType: 'DISCUSSION' | 'REPONSE' | 'COMMENTAIRE', cibleId: number, motif: MotifSignalement): void {
    this.forum.signaler({ cibleType, cibleId, motif }).subscribe({
      next: () => this.toast('Merci, la modération va examiner ce message.'),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected modifier(): void {
    const d = this.d()!;
    this.edition.set({ titre: d.titre, contenu: d.contenu });
  }

  protected enregistrer(): void {
    const e = this.edition();
    const d = this.d();
    if (!e || !d) {
      return;
    }
    this.forum.modifier(d.id, { titre: e.titre.trim(), contenu: e.contenu.trim(), etiquettes: d.etiquettes }).subscribe({
      next: (x) => {
        this.d.set(x);
        this.edition.set(null);
        this.toast('Discussion modifiée.');
      },
      error: (err) => this.toast(messageErreur(err)),
    });
  }

  protected supprimer(): void {
    if (!confirm('Supprimer cette discussion ? Elle ne sera plus visible.')) {
      return;
    }
    this.forum.supprimer(this.d()!.id).subscribe({
      next: () => this.router.navigate([this.base]),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected voirVersions(): void {
    if (this.versions()) {
      this.versions.set(null);
      return;
    }
    this.forum.versions(this.d()!.id).subscribe({ next: (v) => this.versions.set(v), error: (e) => this.toast(messageErreur(e)) });
  }

  protected outil(outil: OutilFil, actif = true): void {
    const motif = outil === 'masquer' ? (prompt('Motif du masquage (obligatoire pour le journal) :') ?? '').trim() : undefined;
    if (outil === 'masquer' && !motif) {
      return;
    }
    this.moderation.outilFil(this.d()!.id, outil, actif, motif).subscribe({
      next: (x) => {
        this.d.set(x);
        this.toast('Décision enregistrée dans le journal de modération.');
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ Réponses

  protected repondre(): void {
    const d = this.d();
    if (!d || !this.reponse().trim() || this.envoi()) {
      return;
    }
    this.envoi.set(true);
    this.forum.repondre(d.id, { contenu: this.reponse().trim(), anonyme: this.anonyme() }).subscribe({
      next: (r) => {
        this.reponse.set('');
        this.envoi.set(false);
        this.recharger();
        this.toast('Réponse publiée.');
        setTimeout(() => document.getElementById(`r-${r.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 300);
      },
      error: (e) => {
        this.envoi.set(false);
        this.toast(messageErreur(e));
      },
    });
  }

  protected citer(texte: string): void {
    const selection = window.getSelection()?.toString().trim();
    const extrait = (selection || texte).split('\n').slice(0, 6).map((l) => `> ${l}`).join('\n');
    this.reponse.set(`${this.reponse() ? `${this.reponse()}\n\n` : ''}${extrait}\n\n`);
    document.getElementById('repondre')?.scrollIntoView({ behavior: 'smooth' });
  }

  protected accepter(r: Reponse): void {
    const d = this.d()!;
    const appel: Observable<DiscussionDetail> = r.acceptee ? this.forum.retirerAcceptation(d.id) : this.forum.accepter(d.id, r.id);
    appel.subscribe({ next: (x) => this.d.set(x), error: (e) => this.toast(messageErreur(e)) });
  }

  protected approuver(r: Reponse): void {
    this.forum.approuver(r.id, !r.approuvee).subscribe({ next: () => this.recharger(), error: (e) => this.toast(messageErreur(e)) });
  }

  protected masquerReponse(r: Reponse): void {
    const motif = r.masquee ? undefined : (prompt('Motif du masquage :') ?? '').trim();
    if (!r.masquee && !motif) {
      return;
    }
    this.moderation.masquerMessage('reponses', r.id, !r.masquee, motif).subscribe({
      next: () => this.recharger(),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected reagir(r: Reponse, type: TypeReaction): void {
    const oui = !r.mesReactions.includes(type);
    this.forum.reagir(r.id, type, oui).subscribe({
      next: (x) =>
        this.d.update((d) => (d ? { ...d, reponses: d.reponses.map((y) => (y.id === r.id ? { ...y, reactions: x.compteurs, mesReactions: x.miennes } : y)) } : d)),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected enregistrerReponse(): void {
    const e = this.editionReponse();
    if (!e) {
      return;
    }
    this.forum.modifierReponse(e.id, e.contenu.trim()).subscribe({
      next: () => {
        this.editionReponse.set(null);
        this.recharger();
      },
      error: (err) => this.toast(messageErreur(err)),
    });
  }

  protected supprimerReponse(r: Reponse): void {
    if (confirm('Supprimer cette réponse ?')) {
      this.forum.supprimerReponse(r.id).subscribe({ next: () => this.recharger(), error: (e) => this.toast(messageErreur(e)) });
    }
  }

  // ------------------------------------------------------------ Commentaires

  protected cleCommentaires(reponseId?: number): string {
    return reponseId ? `r${reponseId}` : 'q';
  }

  protected basculerCommentaires(cle: string): void {
    const s = new Set(this.commentairesOuverts());
    if (s.has(cle)) {
      s.delete(cle);
    } else {
      s.add(cle);
    }
    this.commentairesOuverts.set(s);
  }

  protected saisirCommentaire(cle: string, texte: string): void {
    this.commentaire.set({ ...this.commentaire(), [cle]: texte });
  }

  protected commenter(reponseId?: number): void {
    const cle = this.cleCommentaires(reponseId);
    const texte = (this.commentaire()[cle] ?? '').trim();
    if (!texte) {
      return;
    }
    this.forum.commenter({ discussionId: this.d()!.id, reponseId }, { contenu: texte }).subscribe({
      next: () => {
        this.saisirCommentaire(cle, '');
        this.recharger();
      },
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  protected commentairesDe(reponseId?: number): Commentaire[] {
    const d = this.d();
    if (!d) {
      return [];
    }
    return reponseId ? (d.reponses.find((r) => r.id === reponseId)?.commentaires ?? []) : d.commentaires;
  }

  protected masquerCommentaire(c: Commentaire): void {
    this.moderation.masquerMessage('commentaires', c.id, !c.masque, c.masque ? undefined : 'Masqué par la modération').subscribe({
      next: () => this.recharger(),
      error: (e) => this.toast(messageErreur(e)),
    });
  }

  // ------------------------------------------------------------ La Vigie

  private majVigie(r: VigieResponse): void {
    this.vigie.update((v) => {
      if (!v) {
        return v;
      }
      return r.niveau === 'GENERAL' ? { ...v, generale: r, generaleDisponible: false } : { ...v, principale: r };
    });
  }

  private vigieAction(appel: Observable<VigieResponse>, message?: string): void {
    this.vigieOccupee.set(true);
    appel.subscribe({
      next: (r) => {
        this.majVigie(r);
        this.vigieOccupee.set(false);
        if (message) {
          this.toast(message);
        }
      },
      error: (e) => {
        this.vigieOccupee.set(false);
        this.toast(messageErreur(e));
      },
    });
  }

  protected avis(r: VigieResponse, utile: boolean): void {
    this.vigieAction(this.vigieApi.avis(r.id, utile));
  }

  protected validerVigie(r: VigieResponse): void {
    this.vigieAction(this.vigieApi.valider(r.id), 'Réponse validée : elle rejoint la base de connaissances du forum.');
  }

  protected corrigerVigie(r: VigieResponse, texte: string): void {
    this.vigieAction(this.vigieApi.corriger(r.id, texte), 'Correction publiée. L’original est conservé.');
  }

  protected rejeterVigie(r: VigieResponse, motif: string): void {
    this.vigieAction(this.vigieApi.rejeter(r.id, motif), 'Réponse rejetée : les apprenants ne la voient plus.');
  }

  protected regenererVigie(): void {
    const d = this.d()!;
    this.vigieOccupee.set(true);
    this.vigieApi.regenerer(d.id).subscribe({
      next: (v) => {
        this.vigie.set(v);
        this.vigieOccupee.set(false);
        this.demarrerSondage(d.id);
      },
      error: (e) => {
        this.vigieOccupee.set(false);
        this.toast(messageErreur(e));
      },
    });
  }

  protected demanderGenerale(): void {
    this.vigieAction(this.vigieApi.generale(this.d()!.id));
  }

  // ------------------------------------------------------------ Aides

  protected lienCours(d: DiscussionDetail): { chemin: unknown[]; params: Record<string, number | undefined> } {
    return { chemin: ['/app/cours', d.coursId, 'lecture'], params: { chapitre: d.chapitreId, page: d.page } };
  }

  protected compte(r: Reponse, type: TypeReaction): number {
    return r.reactions[type] ?? 0;
  }

  private toast(message: string): void {
    this.snack.open(message, 'OK', { duration: 4000 });
  }
}
