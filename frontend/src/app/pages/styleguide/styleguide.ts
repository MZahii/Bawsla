import { Component, ElementRef, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatChipsModule } from '@angular/material/chips';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatRadioModule } from '@angular/material/radio';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSliderModule } from '@angular/material/slider';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';

import { COURS } from '../../core/demo/demo-data';
import { ACTIVITE_JOURS, APPRENANTS, EVENEMENTS_CALENDRIER, TAGS_FORUM, tuile } from '../../core/demo/demo-plus';
import {
  Avatar, BwChart, BwChartType, Calendar, CalendarEvent, ChartSerie, CourseTile, Dropzone, Heatmap, Kpi, PageHeader,
  ProgressRing, Rating, Skeleton, Timeline, TimelineItem, VideoPlayer,
} from '../../shared/ui';

import { AiBox } from '../../shared/components/ai-box/ai-box';
import { AiLoading } from '../../shared/components/ai-loading/ai-loading';
import { Badge } from '../../shared/components/badge/badge';
import { EmptyState } from '../../shared/components/empty-state/empty-state';
import { Logo, LogoTheme } from '../../shared/components/logo/logo';
import { StatTile } from '../../shared/components/stat-tile/stat-tile';
import { BwButton } from '../../shared/directives/bw-button';

interface Swatch {
  token: string;
  usage: string;
}

/**
 * Bibliothèque de composants vivante (DESIGN.md) : palette, typographie, logos, composants partagés
 * (shared/ et shared/ui/) et composants Angular Material tels qu'ils sont thémés pour Bawsla.
 * Visible dans le back-office (/admin/composants). Styles dans styles/_styleguide.scss.
 */
