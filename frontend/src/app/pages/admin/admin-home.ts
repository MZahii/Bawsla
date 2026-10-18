import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';

import { API_BASE_URL } from '../../core/config/api.config';
import { EmptyState } from '../../shared';
import { errorMessage } from '../../shared/models/api-response.model';
import { ROLE_LABELS, Role } from '../../shared/models/user.model';
import { BwChart, Kpi, PageHeader, Skeleton } from '../../shared/ui';
import { Cours } from '../../features/cours/models/cours.model';
import { CoursService } from '../../features/cours/services/cours.service';
import { JournalModeration } from '../../features/forum/models/moderation.model';
import { LIBELLES_ACTION, LIBELLES_CIBLE, tempsRelatif } from '../../features/forum/components/outils';
import { ModerationForumService } from '../../features/forum/services/moderation.service';
import { Certificat } from '../../features/quiz/models/quiz.model';
import { QuizService } from '../../features/quiz/services/quiz.service';
import { MetriquesIa } from '../../features/user/models/ia.model';
import { DemandeFormateur, Utilisateur } from '../../features/user/models/utilisateur.model';
import { IaUserService } from '../../features/user/services/ia-user.service';
import { UserService } from '../../features/user/services/user.service';

type Etat = 'chargement' | 'ok' | 'erreur';

interface EtatService {
  nom: string;
  ok: boolean | null;
  latence?: number;
}

const SERVICES = [
  { nom: 'user-service', chemin: 'users' },
  { nom: 'cours-service', chemin: 'cours' },
  { nom: 'quiz-service', chemin: 'quiz' },
  { nom: 'forum-service', chemin: 'forum' },
  { nom: 'ai-service', chemin: 'ai' },
];
const ROLES: Role[] = ['ETUDIANT', 'ENSEIGNANT', 'ADMIN'];

/**
 * Tableau de bord du back-office.
 * Données : user-service (GET /api/users, GET /api/users/demandes-formateur), cours-service (GET /api/cours),
 * quiz-service (GET /api/quiz/certificats), forum-service (GET /api/forum/admin/journal),
 * ai-service (GET /api/ai/user/metriques) et les GET /api/{module}/health de chaque service, via la gateway.
 */
