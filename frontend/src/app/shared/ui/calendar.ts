import { Component, computed, input, output } from '@angular/core';

export interface CalendarEvent {
  jour: number;
  titre: string;
  type: 'cours' | 'quiz' | 'rappel' | 'live' | 'examen';
}

const JOURS = ['Lun.', 'Mar.', 'Mer.', 'Jeu.', 'Ven.', 'Sam.', 'Dim.'];

/**
 * Calendrier mensuel avec événements. Les types se distinguent par la couleur ET par un libellé.
 * <bw-calendar [annee]="2026" [mois]="9" [events]="evenements" [aujourdhui]="8" />  (mois de 0 à 11)
 */
@Component({
  selector: 'bw-calendar',
  template: `
    <div class="head" aria-hidden="true">
      @for (j of jours; track j) {
        <span>{{ j }}</span>
      }
    </div>
    <div class="grid">
      @for (c of cases(); track $index) {
        <div class="day" [class.out]="!c.jour" [class.today]="c.jour === aujourdhui()">
          @if (c.jour) {
            <span class="num">{{ c.jour }}</span>
            @for (e of c.events; track e.titre) {
              <button type="button" [class]="'ev ' + e.type" (click)="select.emit(e)" [title]="e.titre">
                <span class="t">{{ libelles[e.type] }}</span> {{ e.titre }}
              </button>
            }
          }
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: block; }
    .head, .grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); }
    .head span { padding: var(--space-2); font-size: 13px; font-weight: 600; color: var(--color-text-muted); }
    .grid { border-top: 1px solid var(--color-border); border-left: 1px solid var(--color-border); }
    .day { min-height: 104px; padding: 6px; border-right: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); display: flex; flex-direction: column; gap: 4px; min-width: 0; }
    .day.out { background: var(--color-bg); }
    .num { font-size: 13px; font-weight: 600; color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .today .num { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: var(--color-primary); color: var(--color-on-primary); }
    .ev { display: block; width: 100%; padding: 2px 6px; border: 0; border-left: 3px solid; border-radius: 3px; text-align: left; font: 500 12px/18px var(--font-body); color: var(--color-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; cursor: pointer; background: var(--color-neutral-soft); }
    .t { font-weight: 700; }
    .cours { border-color: var(--chart-1); }
    .quiz { border-color: var(--chart-2); }
    .rappel { border-color: var(--chart-3); }
    .live { border-color: var(--chart-4); }
    .examen { border-color: var(--color-error); }
    @media (max-width: 720px) { .day { min-height: 64px; } .ev { font-size: 0; padding: 0; height: 6px; border-left-width: 0; } .cours { background: var(--chart-1); } .quiz { background: var(--chart-2); } .rappel { background: var(--chart-3); } .live { background: var(--chart-4); } .examen { background: var(--color-error); } }
  `,
})
export class Calendar {
  readonly annee = input(2026);
  readonly mois = input(9);
  readonly events = input<CalendarEvent[]>([]);
  readonly aujourdhui = input<number | null>(null);
  readonly select = output<CalendarEvent>();

  protected readonly jours = JOURS;
  protected readonly libelles: Record<CalendarEvent['type'], string> = { cours: 'Cours', quiz: 'Quiz', rappel: 'Rappel', live: 'Live', examen: 'Examen' };

  protected readonly cases = computed(() => {
    const premier = (new Date(this.annee(), this.mois(), 1).getDay() + 6) % 7;
    const n = new Date(this.annee(), this.mois() + 1, 0).getDate();
    const total = Math.ceil((premier + n) / 7) * 7;
    return Array.from({ length: total }, (_, i) => {
      const jour = i - premier + 1;
      const ok = jour >= 1 && jour <= n;
      return { jour: ok ? jour : 0, events: ok ? this.events().filter((e) => e.jour === jour) : [] };
    });
  });
}
