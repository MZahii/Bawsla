import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';

import { AiBox, AiLoading, BwButton } from '../../../shared';
import { SourceVigie, VigieResponse } from '../models';

type Segment = { texte: string } | { numero: number };

/** Découpe « phrase [1]. autre [2]. » en texte et numéros de source cliquables. */
export function segments(texte: string | undefined | null): Segment[] {
  const resultat: Segment[] = [];
  const re = /\[(\d+)\]/g;
  let dernier = 0;
  let m: RegExpExecArray | null;
  const t = texte ?? '';
  while ((m = re.exec(t)) !== null) {
    if (m.index > dernier) {
      resultat.push({ texte: t.slice(dernier, m.index) });
    }
    resultat.push({ numero: Number(m[1]) });
    dernier = m.index + m[0].length;
  }
  if (dernier < t.length) {
    resultat.push({ texte: t.slice(dernier) });
  }
  return resultat;
}

/** Passage découpé autour de la citation exacte (surlignée), si on la retrouve. */
export function surligner(passage: string, citation?: string): { avant: string; cite: string; apres: string } | null {
  if (!citation) {
    return null;
  }
  const i = passage.toLowerCase().indexOf(citation.trim().toLowerCase());
  if (i < 0) {
    return null;
  }
  const n = citation.trim().length;
  return { avant: passage.slice(0, i), cite: passage.slice(i, i + n), apres: passage.slice(i + n) };
}

/**
 * Encadré de La Vigie dans un fil ou dans l'assistant (DESIGN.md §7) : toujours signalé comme IA, sourcé, et
 * validable par le formateur. Les trois niveaux restent distincts : cours / forum (sources [n], confiance calculée)
 * et réponse générale hors cours (encadré séparé, bandeau « non vérifiée », jamais de confiance).
 */
@Component({
  selector: 'app-vigie-box',
  imports: [FormsModule, NgTemplateOutlet, RouterLink, MatIconModule, MatTooltipModule, AiBox, AiLoading, BwButton],
  templateUrl: './vigie-box.html',
  styleUrl: './vigie-box.scss',
})
export class VigieBox {
  readonly reponse = input.required<VigieResponse>();
  /** Formateur du cours ou admin : actions Valider, Corriger, Rejeter, Régénérer. */
  readonly moderateur = input(false);
  readonly generaleDisponible = input(false);
  readonly occupe = input(false);
  /** Dans l'assistant privé : pas d'avis ni de décision (rien n'est publié). */
  readonly prive = input(false);

  readonly avis = output<boolean>();
  readonly valider = output<void>();
  readonly corriger = output<string>();
  readonly rejeter = output<string>();
  readonly regenerer = output<void>();
  readonly demanderGenerale = output<void>();

  protected readonly ouverte = signal<number | null>(null);
  protected readonly indicesVus = signal(1);
  protected readonly complete = signal(false);
  protected readonly edition = signal<'corriger' | 'rejeter' | null>(null);
  protected readonly brouillon = signal('');

  protected readonly r = computed(() => this.reponse());
  protected readonly generale = computed(() => this.r().niveau === 'GENERAL');
  protected readonly sourcee = computed(
    () => !this.generale() && ['GENEREE', 'VALIDEE', 'CORRIGEE', 'REJETEE'].includes(this.r().statut),
  );
  protected readonly morceaux = computed(() => segments(this.r().texte));
  protected readonly guidee = computed(() => this.r().mode === 'GUIDEE' && this.r().indices.length > 0);
  protected readonly montrerTexte = computed(() => !this.guidee() || this.complete());
  protected readonly libelleConfiance = computed(() => {
    switch (this.r().niveauConfiance) {
      case 'ELEVEE':
        return 'Confiance élevée';
      case 'MOYENNE':
        return 'Confiance moyenne';
      default:
        return 'Confiance faible';
    }
  });

  protected source(numero: number): SourceVigie | undefined {
    return this.r().sources.find((s) => s.numero === numero);
  }

  protected basculer(numero: number): void {
    this.ouverte.set(this.ouverte() === numero ? null : numero);
  }

  protected origine(s: SourceVigie): string {
    if (s.origine === 'FORUM') {
      return 'Réponse validée dans un fil du forum';
    }
    return [s.cours ?? 'Cours', s.chapitre, s.page ? `page ${s.page}` : null].filter(Boolean).join(', ');
  }

  protected passage(s: SourceVigie) {
    return surligner(s.passage, s.citation);
  }

  protected ouvrirEdition(mode: 'corriger' | 'rejeter'): void {
    this.brouillon.set(mode === 'corriger' ? (this.r().texteCorrige ?? this.r().texte ?? '') : '');
    this.edition.set(mode);
  }

  protected confirmerEdition(): void {
    const texte = this.brouillon().trim();
    if (this.edition() === 'corriger' && texte) {
      this.corriger.emit(texte);
    } else if (this.edition() === 'rejeter') {
      this.rejeter.emit(texte || 'Réponse incorrecte');
    }
    this.edition.set(null);
  }
}
