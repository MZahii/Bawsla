import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';

import { errorMessage } from '../../../../shared/models/api-response.model';
import { AiBox, EmptyState } from '../../../../shared';
import { Avatar, Kpi, PageHeader, Skeleton } from '../../../../shared/ui';
import { ApprenantsFormateur, LigneApprenant, NiveauRisque } from '../../models/ia.model';
import { AvatarService } from '../../services/avatar.service';
import { IaUserService } from '../../services/ia-user.service';

const LIBELLE: Record<NiveauRisque, string> = { faible: 'Suivi normal', moyen: 'À surveiller', eleve: 'Risque d’échec' };

/**
 * Studio : les apprenants inscrits aux cours du formateur (GET /api/users/formateur/apprenants). Une ligne par
 * inscription (apprenant × cours), avec le risque d'échec prédit par le Random Forest et ses raisons, quand les
 * données de l'apprenant sont complètes. Le risque n'est jamais montré à l'apprenant (DESIGN.md).
 * La liste des inscrits vient de cours-service (contrat 7) : tant qu'elle n'existe pas, la page l'explique.
 */
@Component({
  selector: 'app-apprenants',
  imports: [DatePipe, FormsModule, MatButtonToggleModule, MatIconModule, MatSelectModule, AiBox, EmptyState, Avatar, Kpi, PageHeader, Skeleton],
  templateUrl: './apprenants.html',
  styleUrl: './apprenants.scss',
})
export class Apprenants implements OnInit {
  private readonly api = inject(IaUserService);
  private readonly avatars = inject(AvatarService);
  protected readonly libelle = LIBELLE;

  protected readonly donnees = signal<ApprenantsFormateur | null>(null);
  protected readonly erreur = signal<string | null>(null);
  protected readonly photos = signal<Record<number, string | undefined>>({});
  protected readonly coursChoisi = signal<number | null>(null);
  protected readonly risque = signal<NiveauRisque | 'tous'>('tous');
  protected readonly q = signal('');
  protected readonly ouvert = signal<LigneApprenant | null>(null);

  protected readonly lignes = computed(() => {
    const c = this.coursChoisi();
    const r = this.risque();
    const q = this.q().trim().toLowerCase();
    return (this.donnees()?.lignes ?? []).filter((l) =>
      (!c || l.coursId === c) && (r === 'tous' || l.risque?.niveau === r) && this.nom(l).toLowerCase().includes(q));
  });
  protected readonly nbApprenants = computed(() => new Set((this.donnees()?.lignes ?? []).map((l) => l.etudiantId)).size);
  protected readonly nbRisqueEleve = computed(() =>
    new Set((this.donnees()?.lignes ?? []).filter((l) => l.risque?.niveau === 'eleve').map((l) => l.etudiantId)).size);
  protected readonly progressionMoyenne = computed(() => {
    const p = (this.donnees()?.lignes ?? []).map((l) => l.progression).filter((x): x is number => x !== undefined);
    return p.length ? `${Math.round((p.reduce((a, b) => a + b, 0) / p.length) * 100)} %` : '—';
  });

  ngOnInit(): void {
    this.charger();
  }

  protected charger(): void {
    this.erreur.set(null);
    this.donnees.set(null);
    this.api.apprenantsDuFormateur().subscribe({
      next: (d) => {
        this.donnees.set(d);
        for (const l of d.lignes) {
          this.avatars.url(l.etudiantId, l.avatarVersion).subscribe((url) => this.photos.update((p) => ({ ...p, [l.etudiantId]: url })));
        }
      },
      error: (err) => this.erreur.set(errorMessage(err, 'La liste des apprenants est indisponible')),
    });
  }

  protected nom(l: LigneApprenant): string {
    return `${l.prenom} ${l.nom}`.trim();
  }

  /** 95 min -> « 1,6 h » (format français ; l'application n'a pas de LOCALE_ID français). */
  protected heures(minutes: number | undefined): string {
    return minutes === undefined ? '—' : `${new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 }).format(minutes / 60)} h`;
  }

  protected pct(x: number | undefined): string {
    return x === undefined ? '—' : `${Math.round(x * 100)} %`;
  }
}
