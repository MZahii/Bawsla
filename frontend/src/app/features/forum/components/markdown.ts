import { Component, computed, input, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/** Bloc de Markdown déjà découpé ; html = texte ÉCHAPPÉ puis mis en forme (gras, italique, code, liens). */
export type BlocMarkdown =
  | { type: 'p' | 'quote' | 'h'; html: string }
  | { type: 'ul' | 'ol'; items: string[] }
  | { type: 'code'; langue: string; code: string };

const ECHAPPER: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function echapper(texte: string): string {
  return texte.replace(/[&<>"']/g, (c) => ECHAPPER[c]);
}

/**
 * Mise en forme d'une ligne : le texte est échappé AVANT toute transformation, donc aucune balise de l'utilisateur
 * ne passe. Liens : http(s) uniquement, ouverts dans un nouvel onglet sans transmettre la page d'origine.
 */
export function enLigne(texte: string): string {
  const codes: string[] = [];
  let html = echapper(texte).replace(/`([^`]+)`/g, (_, c: string) => `\u0000${codes.push(c) - 1}\u0000`);
  html = html
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
    .replace(/(^|\W)_([^_\s][^_]*)_(?=\W|$)/g, '$1<em>$2</em>');
  return html.replace(/\u0000(\d+)\u0000/g, (_, i: string) => `<code>${codes[Number(i)]}</code>`);
}

/** Découpe un texte Markdown en blocs (paragraphes, titres, listes, citations, blocs de code). */
export function analyser(source: string): BlocMarkdown[] {
  const lignes = (source ?? '').replace(/\r\n/g, '\n').split('\n');
  const blocs: BlocMarkdown[] = [];
  let i = 0;
  while (i < lignes.length) {
    const ligne = lignes[i];
    const cloture = ligne.match(/^```\s*([\w+-]*)\s*$/);
    if (cloture) {
      const code: string[] = [];
      i++;
      while (i < lignes.length && !/^```\s*$/.test(lignes[i])) {
        code.push(lignes[i++]);
      }
      i++;
      blocs.push({ type: 'code', langue: cloture[1] || '', code: code.join('\n') });
      continue;
    }
    if (!ligne.trim()) {
      i++;
      continue;
    }
    if (/^#{1,4}\s+/.test(ligne)) {
      blocs.push({ type: 'h', html: enLigne(ligne.replace(/^#{1,4}\s+/, '')) });
      i++;
      continue;
    }
    const liste = (re: RegExp, type: 'ul' | 'ol') => {
      const items: string[] = [];
      while (i < lignes.length && re.test(lignes[i])) {
        items.push(enLigne(lignes[i++].replace(re, '')));
      }
      blocs.push({ type, items });
    };
    if (/^\s*[-*]\s+/.test(ligne)) {
      liste(/^\s*[-*]\s+/, 'ul');
      continue;
    }
    if (/^\s*\d+[.)]\s+/.test(ligne)) {
      liste(/^\s*\d+[.)]\s+/, 'ol');
      continue;
    }
    if (/^>\s?/.test(ligne)) {
      const cite: string[] = [];
      while (i < lignes.length && /^>\s?/.test(lignes[i])) {
        cite.push(lignes[i++].replace(/^>\s?/, ''));
      }
      blocs.push({ type: 'quote', html: cite.map(enLigne).join('<br>') });
      continue;
    }
    const paragraphe: string[] = [];
    while (i < lignes.length && lignes[i].trim() && !/^(```|#{1,4}\s|>\s?|\s*[-*]\s+|\s*\d+[.)]\s+)/.test(lignes[i])) {
      paragraphe.push(lignes[i++]);
    }
    blocs.push({ type: 'p', html: paragraphe.map(enLigne).join('<br>') });
  }
  return blocs;
}

/**
 * Affichage sûr du Markdown du forum (sans bibliothèque) : texte échappé puis sous-ensemble Markdown (gras,
 * italique, liens http(s), listes, citations, code en ligne, blocs de code avec bouton « Copier »). Angular nettoie
 * en plus tout innerHTML. Texte arabe : dir="auto" sur chaque bloc.
 */
@Component({
  selector: 'app-forum-markdown',
  imports: [MatIconModule],
  template: `
    @for (b of blocs(); track $index) {
      @switch (b.type) {
        @case ('code') {
          <div class="code">
            <div class="code-head">
              <span>{{ $any(b).langue || 'code' }}</span>
              <button type="button" class="copier" (click)="copier($any(b).code, $index)" [attr.aria-label]="'Copier le code'">
                <mat-icon aria-hidden="true">{{ copie() === $index ? 'check' : 'content_copy' }}</mat-icon>
                {{ copie() === $index ? 'Copié' : 'Copier' }}
              </button>
            </div>
            <pre dir="ltr"><code>{{ $any(b).code }}</code></pre>
          </div>
        }
        @case ('ul') {
          <ul dir="auto">@for (it of $any(b).items; track $index) { <li [innerHTML]="it"></li> }</ul>
        }
        @case ('ol') {
          <ol dir="auto">@for (it of $any(b).items; track $index) { <li [innerHTML]="it"></li> }</ol>
        }
        @case ('quote') { <blockquote dir="auto" [innerHTML]="$any(b).html"></blockquote> }
        @case ('h') { <p class="h" dir="auto" [innerHTML]="$any(b).html"></p> }
        @default { <p dir="auto" [innerHTML]="$any(b).html"></p> }
      }
    }
  `,
  styles: `
    :host { display: block; overflow-wrap: anywhere; }
    p, ul, ol, blockquote { margin: 0 0 var(--space-3); }
    :host > :last-child { margin-bottom: 0; }
    .h { font: 600 17px/24px var(--font-heading); }
    blockquote { padding: var(--space-2) var(--space-4); border-left: 3px solid var(--color-border); color: var(--color-text-muted); }
    :host ::ng-deep code { font: 14px var(--font-code); padding: 1px 5px; border-radius: 4px; background: var(--color-bg); }
    :host ::ng-deep a { color: var(--color-bordeaux-600); }
    .code { margin: 0 0 var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); overflow: hidden; }
    .code-head { display: flex; align-items: center; justify-content: space-between; padding: 4px var(--space-3); background: var(--color-bg); border-bottom: 1px solid var(--color-border); font: 600 12px var(--font-body); color: var(--color-text-muted); }
    .copier { display: inline-flex; align-items: center; gap: 4px; min-height: 32px; border: 0; background: none; color: var(--color-text-muted); font: 600 12px var(--font-body); cursor: pointer; }
    .copier mat-icon { font-size: 16px; width: 16px; height: 16px; }
    pre { margin: 0; padding: var(--space-3); overflow-x: auto; background: var(--color-surface); }
    pre code { padding: 0; background: none; font-size: 13px; line-height: 20px; }
  `,
})
export class ForumMarkdown {
  readonly texte = input<string | undefined | null>('');
  protected readonly blocs = computed(() => analyser(this.texte() ?? ''));
  protected readonly copie = signal<number | null>(null);

  protected copier(code: string, index: number): void {
    navigator.clipboard?.writeText(code).then(() => {
      this.copie.set(index);
      setTimeout(() => this.copie.set(null), 2000);
    });
  }
}