@Component({
  selector: 'app-styleguide',
  imports: [
    ReactiveFormsModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule, MatRadioModule,
    MatSlideToggleModule, MatSliderModule, MatButtonToggleModule, MatChipsModule, MatDatepickerModule, MatTabsModule, MatMenuModule,
    MatTooltipModule, MatStepperModule, MatExpansionModule, MatTableModule, MatPaginatorModule,
    AiBox, AiLoading, Badge, EmptyState, Logo, StatTile, BwButton,
    Avatar, BwChart, Calendar, CourseTile, Dropzone, Heatmap, Kpi, PageHeader, ProgressRing, Rating, Skeleton, Timeline, VideoPlayer,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './styleguide.html',
})
export class Styleguide {
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly snack = inject(MatSnackBar);

  protected readonly sections = [
    { id: 'sg-palette-s', label: 'Palette' },
    { id: 'sg-type-s', label: 'Typographie' },
    { id: 'sg-logos-s', label: 'Logos' },
    { id: 'sg-buttons-s', label: 'Boutons' },
    { id: 'sg-kpi', label: 'Indicateurs' },
    { id: 'sg-charts', label: 'Graphiques' },
    { id: 'sg-tiles', label: 'Cartes de cours' },
    { id: 'sg-data', label: 'Données' },
    { id: 'sg-time', label: 'Temps' },
    { id: 'sg-forms', label: 'Formulaires' },
    { id: 'sg-media', label: 'Médias' },
    { id: 'sg-nav', label: 'Navigation' },
    { id: 'sg-table', label: 'Tableau' },
    { id: 'sg-ai-s', label: 'IA' },
    { id: 'sg-empty-s', label: 'État vide' },
  ];

  private readonly mois = ['Mai', 'Juin', 'Juil.', 'Août', 'Sept.', 'Oct.'];
  protected readonly charts: { type: BwChartType; titre: string; labels: string[]; series: ChartSerie[] }[] = [
    { type: 'line', titre: 'Courbes', labels: this.mois, series: [{ label: 'Étudiants', data: [110, 118, 121, 126, 182, 214] }, { label: 'Actifs', data: [60, 64, 40, 35, 120, 150] }] },
    { type: 'area', titre: 'Aire', labels: this.mois, series: [{ label: 'Minutes', data: [120, 210, 185, 240, 205, 260] }] },
    { type: 'bar', titre: 'Barres', labels: ['SQL', 'Spring', 'Angular', 'Git'], series: [{ label: 'Toi', data: [40, 88, 63, 100] }, { label: 'Autres apprenants', data: [58, 72, 69, 81] }] },
    { type: 'hbar', titre: 'Barres horizontales', labels: ['Cours', 'Quiz', 'Forum'], series: [{ label: 'Requêtes', data: [780, 511, 329] }] },
    { type: 'stacked', titre: 'Barres empilées', labels: ['Lun.', 'Mar.', 'Mer.', 'Jeu.', 'Ven.'], series: [{ label: 'Cours', data: [320, 410, 380, 450, 390] }, { label: 'Quiz', data: [140, 190, 210, 230, 180] }, { label: 'Forum', data: [60, 85, 70, 95, 80] }] },
    { type: 'doughnut', titre: 'Anneau', labels: ['Apprenants', 'Formateurs', 'Admins'], series: [{ label: 'Comptes', data: [214, 12, 2] }] },
    { type: 'radar', titre: 'Radar', labels: ['SQL', 'Jointures', 'Spring', 'Angular', 'Git', 'Docker'], series: [{ label: 'Toi', data: [82, 34, 71, 63, 92, 41] }, { label: 'Autres apprenants', data: [70, 58, 66, 69, 81, 61] }] },
  ];
  protected readonly tuiles = COURS.slice(0, 3).map((c, i) => tuile(c, i === 0));
  protected readonly noms = ['Malek Ouji', 'Mohamed Zahi', 'Imen Ayari', 'Sami Ben Ali'];
  protected readonly jours = ACTIVITE_JOURS;
  protected readonly evenements = EVENEMENTS_CALENDRIER as CalendarEvent[];
  protected readonly dernier = signal('');
  protected readonly timeline: TimelineItem[] = [
    { quand: '10:42', titre: 'Cours publié', detail: 'Bases de données relationnelles', icone: 'publish', ton: 'accent' },
    { quand: '09:15', titre: 'Résumé généré', detail: 'En attente de validation', icone: 'auto_awesome', ton: 'ia' },
    { quand: 'hier', titre: 'Quiz en échec pour 3 apprenants', icone: 'warning', ton: 'alerte' },
    { quand: 'lundi', titre: 'Compte créé', icone: 'person_add', ton: 'neutre' },
  ];
  protected readonly categories = ['Données', 'Back-end', 'Front-end', 'DevOps', 'Outils'];
  protected readonly etiquettes = TAGS_FORUM.slice(0, 5);
  protected readonly email = new FormControl('pas-un-email', Validators.email);
  protected readonly colonnes = ['nom', 'moyenne', 'progression', 'risque'];
  protected readonly lignes = APPRENANTS.slice(0, 4);

  constructor() {
    this.email.markAsTouched();
  }

  /** Les ancres défilent dans la zone de contenu de la coque, pas dans la fenêtre. */
  protected aller(e: Event, id: string): void {
    e.preventDefault();
    this.host.nativeElement.querySelector('#' + id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected toast(): void {
    this.snack.open('Modifications enregistrées', 'Annuler', { duration: 3000 });
  }

  protected readonly palettes: { name: string; swatches: Swatch[] }[] = [
    {
      name: 'Navy : structure',
      swatches: [
        { token: '--color-navy-900', usage: 'Menu latéral' },
        { token: '--color-navy-700', usage: 'Primaire, titres' },
        { token: '--color-navy-500', usage: 'Survol' },
        { token: '--color-navy-100', usage: 'Fonds doux' },
      ],
    },
    {
      name: 'Bordeaux : action',
      swatches: [
        { token: '--color-bordeaux-700', usage: 'Pressé' },
        { token: '--color-bordeaux-600', usage: 'Liens, focus' },
        { token: '--color-bordeaux-500', usage: 'Accent' },
        { token: '--color-bordeaux-100', usage: 'Fonds d’accent' },
      ],
    },
    {
      name: 'Or : IA',
      swatches: [
        { token: '--color-gold-700', usage: 'Survol IA' },
        { token: '--color-gold-500', usage: 'Bouton IA, aiguille' },
        { token: '--color-gold-100', usage: 'Fond de boîte IA' },
      ],
    },
    {
      name: 'Neutres',
      swatches: [
        { token: '--color-bg', usage: 'Fond de page' },
        { token: '--color-surface', usage: 'Cartes' },
        { token: '--color-border', usage: 'Bordures' },
        { token: '--color-text', usage: 'Texte' },
        { token: '--color-text-muted', usage: 'Texte secondaire' },
      ],
    },
    {
      name: 'Graphiques (ordre fixe)',
      swatches: [
        { token: '--chart-1', usage: 'Série 1' },
        { token: '--chart-2', usage: 'Série 2' },
        { token: '--chart-3', usage: 'Série 3 (avec légende)' },
        { token: '--chart-4', usage: 'Série 4' },
      ],
    },
    {
      name: 'États',
      swatches: [
        { token: '--color-success', usage: 'Succès' },
        { token: '--color-warning', usage: 'Avertissement' },
        { token: '--color-error', usage: 'Erreur' },
        { token: '--color-info', usage: 'Information' },
      ],
    },
  ];

  protected readonly iconThemes: { theme: LogoTheme; label: string; dark: boolean }[] = [
    { theme: 'light', label: 'Clair', dark: false },
    { theme: 'mono', label: 'Mono', dark: false },
    { theme: 'dark', label: 'Sombre', dark: true },
    { theme: 'white', label: 'Blanc', dark: true },
  ];

  protected readonly loading = signal(false);
  protected readonly lastEvent = signal<string | null>(null);

  protected simulate(): void {
    this.loading.set(true);
    setTimeout(() => this.loading.set(false), 2000);
  }
}
