import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';

import { COURS, DISCUSSIONS } from '../../../../core/demo/demo-data';
import { BwButton } from '../../../../shared';

const MOTS_VIDES = new Set(['le', 'la', 'les', 'un', 'une', 'des', 'de', 'du', 'et', 'ou', 'pour', 'mon', 'ma', 'mes', 'est', 'qui', 'que']);

/**
 * Poser une question. Pendant la saisie, le panneau « Questions similaires » se met à jour.
 * À brancher : la similarité viendra de la recherche sémantique du module Forum (NLP) ;
 * la démo se contente de comparer les mots du titre.
 */
@Component({
  selector: 'app-forum-ask',
  imports: [FormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule, BwButton],
  templateUrl: './forum-ask.html',
  styleUrl: './forum-ask.scss',
})
export class ForumAsk {
  protected readonly cours = COURS;
  protected readonly titre = signal('Mon LEFT JOIN renvoie les mêmes lignes qu’un INNER JOIN');
  protected readonly contenu = signal('');
  protected readonly coursId = signal<number | null>(1);

  protected readonly similaires = computed(() => {
    const mots = this.titre()
      .toLowerCase()
      .split(/[^a-zà-ÿ0-9]+/)
      .filter((m) => m.length > 2 && !MOTS_VIDES.has(m));
    if (mots.length === 0) return [];
    return DISCUSSIONS.map((d) => ({
      ...d,
      score: mots.filter((m) => `${d.titre} ${d.contenu}`.toLowerCase().includes(m)).length,
    }))
      .filter((d) => d.score > 0)
      .sort((a, b) => b.score - a.score)
      .slice(0, 3);
  });

  protected readonly valide = computed(() => this.titre().trim().length >= 10 && this.contenu().trim().length >= 20);
}