@Component({
  selector: 'app-admin-home',
  imports: [DatePipe, RouterLink, MatIconModule, EmptyState, BwChart, Kpi, PageHeader, Skeleton],
  template: `
    <bw-page-header title="Vue d’ensemble" [subtitle]="'État de la plateforme au ' + aujourdhui + '.'" />

    @switch (etat()) {
      @case ('chargement') {
        <div class="grid-4" aria-busy="true">
          @for (i of [1, 2, 3, 4]; track i) { <bw-skeleton height="104px" /> }
        </div>
        <div class="grid-main mt" aria-busy="true"><bw-skeleton height="320px" /><bw-skeleton height="320px" /></div>
      }
      @case ('erreur') {
        <section class="card">
          <bw-empty-state icon="error" title="Impossible d’afficher la vue d’ensemble" [message]="erreur()" actionLabel="Réessayer" (action)="charger()" />
        </section>
      }
      @default {
        <div class="grid-4">
          <bw-kpi label="Apprenants" [value]="parRole()['ETUDIANT']" icon="group" />
          <bw-kpi label="Formateurs" [value]="parRole()['ENSEIGNANT']" icon="school" />
          <bw-kpi label="Cours publiés" [value]="nbPublies()" icon="menu_book" />
          <bw-kpi label="Certificats délivrés" [value]="certificats().length" icon="workspace_premium" />
        </div>

        @if (demandes().length) {
          <a class="card todo mt" routerLink="/admin/formateurs">
            <mat-icon aria-hidden="true">how_to_reg</mat-icon>
            <span class="grow">
              <strong>{{ demandes().length }} {{ demandes().length > 1 ? 'demandes de formateur à examiner' : 'demande de formateur à examiner' }}</strong>
              <span class="muted small"> La plus ancienne date du {{ demandes()[0].dateCreation | date: 'd MMMM y' }}.</span>
            </span>
            <mat-icon aria-hidden="true">chevron_right</mat-icon>
          </a>
        }

        <div class="grid-main mt">
          <section class="card">
            <div class="card-head"><div><h2>Croissance</h2><span class="sub">Comptes cumulés sur 12 mois</span></div></div>
            <div class="card-body"><bw-chart type="line" [labels]="croissance().labels" [series]="croissance().series" [height]="260" ariaLabel="Nombre de comptes apprenants et formateurs" /></div>
          </section>
          <section class="card">
            <div class="card-head"><div><h2>Comptes par rôle</h2><span class="sub">Tous les comptes enregistrés</span></div></div>
            <div class="card-body"><bw-chart type="doughnut" [labels]="repartition().labels" [series]="repartition().series" [height]="260" ariaLabel="Répartition des comptes par rôle" /></div>
          </section>
        </div>

        <div class="grid-main mt">
          <section class="card">
            <div class="card-head"><div><h2>Certificats délivrés</h2><span class="sub">Par mois, sur 12 mois</span></div><a routerLink="/admin/certificats" class="lnk small">Tous</a></div>
            @if (certificats().length) {
              <div class="card-body"><bw-chart type="bar" [labels]="certificatsParMois().labels" [series]="certificatsParMois().series" [height]="260" ariaLabel="Certificats délivrés par mois" /></div>
            } @else {
              <bw-empty-state title="Aucun certificat délivré pour l’instant" />
            }
          </section>
          <section class="card">
            <div class="card-head">
              <h2>Services</h2>
              @if (services().every(s => s.ok === true)) { <span class="pill ok">Tous opérationnels</span> }
              @else if (services().some(s => s.ok === false)) { <span class="pill warn">Service indisponible</span> }
            </div>
            <ul class="svc">
              @for (s of services(); track s.nom) {
                <li>
                  @if (s.ok === null) { <span class="pill">…</span> }
                  @else { <span class="pill" [class.ok]="s.ok" [class.warn]="!s.ok">{{ s.ok ? 'OK' : 'Indisponible' }}</span> }
                  <span class="small grow">{{ s.nom }}</span>
                  @if (s.latence !== undefined) { <span class="num small muted">{{ s.latence }} ms</span> }
                </li>
              }
            </ul>
          </section>
        </div>
      }
    }

    <div class="grid-main mt">
      <section class="card">
        <div class="card-head"><div><h2>Dernières actions</h2><span class="sub">Journal de modération du forum</span></div><a routerLink="/admin/audit" class="lnk small">Journal complet</a></div>
        @if (etatJournal() === 'chargement') {
          <div class="card-body"><bw-skeleton height="180px" /></div>
        } @else if (etatJournal() === 'erreur') {
          <bw-empty-state icon="error" title="Journal indisponible" [message]="erreurJournal()" />
        } @else if (journal().length) {
          <ul class="svc">
            @for (a of journal(); track a.id) {
              <li>
                <span class="num small muted quand">{{ tempsRelatif(a.creeLe) }}</span>
                <span class="small grow"><strong>{{ a.acteurNom || 'Un modérateur' }}</strong> : {{ libellesAction[a.action] }} ({{ libellesCible[a.cibleType] || a.cibleType }})</span>
              </li>
            }
          </ul>
        } @else {
          <bw-empty-state title="Aucune action enregistrée" />
        }
      </section>
      <section class="card">
        <div class="card-head"><h2>Modèles IA</h2><a routerLink="/admin/ia" class="lnk small">Supervision</a></div>
        @if (etatIa() === 'chargement') {
          <div class="card-body"><bw-skeleton height="180px" /></div>
        } @else if (metriques(); as m) {
          <div class="card-body ia">
            <p class="small">Dernier entraînement le <strong>{{ m.dateEntrainement | date: 'd MMMM y à HH:mm' }}</strong></p>
            @if (m.donneesSimulees) { <span class="pill warn">Données simulées</span> }
          </div>
          <ul class="svc">
            <li><span class="small grow">Risque d’échec (Random Forest) : F1 sur le test</span><strong class="num small">{{ pct(m.risque.test.f1) }}</strong></li>
            <li><span class="small grow">Recommandations (hybride) : réussite à 5</span><strong class="num small">{{ pct(m.recommandation.evaluation.hybride.reussiteA5) }}</strong></li>
            <li><span class="small grow">Profils (k-means, k = {{ m.profils.k }}) : silhouette</span><strong class="num small">{{ m.profils.silhouette.toFixed(2).replace('.', ',') }}</strong></li>
          </ul>
        } @else {
          <bw-empty-state title="Aucun modèle entraîné" [message]="erreurIa()" />
        }
      </section>
    </div>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; }
    .svc li { display: flex; align-items: center; gap: var(--space-3); padding: 10px var(--space-5); border-bottom: 1px solid var(--color-border); }
    .svc li:last-child { border-bottom: 0; }
    .grow { flex: 1; }
    .quand { width: 96px; flex: none; }
    .lnk { color: var(--color-bordeaux-600); font-weight: 600; text-decoration: none; }
    .ia { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-3); }
    .ia p { margin: 0; }
    .todo { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-left: 3px solid var(--color-warning); color: inherit; text-decoration: none; }
    .todo:hover strong { text-decoration: underline; }
  `,
})
export class AdminHome implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly userService = inject(UserService);
  private readonly coursService = inject(CoursService);
  private readonly quizService = inject(QuizService);
  private readonly moderation = inject(ModerationForumService);
  private readonly iaService = inject(IaUserService);

  protected readonly aujourdhui = new Date().toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  protected readonly libellesAction = LIBELLES_ACTION;
  protected readonly libellesCible = LIBELLES_CIBLE;
  protected readonly tempsRelatif = tempsRelatif;

  protected readonly etat = signal<Etat>('chargement');
  protected readonly erreur = signal('');
  private readonly utilisateurs = signal<Utilisateur[]>([]);
  private readonly cours = signal<Cours[]>([]);
  protected readonly certificats = signal<Certificat[]>([]);
  protected readonly demandes = signal<DemandeFormateur[]>([]);
  protected readonly services = signal<EtatService[]>(SERVICES.map((s) => ({ nom: s.nom, ok: null })));

  protected readonly etatJournal = signal<Etat>('chargement');
  protected readonly erreurJournal = signal('');
  protected readonly journal = signal<JournalModeration[]>([]);

  protected readonly etatIa = signal<Etat>('chargement');
  protected readonly erreurIa = signal('');
  protected readonly metriques = signal<MetriquesIa | null>(null);

  protected readonly parRole = computed(() => {
    const r: Record<Role, number> = { ETUDIANT: 0, ENSEIGNANT: 0, ADMIN: 0 };
    for (const u of this.utilisateurs()) r[u.role]++;
    return r;
  });
  protected readonly nbPublies = computed(() => this.cours().filter((c) => c.statut === 'PUBLIE').length);
  protected readonly repartition = computed(() => ({
    labels: ROLES.map((r) => ROLE_LABELS[r]),
    series: [{ label: 'Comptes', data: ROLES.map((r) => this.parRole()[r]) }],
  }));

  /** Les 12 derniers mois, du plus ancien au plus récent : [début du mois, libellé]. */
  private readonly mois = (() => {
    const d = new Date();
    return Array.from({ length: 12 }, (_, i) => {
      const m = new Date(d.getFullYear(), d.getMonth() - 11 + i, 1);
      return { debut: m, fin: new Date(m.getFullYear(), m.getMonth() + 1, 1), libelle: m.toLocaleDateString('fr-FR', { month: 'short' }) };
    });
  })();

  protected readonly croissance = computed(() => {
    const cumul = (role: Role) =>
      this.mois.map((m) => this.utilisateurs().filter((u) => u.role === role && new Date(u.dateCreation) < m.fin).length);
    return {
      labels: this.mois.map((m) => m.libelle),
      series: [
        { label: 'Apprenants', data: cumul('ETUDIANT') },
        { label: 'Formateurs', data: cumul('ENSEIGNANT') },
      ],
    };
  });

  protected readonly certificatsParMois = computed(() => ({
    labels: this.mois.map((m) => m.libelle),
    series: [
      {
        label: 'Certificats',
        data: this.mois.map((m) => this.certificats().filter((c) => { const t = new Date(c.dateEmission); return t >= m.debut && t < m.fin; }).length),
      },
    ],
  }));

  ngOnInit(): void {
    this.charger();
    this.chargerJournal();
    this.chargerIa();
    this.verifierServices();
  }

  protected charger(): void {
    this.etat.set('chargement');
    forkJoin({
      utilisateurs: this.userService.findAll(),
      cours: this.coursService.findAll().pipe(catchError(() => of<Cours[]>([]))),
      certificats: this.quizService.tousLesCertificats().pipe(catchError(() => of<Certificat[]>([]))),
      demandes: this.userService.demandesFormateur().pipe(catchError(() => of<DemandeFormateur[]>([]))),
    }).subscribe({
      next: ({ utilisateurs, cours, certificats, demandes }) => {
        this.utilisateurs.set(utilisateurs);
        this.cours.set(cours);
        this.certificats.set(certificats);
        this.demandes.set(
          demandes.filter((d) => d.statut === 'EN_ATTENTE').sort((a, b) => a.dateCreation.localeCompare(b.dateCreation)),
        );
        this.etat.set('ok');
      },
      error: (err) => {
        this.erreur.set(errorMessage(err, 'Impossible de charger les comptes'));
        this.etat.set('erreur');
      },
    });
  }

  private chargerJournal(): void {
    this.moderation.journal({ page: 0 }).subscribe({
      next: (p) => {
        this.journal.set(p.contenu.slice(0, 5));
        this.etatJournal.set('ok');
      },
      error: (err) => {
        this.erreurJournal.set(errorMessage(err, 'Le journal de modération est indisponible'));
        this.etatJournal.set('erreur');
      },
    });
  }

  private chargerIa(): void {
    this.iaService.metriques().subscribe({
      next: (m) => {
        this.metriques.set(m);
        this.etatIa.set('ok');
      },
      error: (err) => {
        this.erreurIa.set(errorMessage(err, 'Les métriques des modèles sont indisponibles'));
        this.etatIa.set('erreur');
      },
    });
  }

  /** Interroge GET /api/{module}/health (public) de chaque service via la gateway et mesure le temps de réponse. */
  private verifierServices(): void {
    SERVICES.forEach((s, i) => {
      const debut = performance.now();
      this.http
        .get(`${API_BASE_URL}/${s.chemin}/health`)
        .pipe(
          map(() => true),
          catchError(() => of(false)),
        )
        .subscribe((ok) => {
          const latence = Math.round(performance.now() - debut);
          this.services.update((liste) => liste.map((x, j) => (j === i ? { nom: x.nom, ok, latence: ok ? latence : undefined } : x)));
        });
    });
  }

  protected pct(v: number | undefined): string {
    return v === undefined || v === null ? '–' : `${Math.round(v * 100)} %`;
  }
}
