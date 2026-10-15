import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { catchError, forkJoin, of } from 'rxjs';

import { formatDate } from '../../models/cours.constantes';
import { Fiche, ResumeLocal } from '../../models/cours.model';
import { CoursService } from '../../services/cours.service';
import { ExplicationLecteur } from '../explication-lecteur';
import { FicheLecteur } from '../fiche-lecteur';
import { ResumeLocalLecteur } from '../resume-local-lecteur';
import { ICONE_GENERATION, TITRE_GENERATION, TypeGeneration } from './studio-ia.modele';

interface Element {
  type: TypeGeneration;
  detail: string;
  date: string | null;
}

/**
 * « Studio IA » de l'apprenant, en lecture seule : la même présentation que celle du formateur, avec uniquement les éléments VALIDÉS
 * (fiche, explications, résumé automatique), sans score, alertes, extraits internes ni moteur. Chaque élément se déplie et réutilise le
 * composant de lecture existant.
 */
@Component({
  selector: 'app-studio-ia-lecteur',
  imports: [MatIconModule, MatExpansionModule, FicheLecteur, ExplicationLecteur, ResumeLocalLecteur],
  template: `
    @if (elements().length) {
      <section class="studio" aria-labelledby="studio-ia-lecteur-h">
        <h3 id="studio-ia-lecteur-h"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Studio IA</h3>
        <p class="muted small">Des contenus générés par l’IA à partir du cours, relus et validés par le formateur.</p>
        <mat-accordion displayMode="flat">
          @for (e of elements(); track e.type) {
            <mat-expansion-panel>
              <mat-expansion-panel-header>
                <mat-panel-title>
                  <mat-icon class="type" aria-hidden="true">{{ icones[e.type] }}</mat-icon>
                  {{ titres[e.type] }}
                </mat-panel-title>
                <mat-panel-description>
                  <span class="pill ok">Validé</span>
                  <span class="detail muted small">{{ e.detail }}</span>
                  @if (e.date) { <span class="muted small">{{ date(e.date) }}</span> }
                </mat-panel-description>
              </mat-expansion-panel-header>
              <ng-template matExpansionPanelContent>
                @switch (e.type) {
                  @case ('fiche') {
                    @if (fiche(); as f) {
                      <app-fiche-lecteur [coursId]="coursId()" [fiche]="f" [titreCours]="titreCours()" />
                    }
                  }
                  @case ('resume') {
                    <app-resume-local-lecteur [coursId]="coursId()" />
                  }
                  @case ('explication') {
                    <app-explication-lecteur [coursId]="coursId()" />
                  }
                }
              </ng-template>
            </mat-expansion-panel>
          }
        </mat-accordion>
      </section>
    } @else if (etat() === 'ok') {
      <section class="studio" aria-labelledby="studio-ia-lecteur-h">
        <h3 id="studio-ia-lecteur-h"><mat-icon class="boussole" aria-hidden="true">explore</mat-icon> Studio IA</h3>
        <p class="muted">Le formateur n’a pas encore publié de contenu généré par l’IA pour ce cours.</p>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .studio { display: grid; gap: var(--space-3); padding: var(--space-4); border: 1px solid var(--color-gold-500); border-radius: var(--radius-sm); background: var(--color-surface); }
    h3 { display: flex; align-items: center; gap: var(--space-2); margin: 0; font: 600 20px var(--font-heading); }
    p { margin: 0; }
    .boussole, .type { color: var(--color-gold-700); }
    mat-panel-title { display: flex; align-items: center; gap: var(--space-2); font-weight: 600; }
    mat-panel-description { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    mat-expansion-panel { border: 1px solid var(--color-border); border-radius: var(--radius-sm); box-shadow: none; margin-bottom: var(--space-2); }
  `,
})
export class StudioIaLecteur implements OnInit {
  private readonly service = inject(CoursService);

  readonly coursId = input.required<number>();
  readonly titreCours = input('');

  protected readonly icones = ICONE_GENERATION;
  protected readonly titres = TITRE_GENERATION;
  protected readonly etat = signal<'chargement' | 'ok'>('chargement');
  protected readonly fiche = signal<Fiche | null>(null);
  protected readonly resume = signal<ResumeLocal | null>(null);
  protected readonly elements = signal<Element[]>([]);

  ngOnInit(): void {
    const id = this.coursId();
    forkJoin({
      fiche: this.service.fiche(id).pipe(catchError(() => of(null))),
      resume: this.service.resumeLocal(id).pipe(catchError(() => of(null))),
      disponibles: this.service.explicationsDisponibles(id).pipe(catchError(() => of([]))),
    }).subscribe(({ fiche, resume, disponibles }) => {
      // L'apprenant ne reçoit que du VALIDÉ ; on garde cette règle côté affichage aussi.
      const f = fiche && fiche.statut === 'VALIDEE' ? fiche : null;
      const r = resume && resume.statut === 'VALIDE' ? resume : null;
      const e = disponibles.filter((d) => d.statut === 'VALIDEE');
      this.fiche.set(f);
      this.resume.set(r);
      const liste: Element[] = [];
      if (f) liste.push({ type: 'fiche', detail: 'Résumé, mots-clés et notions', date: f.dateValidation ?? null });
      if (r) liste.push({ type: 'resume', detail: `${r.langue === 'fr' ? 'Français' : 'English'} · ${r.modeUtilise === 'extractif' ? 'phrases clés' : 'reformulé'}`, date: r.dateValidation ?? null });
      if (e.length) liste.push({ type: 'explication', detail: e.length > 1 ? `${e.length} versions` : e[0].langue === 'fr' ? 'Français' : 'English', date: null });
      this.elements.set(liste);
      this.etat.set('ok');
    });
  }

  protected date(iso: string): string {
    return formatDate(iso);
  }
}
