import { Component, computed, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { Observable, filter } from 'rxjs';

import { BwButton } from '../../../shared';
import { MotifChamp } from './motif-champ';
import { ContexteMotif, erreurMotif } from './validation';

export interface DonneesDialogue {
  titre: string;
  message?: string;
  confirmer: string;
  /** Bouton rouge (suppression, masquage) ; sinon bouton principal. */
  danger?: boolean;
  /** Demande un motif obligatoire (règle du serveur) ; la boîte renvoie alors le motif saisi. */
  motif?: ContexteMotif;
  libelleMotif?: string;
}

/**
 * Boîte de confirmation du forum, à la place de confirm() et prompt() du navigateur : MatDialog aux couleurs du
 * thème, bouton Annuler fantôme à gauche de l'action, motif obligatoire avec motifs prédéfinis si demandé.
 */
@Component({
  selector: 'app-forum-dialogue',
  imports: [MatDialogModule, BwButton, MotifChamp],
  template: `
    <h2 mat-dialog-title>{{ d.titre }}</h2>
    <mat-dialog-content>
      @if (d.message) { <p class="message">{{ d.message }}</p> }
      @if (d.motif) {
        <app-motif-champ [(valeur)]="motif" [contexte]="d.motif" [libelle]="d.libelleMotif ?? 'Motif'" />
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button type="button" bwButton="fantome" mat-dialog-close>Annuler</button>
      <button type="button" [bwButton]="d.danger ? 'danger' : 'principal'" [disabled]="!valide()" (click)="valider()">{{ d.confirmer }}</button>
    </mat-dialog-actions>
  `,
  styles: `
    .message { margin: 0 0 var(--space-3); color: var(--color-text); }
    mat-dialog-actions { gap: var(--space-2); }
  `,
})
export class ForumDialogue {
  protected readonly d = inject<DonneesDialogue>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<ForumDialogue, string | true>>(MatDialogRef);

  protected readonly motif = signal('');
  protected readonly valide = computed(() => !this.d.motif || erreurMotif(this.motif()) === null);

  protected valider(): void {
    if (this.valide()) {
      this.ref.close(this.d.motif ? this.motif().trim() : true);
    }
  }
}

/** Ouvre la boîte ; n'émet que si l'utilisateur confirme (le motif saisi, ou true sans motif). */
export function demander(dialog: MatDialog, donnees: DonneesDialogue): Observable<string | true> {
  return dialog
    .open<ForumDialogue, DonneesDialogue, string | true>(ForumDialogue, {
      data: donnees,
      width: '520px',
      maxWidth: 'calc(100vw - 32px)',
      autoFocus: donnees.motif ? 'textarea' : 'first-tabbable',
    })
    .afterClosed()
    .pipe(filter((r): r is string | true => r !== undefined && r !== ''));
}
