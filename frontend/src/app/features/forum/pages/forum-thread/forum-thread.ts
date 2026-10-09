import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router, RouterLink } from '@angular/router';

import { DISCUSSIONS, REPONSES, REPONSE_IA, coursParId } from '../../../../core/demo/demo-data';
import { ROLE_LABELS } from '../../../../shared/models/user.model';
import { AiBox, Badge, BwButton, EmptyState } from '../../../../shared';

/**
 * Fil de discussion : question, réponse IA sourcée (RAG) avec confiance, réponses humaines.
 * À brancher : ForumService (discussion + réponses) et /api/ai/forum pour la réponse IA.
 */
@Component({
  selector: 'app-forum-thread',
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatIconModule, AiBox, Badge, BwButton, EmptyState],
  templateUrl: './forum-thread.html',
  styleUrl: './forum-thread.scss',
})
export class ForumThread {
  /** Le fil s'ouvre depuis l'espace étudiant ou depuis le studio (modération). */
  protected readonly studio = inject(Router).url.startsWith('/studio');
  protected readonly base = this.studio ? '/studio/forum' : '/app/forum';

  readonly id = input(0, { transform: numberAttribute });

  protected readonly discussion = computed(() => DISCUSSIONS.find((d) => d.id === this.id()));
  protected readonly cours = computed(() => coursParId(this.discussion()?.coursId ?? 0));
  /** Dans la démo, seule la discussion 1 a une réponse IA et des réponses détaillées. */
  protected readonly ia = computed(() => (REPONSE_IA.discussionId === this.id() ? REPONSE_IA : null));
  protected readonly reponses = computed(() => (this.id() === 1 ? REPONSES : []));
  protected readonly roleLabels = ROLE_LABELS;

  protected readonly brouillon = signal('');
  protected readonly votes = signal<Record<number, number>>({});

  protected voter(i: number): void {
    this.votes.update((v) => ({ ...v, [i]: v[i] ? 0 : 1 }));
  }

  protected initiales(nom: string): string {
    return nom
      .split(' ')
      .map((p) => p.charAt(0))
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }
}
