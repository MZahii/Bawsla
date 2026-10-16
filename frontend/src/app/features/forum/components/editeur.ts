import { Component, ElementRef, input, model, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ForumMarkdown } from './markdown';

interface Outil {
  icone: string;
  libelle: string;
  avant: string;
  apres: string;
  exemple: string;
  ligne?: boolean;
}

const OUTILS: Outil[] = [
  { icone: 'format_bold', libelle: 'Gras', avant: '**', apres: '**', exemple: 'texte en gras' },
  { icone: 'format_italic', libelle: 'Italique', avant: '*', apres: '*', exemple: 'texte en italique' },
  { icone: 'link', libelle: 'Lien', avant: '[', apres: '](https://)', exemple: 'texte du lien' },
  { icone: 'format_list_bulleted', libelle: 'Liste', avant: '- ', apres: '', exemple: 'élément', ligne: true },
  { icone: 'format_quote', libelle: 'Citation', avant: '> ', apres: '', exemple: 'citation', ligne: true },
  { icone: 'code', libelle: 'Code en ligne', avant: '`', apres: '`', exemple: 'code' },
  { icone: 'data_object', libelle: 'Bloc de code', avant: '```sql\n', apres: '\n```', exemple: 'SELECT * FROM table;', ligne: true },
];

/**
 * Éditeur Markdown du forum : barre d'outils (insère la syntaxe autour de la sélection), onglet Aperçu, compteur
 * de caractères, et bouton « Utiliser le modèle de question » si un modèle est fourni.
 */
@Component({
  selector: 'app-forum-editeur',
  imports: [FormsModule, MatIconModule, MatTooltipModule, ForumMarkdown],
  template: `
    <div class="editeur" [class.focus]="focus()">
      <div class="barre" role="toolbar" [attr.aria-label]="'Mise en forme de : ' + libelle()">
        <div class="onglets" role="tablist">
          <button type="button" role="tab" [attr.aria-selected]="!apercu()" [class.on]="!apercu()" (click)="apercu.set(false)">Écrire</button>
          <button type="button" role="tab" [attr.aria-selected]="apercu()" [class.on]="apercu()" (click)="apercu.set(true)">Aperçu</button>
        </div>
        @if (!apercu()) {
          <span class="outils">
            @for (o of outils; track o.icone) {
              <button type="button" class="icon-btn" [matTooltip]="o.libelle" [attr.aria-label]="o.libelle" (click)="inserer(o)">
                <mat-icon>{{ o.icone }}</mat-icon>
              </button>
            }
          </span>
        }
        @if (modele()) {
          <button type="button" class="modele" (click)="utiliserModele()"><mat-icon aria-hidden="true">assignment</mat-icon> Utiliser le modèle de question</button>
        }
      </div>
      @if (apercu()) {
        <div class="apercu">
          @if (valeur().trim()) { <app-forum-markdown [texte]="valeur()" /> } @else { <p class="muted">Rien à prévisualiser pour l’instant.</p> }
        </div>
      } @else {
        <textarea #zone dir="auto" [rows]="lignes()" [attr.aria-label]="libelle()" [placeholder]="placeholder()" [ngModel]="valeur()"
                  (ngModelChange)="valeur.set($event)" (focus)="focus.set(true)" (blur)="focus.set(false)" [maxlength]="max()"></textarea>
      }
      <div class="pied small muted">
        <span>Markdown : **gras**, *italique*, \`code\`, blocs \`\`\`sql</span>
        <span class="num" [class.limite]="valeur().length > max() * 0.9">{{ valeur().length }} / {{ max() }}</span>
      </div>
    </div>
  `,
  styles: `
    .editeur { border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .editeur.focus { border-color: var(--color-navy-700); outline: 1px solid var(--color-navy-700); }
    .barre { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2); padding: 4px var(--space-2); border-bottom: 1px solid var(--color-border); }
    .onglets { display: flex; }
    .onglets button { min-height: 36px; padding: 0 var(--space-3); border: 0; border-bottom: 2px solid transparent; background: none; color: var(--color-text-muted); font: 600 14px var(--font-body); cursor: pointer; }
    .onglets button.on { color: var(--color-text); border-bottom-color: var(--color-bordeaux-600); }
    .outils { display: flex; flex-wrap: wrap; }
    .modele { display: inline-flex; align-items: center; gap: 4px; margin-left: auto; min-height: 36px; border: 0; background: none; color: var(--color-bordeaux-600); font: 600 13px var(--font-body); cursor: pointer; }
    .modele mat-icon { font-size: 18px; width: 18px; height: 18px; }
    textarea { display: block; width: 100%; box-sizing: border-box; padding: var(--space-3); border: 0; outline: 0; resize: vertical; background: none; color: var(--color-text); font: 400 15px/22px var(--font-body); }
    .apercu { min-height: 120px; padding: var(--space-3); }
    .pied { display: flex; justify-content: space-between; gap: var(--space-3); padding: 4px var(--space-3); border-top: 1px solid var(--color-border); }
    .limite { color: var(--color-warning); font-weight: 600; }
  `,
})
export class ForumEditeur {
  readonly valeur = model('');
  readonly libelle = input('Contenu');
  readonly placeholder = input('');
  readonly max = input(20000);
  readonly lignes = input(8);
  readonly modele = input<string | undefined | null>(null);

  protected readonly outils = OUTILS;
  protected readonly apercu = signal(false);
  protected readonly focus = signal(false);
  private readonly zone = viewChild<ElementRef<HTMLTextAreaElement>>('zone');

  protected inserer(o: Outil): void {
    const zone = this.zone()?.nativeElement;
    const texte = this.valeur();
    const debut = zone?.selectionStart ?? texte.length;
    const fin = zone?.selectionEnd ?? texte.length;
    const choisi = texte.slice(debut, fin) || o.exemple;
    const avant = o.ligne && debut > 0 && texte[debut - 1] !== '\n' ? `\n${o.avant}` : o.avant;
    const nouveau = texte.slice(0, debut) + avant + choisi + o.apres + texte.slice(fin);
    this.valeur.set(nouveau.slice(0, this.max()));
    queueMicrotask(() => {
      zone?.focus();
      zone?.setSelectionRange(debut + avant.length, debut + avant.length + choisi.length);
    });
  }

  protected utiliserModele(): void {
    const modele = this.modele();
    if (modele) {
      this.valeur.set(this.valeur().trim() ? `${this.valeur().trim()}\n\n${modele}` : modele);
    }
  }
}
