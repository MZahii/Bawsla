import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { ROLE_LABELS } from '../../../../shared/models/user.model';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { DemandeFormateur } from '../../models/utilisateur.model';
import { AvatarService } from '../../services/avatar.service';
import { UserService } from '../../services/user.service';

const JOUR = 86_400_000;

/**
 * Back-office : demandes pour devenir formateur (user-service). N'importe qui peut postuler, à l'inscription
 * ou depuis « Mon compte » ; l'admin accepte (le compte devient formateur validé et peut publier) ou refuse
 * avec un motif, que le demandeur voit dans « Mon compte ».
 */
@Component({
  selector: 'app-admin-formateurs',
  imports: [DatePipe, FormsModule, MatButtonToggleModule, MatIconModule, BwButton, EmptyState, Avatar, Kpi, PageHeader, Skeleton],
  template: `
    <bw-page-header title="Demandes formateurs" subtitle="Toute personne peut demander à enseigner. Vérifie son expertise avant d’ouvrir la publication." [crumbs]="[{ label: 'Admin', link: '/admin' }, { label: 'Demandes formateurs' }]" />

    <div class="grid-4">
      <bw-kpi label="En attente" [value]="enAttente().length" icon="hourglass_top" />
      <bw-kpi label="Acceptées (30 jours)" [value]="traitees30j('ACCEPTEE')" icon="how_to_reg" />
      <bw-kpi label="Refusées (30 jours)" [value]="traitees30j('REFUSEE')" icon="person_off" />
      <bw-kpi label="Délai moyen de réponse" [value]="delaiMoyen()" icon="schedule" />
    </div>

    <div class="bar mt">
      <mat-button-toggle-group [value]="vue()" (change)="vue.set($event.value)" aria-label="Demandes affichées">
        <mat-button-toggle value="attente">En attente ({{ enAttente().length }})</mat-button-toggle>
        <mat-button-toggle value="traitees">Traitées ({{ toutes().length - enAttente().length }})</mat-button-toggle>
      </mat-button-toggle-group>
    </div>

    @if (erreur()) {
      <section class="card mt"><bw-empty-state icon="cloud_off" title="Demandes indisponibles" [message]="erreur()!" actionLabel="Réessayer" (action)="charger()" /></section>
    } @else if (chargement()) {
      <div class="list mt" aria-busy="true" aria-label="Chargement des demandes"><bw-skeleton height="180px" /><bw-skeleton height="180px" /></div>
    } @else {
      <div class="list mt">
        @for (d of affichees(); track d.id) {
          <article class="card req">
            <header>
              <bw-avatar [nom]="nomComplet(d)" [size]="48" [src]="photos()[d.demandeur.id]" />
              <div class="who">
                <h2>{{ nomComplet(d) }}</h2>
                <span class="muted small">{{ d.demandeur.email }} · {{ roles[d.demandeur.role] }} · demande du {{ d.dateCreation | date: 'dd/MM/yyyy HH:mm' }}</span>
              </div>
              @if (d.statut === 'ACCEPTEE') { <span class="pill ok">Acceptée</span> }
              @if (d.statut === 'REFUSEE') { <span class="pill ko">Refusée</span> }
            </header>
            <dl>
              <div><dt>Expertise</dt><dd dir="auto">{{ d.expertise }}</dd></div>
              <div><dt>Lien</dt><dd>{{ d.lien || '—' }}</dd></div>
              @if (d.dateTraitement) { <div><dt>Traitée le</dt><dd>{{ d.dateTraitement | date: 'dd/MM/yyyy HH:mm' }}</dd></div> }
            </dl>
            @if (d.presentation) { <p class="bio" dir="auto">{{ d.presentation }}</p> }
            @if (d.motifRefus) { <p class="small"><strong>Motif du refus : </strong><span dir="auto">{{ d.motifRefus }}</span></p> }

            @if (d.statut === 'EN_ATTENTE') {
              @if (refus() === d.id) {
                <label class="lbl" [for]="'m-' + d.id">Motif du refus (visible par la personne)</label>
                <textarea [id]="'m-' + d.id" rows="2" maxlength="500" [(ngModel)]="motif" placeholder="Ex. : merci de préciser ton expérience dans le domaine."></textarea>
                <footer>
                  <button type="button" bwButton="fantome" (click)="annulerRefus()">Annuler</button>
                  <button type="button" bwButton="danger" (click)="refuser(d)" [disabled]="!motif.trim() || enCours() === d.id">Confirmer le refus</button>
                </footer>
              } @else {
                <footer>
                  <button type="button" bwButton="secondaire" (click)="refus.set(d.id); motif = ''" [disabled]="enCours() === d.id">Refuser</button>
                  <button type="button" bwButton="secondaire" class="ok" (click)="accepter(d)" [disabled]="enCours() === d.id"><mat-icon>check</mat-icon> Accepter</button>
                </footer>
              }
            }
          </article>
        } @empty {
          @if (vue() === 'attente') {
            <bw-empty-state icon="how_to_reg" title="Aucune demande en attente" message="Les nouvelles demandes apparaîtront ici." />
          } @else {
            <bw-empty-state icon="history" title="Aucune demande traitée" message="Les demandes acceptées ou refusées apparaîtront ici." />
          }
        }
      </div>
    }
  `,
  styles: `
    .bar { display: flex; justify-content: flex-start; }
    .list { display: grid; gap: var(--space-4); }
    .req { padding: var(--space-5); }
    header { display: flex; align-items: center; gap: var(--space-3); }
    .who { display: flex; flex-direction: column; flex: 1; }
    h2 { margin: 0; font: 600 18px var(--font-heading); }
    dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: var(--space-3); margin: var(--space-4) 0; }
    dt { font-size: 13px; color: var(--color-text-muted); }
    dd { margin: 2px 0 0; font-weight: 600; overflow-wrap: anywhere; }
    .bio { margin: 0; padding: var(--space-3); border-left: 3px solid var(--color-border); background: var(--color-bg); white-space: pre-line; }
    footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: var(--space-2); margin-top: var(--space-4); }
    .lbl { display: block; margin-top: var(--space-4); font-weight: 600; font-size: 14px; }
    textarea { width: 100%; box-sizing: border-box; margin-top: var(--space-2); padding: var(--space-3); border: 1px solid var(--color-border); border-radius: 6px; background: var(--color-surface); color: var(--color-text); font: 400 15px var(--font-body); }
  `,
})
export class AdminFormateurs implements OnInit {
  private readonly api = inject(UserService);
  private readonly avatars = inject(AvatarService);
  private readonly snack = inject(MatSnackBar);
  protected readonly roles = ROLE_LABELS;

