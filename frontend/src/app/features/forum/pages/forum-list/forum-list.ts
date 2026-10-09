import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';

import { COURS, DISCUSSIONS, coursParId } from '../../../../core/demo/demo-data';
import { TAGS_FORUM } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, PageHeader } from '../../../../shared/ui';

type Onglet = 'recentes' | 'sans-reponse' | 'resolues' | 'miennes';

/** Étiquettes de démonstration par discussion. */
const TAGS: Record<number, string[]> = { 1: ['sql', 'jointures'], 2: ['spring', 'eureka'], 3: ['angular', 'signals'], 4: ['docker'], 5: ['git'] };

/**
 * Forum (questions-réponses) : onglets, recherche, filtre par cours, compteurs de votes et de réponses,
 * étiquettes et contributeurs.
 * À brancher : ForumService.findAll() ; la recherche sémantique viendra de /api/ai/forum.
 */
@Component({
  selector: 'app-forum-list',
  imports: [FormsModule, RouterLink, MatIconModule, MatSelectModule, MatTabsModule, BwButton, EmptyState, Avatar, PageHeader],
  templateUrl: './forum-list.html',
  styleUrl: './forum-list.scss',
})
export class ForumList {
  protected readonly onglets: { id: Onglet; label: string }[] = [
    { id: 'recentes', label: 'Récentes' },
    { id: 'sans-reponse', label: 'Sans réponse' },
    { id: 'resolues', label: 'Résolues' },
    { id: 'miennes', label: 'Mes questions' },
  ];
  protected readonly cours = COURS.filter((c) => DISCUSSIONS.some((d) => d.coursId === c.id));
  protected readonly tags = TAGS_FORUM;
  protected readonly onglet = signal<Onglet>('recentes');
  protected readonly recherche = signal('');
  protected readonly coursId = signal<number | null>(null);
  protected readonly tag = signal<string | null>(null);

  protected readonly contributeurs = [
    { nom: 'Imen Ayari', role: 'Formatrice', points: 412 },
    { nom: 'Oumaima Gafsi', role: 'Étudiante', points: 236 },
    { nom: 'Aziz Boulifa', role: 'Étudiant', points: 198 },
    { nom: 'Malek Ouji', role: 'Étudiant', points: 154 },
  ];

  protected readonly discussions = computed(() => {
    const q = this.recherche().trim().toLowerCase();
    const o = this.onglet();
    return DISCUSSIONS.map((d) => ({ ...d, cours: coursParId(d.coursId)?.titre ?? '', tags: TAGS[d.id] ?? [], votes: [14, 3, 9, 1, 22][d.id - 1] ?? 0 }))
      .filter((d) => this.coursId() === null || d.coursId === this.coursId())
      .filter((d) => !this.tag() || d.tags.includes(this.tag()!))
      .filter((d) => !q || `${d.titre} ${d.contenu}`.toLowerCase().includes(q))
      .filter((d) => (o === 'sans-reponse' ? d.reponses === 0 : o === 'resolues' ? d.resolue : o === 'miennes' ? d.id === 1 || d.id === 4 : true));
  });

  protected choisirOnglet(i: number): void {
    this.onglet.set(this.onglets[i].id);
  }

  protected reinitialiser(): void {
    this.recherche.set('');
    this.coursId.set(null);
    this.tag.set(null);
  }
}
