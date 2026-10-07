import { Directive, ElementRef, Renderer2, booleanAttribute, effect, inject, input } from '@angular/core';

export type ButtonVariant = 'principal' | 'secondaire' | 'fantome' | 'ia' | 'danger';

/**
 * Les 5 variantes de bouton de DESIGN.md (section 6). Styles dans styles/_buttons.scss.
 * <button bwButton="principal">Continuer le cours</button>
 * <button bwButton="ia" [bwLoading]="generation()">Générer le résumé</button>
 * La variante "ia" ajoute automatiquement l'icône boussole.
 * Rappel : un seul bouton "principal" par écran.
 */
@Directive({
  selector: '[bwButton]',
  host: {
    class: 'bw-btn',
    '[class.bw-btn--principal]': "variant() === 'principal' || !variant()",
    '[class.bw-btn--secondaire]': "variant() === 'secondaire'",
    '[class.bw-btn--fantome]': "variant() === 'fantome'",
    '[class.bw-btn--ia]': "variant() === 'ia'",
    '[class.bw-btn--danger]': "variant() === 'danger'",
    '[class.bw-btn--loading]': 'loading()',
    '[class.bw-btn--block]': 'block()',
    '[attr.aria-busy]': 'loading() || null',
  },
})
export class BwButton {
  readonly variant = input<ButtonVariant | ''>('principal', { alias: 'bwButton' });
  readonly loading = input(false, { alias: 'bwLoading', transform: booleanAttribute });
  readonly block = input(false, { alias: 'bwBlock', transform: booleanAttribute });

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const renderer = inject(Renderer2);
    let icon: HTMLElement | null = null;

    effect(() => {
      const isAi = this.variant() === 'ia';
      if (isAi && !icon) {
        icon = renderer.createElement('span') as HTMLElement;
        renderer.addClass(icon, 'material-symbols-rounded');
        renderer.setAttribute(icon, 'aria-hidden', 'true');
        renderer.appendChild(icon, renderer.createText('explore'));
        renderer.insertBefore(host, icon, host.firstChild);
      } else if (!isAi && icon) {
        renderer.removeChild(host, icon);
        icon = null;
      }
    });
  }
}