  protected readonly toutes = signal<DemandeFormateur[]>([]);
  protected readonly photos = signal<Record<number, string | undefined>>({});
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly vue = signal<'attente' | 'traitees'>('attente');
  protected readonly refus = signal<number | null>(null);
  protected readonly enCours = signal<number | null>(null);
  protected motif = '';

  /** Les plus anciennes d'abord : on traite dans l'ordre d'arrivée. */
  protected readonly enAttente = computed(() =>
    this.toutes().filter((d) => d.statut === 'EN_ATTENTE').sort((a, b) => a.dateCreation.localeCompare(b.dateCreation)),
  );
  protected readonly affichees = computed(() =>
    this.vue() === 'attente'
      ? this.enAttente()
      : this.toutes().filter((d) => d.statut !== 'EN_ATTENTE').sort((a, b) => (b.dateTraitement ?? '').localeCompare(a.dateTraitement ?? '')),
  );
  /** Délai moyen entre la demande et la décision, sur les demandes traitées. */
  protected readonly delaiMoyen = computed(() => {
    const t = this.toutes().filter((d) => d.dateTraitement);
    if (t.length === 0) return '—';
    const h = t.reduce((s, d) => s + (new Date(d.dateTraitement!).getTime() - new Date(d.dateCreation).getTime()), 0) / t.length / 3_600_000;
    return h < 1 ? `${Math.max(1, Math.round(h * 60))} min` : h < 48 ? `${Math.round(h)} h` : `${Math.round(h / 24)} j`;
  });

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.chargement.set(true);
    this.erreur.set(null);
    this.api.demandesFormateur().subscribe({
      next: (liste) => {
        this.toutes.set(liste);
        this.chargement.set(false);
        for (const d of liste) {
          this.avatars.url(d.demandeur.id, d.demandeur.avatarVersion).subscribe((url) => {
            if (url) this.photos.update((p) => ({ ...p, [d.demandeur.id]: url }));
          });
        }
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger les demandes'));
        this.chargement.set(false);
      },
    });
  }

  protected nomComplet(d: DemandeFormateur): string {
    return `${d.demandeur.prenom} ${d.demandeur.nom}`.trim();
  }

  protected traitees30j(statut: 'ACCEPTEE' | 'REFUSEE'): number {
    const depuis = Date.now() - 30 * JOUR;
    return this.toutes().filter((d) => d.statut === statut && d.dateTraitement && new Date(d.dateTraitement).getTime() >= depuis).length;
  }

  protected accepter(d: DemandeFormateur): void {
    this.enCours.set(d.id);
    this.api.accepterDemande(d.id).subscribe({
      next: (maj) => {
        this.remplacer(maj);
        this.snack.open(`${this.nomComplet(d)} peut maintenant publier des cours`, 'OK', { duration: 3000 });
      },
      error: (err) => this.echec(err),
    });
  }

  protected refuser(d: DemandeFormateur): void {
    this.enCours.set(d.id);
    this.api.refuserDemande(d.id, this.motif.trim()).subscribe({
      next: (maj) => {
        this.remplacer(maj);
        this.annulerRefus();
        this.snack.open(`Demande de ${this.nomComplet(d)} refusée`, 'OK', { duration: 3000 });
      },
      error: (err) => this.echec(err),
    });
  }

  protected annulerRefus(): void {
    this.refus.set(null);
    this.motif = '';
  }

  private remplacer(maj: DemandeFormateur): void {
    this.toutes.update((l) => l.map((x) => (x.id === maj.id ? maj : x)));
    this.enCours.set(null);
  }

  private echec(err: unknown): void {
    this.enCours.set(null);
    this.snack.open(errorMessage(err, 'Action impossible'), 'OK', { duration: 5000 });
    // La demande a pu être traitée par un autre admin entre-temps : on recharge.
    this.charger();
  }
}
