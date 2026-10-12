import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';

import { AiBox, BwButton } from '../../../shared';
import { MatIconModule } from '@angular/material/icon';
import { ResumeLocal } from '../models/cours.model';
import { CoursService } from '../services/cours.service';
import { VoixNavigateur } from '../services/voix-navigateur';
import { ResumeLocalAudio } from './resume-local-audio';

/**
 * Section « Résumé automatique » du lecteur : le résumé VALIDÉ par le formateur (texte phrase par phrase avec la page du cours, et audio).
 * Rien n'est affiché tant qu'aucun résumé n'est validé. Sans audio serveur, la voix du navigateur sert de secours.
 */
@Component({
  selector: 'app-resume-local-lecteur',
  imports: [MatIconModule, AiBox, BwButton, ResumeLocalAudio],
  template: `
    @if (resume(); as r) {
      <section aria-label="Résumé automatique">
        <bw-ai-box heading="Résumé automatique" label="Généré par un modèle local">
          <p class="intro muted small">
            {{ r.modeUtilise === 'extractif' ? 'Les phrases les plus importantes du cours.' : 'Le cours résumé par un modèle installé sur le serveur.' }}
          </p>
          <ol class="phrases">
            @for (p of r.phrases; track p.ordre) {
              <li>
                <span dir="auto">{{ p.texte }}</span>
                @if (p.page) {
                  <span class="page muted small">p. {{ p.page }}</span>
                }
              </li>
            }
          </ol>
          <app-resume-local-audio [coursId]="coursId()" [actif]="r.aAudio" [cle]="r.id + '|' + (r.dureeAudio ?? 0)" [fournisseur]="r.fournisseurAudio" />
          @if (!r.aAudio && voix.disponible) {
            <div class="navigateur">
              <button type="button" bwButton="secondaire" (click)="basculerVoix(r)" [attr.aria-pressed]="voix.enLecture()">
                <mat-icon aria-hidden="true">{{ voix.enLecture() ? 'stop_circle' : 'record_voice_over' }}</mat-icon>
                {{ voix.enLecture() ? 'Arrêter la lecture' : 'Écouter avec la voix du navigateur' }}
              </button>
              <span class="muted small">L’audio n’est pas encore prêt : ton navigateur lit le texte avec sa propre voix.</span>
            </div>
          }
          <span bwAiSource>Source : le cours, résumé par un modèle local et validé par le formateur</span>
        </bw-ai-box>
      </section>
    }
  `,
  styles: `
    :host { display: block; }
    .intro { margin: 0 0 var(--space-3); }
    .phrases { margin: 0 0 var(--space-3); padding-left: var(--space-5); display: grid; gap: var(--space-2); line-height: 24px; }
    .page { margin-left: var(--space-2); white-space: nowrap; }
    .navigateur { display: grid; justify-items: start; gap: var(--space-2); margin-top: var(--space-2); }
  `,
})
export class ResumeLocalLecteur implements OnInit, OnDestroy {
  private readonly service = inject(CoursService);
  protected readonly voix = inject(VoixNavigateur);

  readonly coursId = input.required<number>();
  protected readonly resume = signal<ResumeLocal | null>(null);

  ngOnInit(): void {
    this.service.resumeLocal(this.coursId()).subscribe({
      next: (r) => this.resume.set(r),
      // La section est facultative : en cas d'erreur, elle reste simplement masquée.
      error: () => undefined,
    });
  }

  ngOnDestroy(): void {
    this.voix.arreter();
  }

  protected basculerVoix(r: ResumeLocal): void {
    if (this.voix.enLecture()) this.voix.arreter();
    else this.voix.lire(r.texte, r.langue);
  }
}
