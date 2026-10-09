import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

/**
 * Zone de dépôt de fichier (glisser-déposer ou clic), avec la liste des fichiers choisis.
 * <bw-dropzone accept=".pdf" hint="PDF, 20 Mo maximum" (files)="onFiles($event)" />
 */
@Component({
  selector: 'bw-dropzone',
  imports: [MatIconModule],
  template: `
    <label class="zone" [class.over]="over()" (dragover)="$event.preventDefault(); over.set(true)" (dragleave)="over.set(false)" (drop)="drop($event)">
      <mat-icon aria-hidden="true">upload_file</mat-icon>
      <span class="main"><strong>Choisis un fichier</strong> ou dépose-le ici</span>
      <span class="hint">{{ hint() }}</span>
      <input type="file" [accept]="accept()" [multiple]="multiple()" (change)="pick($event)" />
    </label>
    @if (choisis().length) {
      <ul class="list">
        @for (f of choisis(); track f.name) {
          <li>
            <mat-icon aria-hidden="true">description</mat-icon>
            <span class="name">{{ f.name }}</span>
            <span class="size">{{ taille(f.size) }}</span>
            <button type="button" (click)="retirer(f)" [attr.aria-label]="'Retirer ' + f.name"><mat-icon>close</mat-icon></button>
          </li>
        }
      </ul>
    }
  `,
  styles: `
    :host { display: block; }
    .zone { position: relative; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: var(--space-6) var(--space-4); border: 2px dashed var(--color-border); border-radius: var(--radius-md); background: var(--color-bg); text-align: center; cursor: pointer; }
    .zone.over, .zone:hover { border-color: var(--color-primary); }
    .zone:focus-within { outline: 2px solid var(--color-bordeaux-600); outline-offset: 2px; }
    .zone mat-icon { font-size: 36px; width: 36px; height: 36px; color: var(--color-text-muted); }
    .hint { font-size: 13px; color: var(--color-text-muted); }
    input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .list { margin: var(--space-3) 0 0; padding: 0; list-style: none; display: grid; gap: var(--space-2); }
    li { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: var(--color-surface); }
    .name { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .size { font-size: 13px; color: var(--color-text-muted); }
    li button { display: grid; place-items: center; width: 32px; height: 32px; border: 0; border-radius: 50%; background: none; color: var(--color-text-muted); cursor: pointer; }
  `,
})
export class Dropzone {
  readonly accept = input('');
  readonly hint = input('');
  readonly multiple = input(false);
  /** Fichiers d'exemple déjà affichés (aperçu). */
  readonly initial = input<{ name: string; size: number }[]>([]);
  readonly files = output<File[]>();

  protected readonly over = signal(false);
  protected readonly choisis = signal<{ name: string; size: number }[]>([]);

  ngOnInit(): void {
    this.choisis.set(this.initial());
  }

  protected pick(e: Event): void {
    const list = Array.from((e.target as HTMLInputElement).files ?? []);
    this.ajouter(list);
  }
  protected drop(e: DragEvent): void {
    e.preventDefault();
    this.over.set(false);
    this.ajouter(Array.from(e.dataTransfer?.files ?? []));
  }
  protected retirer(f: { name: string }): void {
    this.choisis.update((l) => l.filter((x) => x.name !== f.name));
  }
  protected taille(o: number): string {
    return o > 1e6 ? `${(o / 1e6).toFixed(1)} Mo` : `${Math.round(o / 1e3)} Ko`;
  }
  private ajouter(list: File[]): void {
    if (!list.length) return;
    this.choisis.update((l) => (this.multiple() ? [...l, ...list] : list.slice(0, 1)));
    this.files.emit(list);
  }
}
