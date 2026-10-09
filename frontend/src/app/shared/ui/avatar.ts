import { Component, computed, input } from '@angular/core';

const TONS = ['navy', 'bordeaux', 'slate', 'teal'];

/**
 * Avatar à initiales (ou photo). La couleur dépend du nom : la même personne garde toujours la même.
 * <bw-avatar nom="Malek Ouji" [size]="40" />
 */
@Component({
  selector: 'bw-avatar',
  template: `
    @if (src()) {
      <img [src]="src()" [alt]="nom()" [style.width.px]="size()" [style.height.px]="size()" />
    } @else {
      <span [class]="'av ' + ton()" [style.width.px]="size()" [style.height.px]="size()" [style.font-size.px]="size() * 0.38" [attr.aria-label]="nom()" role="img">{{ initiales() }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; flex: none; }
    img, .av { border-radius: 50%; object-fit: cover; }
    .av { display: grid; place-items: center; font-family: var(--font-heading); font-weight: 600; color: var(--color-white); letter-spacing: 0.02em; }
    .navy { background: var(--color-navy-500); }
    .bordeaux { background: var(--color-bordeaux-500); }
    .slate { background: var(--chart-1); }
    .teal { background: var(--chart-4); }
  `,
})
export class Avatar {
  readonly nom = input.required<string>();
  readonly size = input(36);
  readonly src = input<string | undefined>(undefined);

  protected readonly initiales = computed(() =>
    this.nom().split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]!.toUpperCase()).join(''),
  );
  protected readonly ton = computed(() => TONS[[...this.nom()].reduce((s, c) => s + c.charCodeAt(0), 0) % TONS.length]);
}
