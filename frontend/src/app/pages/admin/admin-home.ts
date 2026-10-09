import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';

import { ACTIVITE_MODULES, AUDIT, CROISSANCE, DEMANDES_FORMATEUR, MODELES_IA, QUOTA_IA, REPARTITION_PROFILS, VENTES_MOIS } from '../../core/demo/demo-plus';
import { BwChart, Kpi, PageHeader, ProgressRing } from '../../shared/ui';

/**
 * Tableau de bord du back-office : croissance (apprenants, formateurs), ventes, certificats,
 * demandes de formateurs, profils des apprenants, activité par module, état de l'IA, services
 * et dernières actions du journal d'audit.
 * À brancher : endpoints d'administration (user-service, gateway /actuator, ai-service).
 */
@Component({
  selector: 'app-admin-home',
  imports: [RouterLink, MatIconModule, BwChart, Kpi, PageHeader, ProgressRing],
  template: `
    <bw-page-header title="Vue d’ensemble" subtitle="État de la plateforme au 8 octobre 2026." />

    <div class="grid-4">
      <bw-kpi label="Apprenants" value="214" icon="group" [delta]="18" deltaLabel="ce mois" [trend]="croissance.apprenants" />
      <bw-kpi label="Ventes d’octobre (DT)" value="1 659" icon="payments" [delta]="12" deltaLabel="vs septembre (à date)" [trend]="ventes.montants" />
      <bw-kpi label="Certificats délivrés" value="412" icon="workspace_premium" [delta]="9" deltaLabel="ce mois" />
      <bw-kpi label="Requêtes IA (mois)" [value]="quota.utilise" icon="psychology" [delta]="21" deltaLabel="vs septembre" />
    </div>

    @if (demandes.length) {
      <a class="card todo mt" routerLink="/admin/formateurs">
        <mat-icon aria-hidden="true">how_to_reg</mat-icon>
        <span class="grow"><strong>{{ demandes.length }} demandes de formateur à examiner</strong><span class="muted small"> La plus ancienne date de {{ demandes[demandes.length - 1].quand }}.</span></span>
        <mat-icon aria-hidden="true">chevron_right</mat-icon>
      </a>
    }

    <div class="grid-main mt">
      <section class="card">
        <div class="card-head"><div><h2>Croissance</h2><span class="sub">Comptes cumulés sur 12 mois</span></div></div>
        <div class="card-body"><bw-chart type="line" [labels]="croissance.labels" [series]="seriesCroissance" [height]="260" ariaLabel="Nombre de comptes apprenants et formateurs" /></div>
      </section>
      <section class="card">
        <div class="card-head"><div><h2>Profils des apprenants</h2><span class="sub">Déclarés à l’inscription</span></div></div>
        <div class="card-body"><bw-chart type="doughnut" [labels]="profils.labels" [series]="[{ label: 'Apprenants', data: profils.valeurs }]" [height]="260" ariaLabel="Répartition des apprenants par profil" /></div>
      </section>
    </div>

    <div class="grid-main mt">
      <section class="card">
        <div class="card-head"><div><h2>Activité par module</h2><span class="sub">Actions par jour, cette semaine</span></div></div>
        <div class="card-body"><bw-chart type="stacked" [labels]="activite.labels" [series]="seriesActivite" [height]="260" ariaLabel="Actions par module et par jour" /></div>
      </section>
      <section class="card">
        <div class="card-head"><h2>Services</h2><span class="pill ok">Tous opérationnels</span></div>
        <ul class="svc">
          @for (s of services; track s.nom) {
            <li><span class="pill" [class.ok]="s.ok" [class.warn]="!s.ok">{{ s.ok ? 'OK' : 'Lent' }}</span><span class="small grow">{{ s.nom }}</span><span class="num small muted">{{ s.latence }} ms</span></li>
          }
        </ul>
      </section>
    </div>

    <div class="grid-main mt">
      <section class="card">
        <div class="card-head"><h2>Dernières actions</h2><a routerLink="/admin/audit" class="lnk small">Journal complet</a></div>
        <ul class="svc">
          @for (a of audit; track a.quand) {
            <li><span class="num small muted" style="width: 84px">{{ a.quand }}</span><span class="small grow"><strong>{{ a.qui }}</strong> {{ a.action }} <strong>{{ a.cible }}</strong></span></li>
          }
        </ul>
      </section>
      <section class="card">
        <div class="card-head"><h2>Modèles IA</h2><a routerLink="/admin/ia" class="lnk small">Supervision</a></div>
        <div class="card-body ring">
          <bw-progress-ring [value]="quotaPct" [size]="96" label="Quota IA utilisé" />
          <p class="small muted">du quota mensuel utilisé</p>
        </div>
        <ul class="svc">
          @for (m of modeles; track m.module) {
            <li><span class="small grow">{{ m.module }} : {{ m.usage }}</span><strong class="num small">{{ m.valeur }} %</strong></li>
          }
        </ul>
      </section>
    </div>
  `,
  styles: `
    ul { list-style: none; margin: 0; padding: 0; }
    .svc li { display: flex; align-items: center; gap: var(--space-3); padding: 10px var(--space-5); border-bottom: 1px solid var(--color-border); }
    .svc li:last-child { border-bottom: 0; }
    .grow { flex: 1; }
    .lnk { color: var(--color-bordeaux-600); font-weight: 600; text-decoration: none; }
    .ring { display: flex; align-items: center; gap: var(--space-4); }
    .ring p { margin: 0; }
    .todo { display: flex; align-items: center; gap: var(--space-3); padding: var(--space-3) var(--space-5); border-left: 3px solid var(--color-warning); color: inherit; text-decoration: none; }
    .todo:hover strong { text-decoration: underline; }
  `,
})
export class AdminHome {
  protected readonly croissance = CROISSANCE;
  protected readonly seriesCroissance = [
    { label: 'Apprenants', data: CROISSANCE.apprenants },
    { label: 'Formateurs', data: CROISSANCE.formateurs },
  ];
  protected readonly profils = REPARTITION_PROFILS;
  protected readonly ventes = VENTES_MOIS;
  protected readonly demandes = DEMANDES_FORMATEUR;
  protected readonly activite = ACTIVITE_MODULES;
  protected readonly seriesActivite = [
    { label: 'Cours', data: ACTIVITE_MODULES.cours },
    { label: 'Quiz', data: ACTIVITE_MODULES.quiz },
    { label: 'Forum', data: ACTIVITE_MODULES.forum },
  ];
  protected readonly quota = QUOTA_IA;
  protected readonly quotaPct = Math.round((QUOTA_IA.utilise / QUOTA_IA.limite) * 100);
  protected readonly modeles = MODELES_IA;
  protected readonly audit = AUDIT.slice(0, 5);
  protected readonly services = [
    { nom: 'api-gateway', ok: true, latence: 18 },
    { nom: 'user-service', ok: true, latence: 42 },
    { nom: 'cours-service', ok: true, latence: 55 },
    { nom: 'quiz-service', ok: true, latence: 47 },
    { nom: 'forum-service', ok: true, latence: 51 },
    { nom: 'ai-service', ok: false, latence: 2400 },
  ];
}
