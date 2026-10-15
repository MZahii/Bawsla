import { Component, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router } from '@angular/router';

import { AiLoading, BwButton, EmptyState } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { TempsRelatifPipe, messageErreur } from '../../components/outils';
import { VigieBox } from '../../components/vigie-box';
import { ConversationVigie, CoursOption, ModeVigie } from '../../models';
import { ForumService } from '../../services/forum.service';
import { VigieService } from '../../services/vigie.service';

/**
 * Assistant privé « Demander au cours » (plan §4.3, §7.4) : conversation avec La Vigie sur un cours, sans rien
 * publier. Modes Réponse directe, Guide-moi (indices progressifs) et réponse générale hors cours (si permise) ;
 * les questions de suivi sont rendues autonomes par le serveur. « Publier comme question » prépare un brouillon.
 */
@Component({
  selector: 'app-forum-assistant',
  imports: [FormsModule, MatIconModule, MatSelectModule, MatButtonToggleModule, AiLoading, BwButton, EmptyState, PageHeader, VigieBox, TempsRelatifPipe],
  templateUrl: './forum-assistant.html',
  styleUrl: './forum-assistant.scss',
})
export class ForumAssistant implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly vigie = inject(VigieService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fin = viewChild<ElementRef<HTMLElement>>('fin');

  protected readonly cours = signal<CoursOption[]>([]);
  protected readonly coursId = signal<number | null>(null);
  protected readonly conversations = signal<ConversationVigie[]>([]);
  protected readonly active = signal<ConversationVigie | null>(null);
  protected readonly texte = signal('');
  protected readonly mode = signal<ModeVigie>('DIRECTE');
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);

  ngOnInit(): void {
    const c = this.route.snapshot.queryParamMap.get('coursId');
    this.coursId.set(c ? Number(c) : null);
    this.forum.cours().subscribe({ next: (x) => this.cours.set(x), error: () => this.cours.set([]) });
    this.chargerConversations();
  }

  private chargerConversations(): void {
    this.vigie.conversations().subscribe({ next: (c) => this.conversations.set(c), error: () => this.conversations.set([]) });
  }

  protected ouvrir(id: number): void {
    this.erreur.set(null);
    this.vigie.conversation(id).subscribe({
      next: (c) => {
        this.active.set(c);
        this.coursId.set(c.coursId);
        this.defiler();
      },
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  protected nouvelle(): void {
    this.active.set(null);
    this.texte.set('');
    this.erreur.set(null);
  }

  /** Crée la conversation au premier message, puis envoie la question. */
  protected envoyer(): void {
    const texte = this.texte().trim();
    if (!texte || this.envoi()) {
      return;
    }
    if (!this.coursId()) {
      this.erreur.set('Choisis d’abord le cours sur lequel tu poses ta question.');
      return;
    }
    this.envoi.set(true);
    this.erreur.set(null);
    const active = this.active();
    if (active) {
      this.poser(active.id, texte);
      return;
    }
    this.vigie.creerConversation(this.coursId()!).subscribe({
      next: (c) => {
        this.active.set(c);
        this.poser(c.id, texte);
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(messageErreur(e));
      },
    });
  }

  /** Entrée envoie, Maj + Entrée va à la ligne. */
  protected entree(e: KeyboardEvent): void {
    if (!e.shiftKey) {
      e.preventDefault();
      this.envoyer();
    }
  }

  private poser(id: number, texte: string): void {
    const actuelle = this.active()!;
    const question = { id: -Date.now(), role: 'APPRENANT' as const, texte, creeLe: new Date().toISOString() };
    this.active.set({ ...actuelle, messages: [...actuelle.messages, question] });
    this.texte.set('');
    this.defiler();
    this.vigie.envoyer(id, { texte, mode: this.mode() }).subscribe({
      next: (m) => {
        const c = this.active()!;
        this.active.set({ ...c, messages: [...c.messages, m] });
        this.envoi.set(false);
        this.chargerConversations();
        this.defiler();
      },
      error: (e) => {
        this.envoi.set(false);
        this.erreur.set(messageErreur(e));
        this.texte.set(texte);
      },
    });
  }

  protected publier(): void {
    const c = this.active();
    if (!c) {
      return;
    }
    this.vigie.brouillon(c.id).subscribe({
      next: (b) =>
        this.router.navigate(['/app/forum/nouvelle'], {
          queryParams: { coursId: b.coursId, titre: b.titre, conversationId: b.conversationId },
          state: { contenu: b.contenu },
        }),
      error: (e) => this.erreur.set(messageErreur(e)),
    });
  }

  private defiler(): void {
    setTimeout(() => this.fin()?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'end' }), 50);
  }
}
