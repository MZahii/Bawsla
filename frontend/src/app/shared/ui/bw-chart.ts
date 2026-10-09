import { Component, DestroyRef, ElementRef, afterNextRender, computed, effect, inject, input, viewChild } from '@angular/core';
import { Chart, ChartConfiguration, ChartType, registerables } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import { ThemeService } from '../../core/theme/theme.service';

// Enregistré ici (et non dans app.config) pour que Chart.js reste dans les morceaux chargés à la demande.
Chart.register(...registerables);

export interface ChartSerie {
  label: string;
  data: number[];
  /** Index de couleur imposé (1 à 4) : la couleur suit la série, jamais son rang. */
  color?: 1 | 2 | 3 | 4;
}

export type BwChartType = 'line' | 'area' | 'bar' | 'hbar' | 'stacked' | 'doughnut' | 'radar';

/** Lit une variable CSS du thème courant (les couleurs viennent de styles/_tokens.scss). */
function token(name: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || 'gray';
}

/**
 * Graphique Chart.js aux couleurs de Bawsla, clair et sombre.
 * Couleurs : --chart-1..4 (ordre fixe validé daltonisme). Un seul axe Y, toujours.
 *
 * <bw-chart type="line" [labels]="mois" [series]="[{ label: 'Étudiants', data: [...] }]" [height]="260" />
 */
@Component({
  selector: 'bw-chart',
  imports: [BaseChartDirective],
  template: `
    <div class="wrap" [style.height.px]="height()">
      <canvas baseChart [type]="chartType()" [data]="data()" [options]="options()" [attr.aria-label]="ariaLabel()" role="img"></canvas>
    </div>
  `,
  styles: `:host { display: block; min-width: 0; } .wrap { position: relative; width: 100%; min-width: 0; overflow: hidden; }`,
})
export class BwChart {
  private readonly theme = inject(ThemeService);
  private readonly chart = viewChild(BaseChartDirective);

  readonly type = input<BwChartType>('line');
  readonly labels = input<string[]>([]);
  readonly series = input<ChartSerie[]>([]);
  readonly height = input(260);
  readonly unit = input('');
  readonly legend = input<boolean | undefined>(undefined);
  readonly max = input<number | undefined>(undefined);
  readonly ariaLabel = input('Graphique');

  protected readonly chartType = computed<ChartType>(() => {
    const t = this.type();
    return t === 'area' ? 'line' : t === 'hbar' || t === 'stacked' ? 'bar' : t;
  });

  protected readonly data = computed<ChartConfiguration['data']>(() => {
    this.theme.theme();
    const t = this.type();
    const surface = token('--color-surface');
    const colors = [1, 2, 3, 4].map((i) => token(`--chart-${i}`));
    if (t === 'doughnut') {
      const s = this.series()[0] ?? { label: '', data: [] };
      return {
        labels: this.labels(),
        datasets: [{ label: s.label, data: s.data, backgroundColor: colors, borderColor: surface, borderWidth: 2, hoverOffset: 4 }],
      };
    }
    return {
      labels: this.labels(),
      datasets: this.series().map((s, i) => {
        const c = colors[(s.color ?? i + 1) - 1];
        const base = { label: s.label, data: s.data, borderColor: c, backgroundColor: c };
        if (t === 'line' || t === 'area') {
          return { ...base, borderWidth: 2, pointRadius: 0, pointHoverRadius: 5, pointBackgroundColor: c, pointBorderColor: surface, pointBorderWidth: 2, tension: 0.3, fill: t === 'area' ? 'origin' : false, backgroundColor: t === 'area' ? c + '22' : c };
        }
        if (t === 'radar') {
          return { ...base, borderWidth: 2, backgroundColor: c + '33', pointRadius: 3, pointBackgroundColor: c };
        }
        return { ...base, borderRadius: 4, borderSkipped: 'start', maxBarThickness: t === 'hbar' ? 18 : 28, borderColor: surface, borderWidth: t === 'stacked' ? { top: 2 } : 0 };
      }),
    } as ChartConfiguration['data'];
  });

  protected readonly options = computed<ChartConfiguration['options']>(() => {
    this.theme.theme();
    const t = this.type();
    const text = token('--color-text-muted');
    const grid = token('--chart-grid');
    const font = { family: getComputedStyle(document.body).fontFamily, size: 12 };
    const unit = this.unit();
    const showLegend = this.legend() ?? (t === 'doughnut' || this.series().length > 1);
    const common = {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 300 },
      plugins: {
        legend: { display: showLegend, position: 'bottom' as const, labels: { color: text, font, boxWidth: 10, boxHeight: 10, usePointStyle: true, pointStyle: 'rectRounded' } },
        tooltip: {
          backgroundColor: token('--color-navy-900'),
          titleFont: { ...font, weight: 'bold' as const },
          bodyFont: font,
          padding: 10,
          cornerRadius: 6,
          callbacks: unit ? { label: (c: { dataset: { label?: string }; formattedValue: string }) => ` ${c.dataset.label ?? ''} : ${c.formattedValue} ${unit}` } : undefined,
        },
      },
    };
    if (t === 'doughnut') return { ...common, cutout: '68%' } as ChartConfiguration['options'];
    if (t === 'radar') {
      return {
        ...common,
        scales: { r: { min: 0, max: this.max() ?? 100, ticks: { display: false, stepSize: 25 }, grid: { color: grid }, angleLines: { color: grid }, pointLabels: { color: text, font } } },
      } as ChartConfiguration['options'];
    }
    const axis = { ticks: { color: text, font }, grid: { color: grid, drawTicks: false }, border: { display: false } };
    return {
      ...common,
      indexAxis: t === 'hbar' ? 'y' : 'x',
      interaction: { mode: 'index', intersect: false },
      scales: {
        x: { ...axis, stacked: t === 'stacked', grid: { display: t === 'hbar', color: grid } },
        y: { ...axis, stacked: t === 'stacked', beginAtZero: true, max: this.max(), grid: { display: t !== 'hbar', color: grid } },
      },
    } as ChartConfiguration['options'];
  });

  constructor() {
    // Le thème change : Chart.js doit relire les couleurs.
    effect(() => {
      this.theme.theme();
      queueMicrotask(() => this.chart()?.update());
    });

    // Chart.js ne suit pas toujours un conteneur qui rétrécit (grilles, menu latéral replié) :
    // on le redimensionne nous-mêmes quand la largeur du composant change.
    const host = inject(ElementRef<HTMLElement>).nativeElement;
    let largeur = 0;
    const ro = new ResizeObserver(([e]) => {
      const w = Math.round(e.contentRect.width);
      if (w === largeur) return;
      largeur = w;
      this.chart()?.chart?.resize();
    });
    afterNextRender(() => ro.observe(host));
    inject(DestroyRef).onDestroy(() => ro.disconnect());
  }
}
