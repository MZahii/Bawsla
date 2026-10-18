import { Component, computed, inject, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { enregistrerOnboarding } from '../../core/profil/onboarding';
import { BwButton, Logo } from '../../shared';
import { CATEGORIES } from '../../features/cours/models/cours.constantes';
import { DOMAINES_PARCOURS, OBJECTIFS_PARCOURS, PROFILS_PARCOURS, TEMPS_PARCOURS } from '../../features/user/models/parcours.constantes';
import { UserService } from '../../features/user/services/user.service';

/**
 * Questionnaire d'accueil de l'apprenant, juste après l'inscription : 4 questions, toutes facultatives
 * (qui es-tu, ton objectif, ce qui t'intéresse, ton temps par semaine). Les réponses servent aux
 * premières recommandations de l'IA. « Passer » mène directement à l'espace apprenant.
 */
@Component({
  selector: 'app-onboarding',
  imports: [MatIconModule, BwButton, Logo],
  templateUrl: './onboarding.html',
  styleUrl: './onboarding.scss',
})
export class Onboarding {
  private readonly router = inject(Router);
  private readonly cours = inject(ActivatedRoute).snapshot.queryParamMap.get('cours');
  private readonly auth = inject(AuthService);
  private readonly users = inject(UserService);
  protected readonly prenom = computed(() => this.auth.currentUser()?.prenom ?? '');

  protected readonly etapes = ['Qui es-tu ?', 'Ton objectif', 'Ce qui t’intéresse', 'Ton rythme'];
  protected readonly etape = signal(0);
  protected readonly o = { profils: PROFILS_PARCOURS, objectifs: OBJECTIFS_PARCOURS, temps: TEMPS_PARCOURS };
  /** Les domaines acceptés par user-service, avec l'icône de la matière correspondante du catalogue. */
  protected readonly categories = DOMAINES_PARCOURS.map((nom) => ({ nom, icone: CATEGORIES.find((c) => c.nom === nom)?.icone ?? 'school' }));

  protected readonly profil = signal<string | null>(null);
  protected readonly objectif = signal<string | null>(null);
  protected readonly domaines = signal<string[]>([]);
  protected readonly temps = signal<string | null>(null);

  protected basculerDomaine(d: string): void {
    this.domaines.update((l) => (l.includes(d) ? l.filter((x) => x !== d) : [...l, d]));
  }

  protected suivant(): void {
    if (this.etape() < this.etapes.length - 1) this.etape.update((e) => e + 1);
    else this.terminer(false);
  }

  protected terminer(passe: boolean): void {
    const reponses = { profil: this.profil(), objectif: this.objectif(), domaines: this.domaines(), temps: this.temps() };
    enregistrerOnboarding({ ...reponses, passe });
    // Réponses aussi enregistrées par user-service (« Mon compte » et module IA). Un échec ne bloque pas l'accueil.
    if (!passe) this.users.enregistrerMonParcours(reponses).subscribe({ error: () => undefined });
    this.router.navigateByUrl(this.cours ? `/app/explorer/${this.cours}` : '/app');
  }
}
