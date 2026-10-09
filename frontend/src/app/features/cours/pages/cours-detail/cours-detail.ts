import { Component, computed, inject, input, numberAttribute, signal } from '@angular/core';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { COURS, NOTIONS, coursParId, libellePrix } from '../../../../core/demo/demo-data';
import { AVIS, CERTIFICATS, LIBELLE_NIVEAU, REPARTITION_NOTES, dureeTotale, formatDuree, meta, tuile } from '../../../../core/demo/demo-plus';
import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, CourseTile, PageHeader, Rating } from '../../../../shared/ui';

/**
 * Fiche d'un cours (avant de le suivre) : prix, présentation, programme avec examen final,
 * certificat, formateur et avis.
 * Visiteur : le bouton mène à l'inscription. Apprenant non inscrit : « S'inscrire » (cours gratuit)
 * ou « Acheter » (page de paiement). Apprenant inscrit : « Commencer », « Continuer » ou « Revoir ».
 * À brancher : CoursService.findById(id), POST /api/cours/{id}/inscriptions et les avis
 * (à prévoir dans CONTRATS_API.md).
 */
@Component({
  selector: 'app-cours-detail',
  imports: [RouterLink, MatIconModule, MatTabsModule, MatExpansionModule, BwButton, EmptyState, Avatar, Rating, PageHeader, CourseTile],
  templateUrl: './cours-detail.html',
  styleUrl: './cours-detail.scss',
})
export class CoursDetail {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  readonly id = input(0, { transform: numberAttribute });

  protected readonly espace = (this.route.snapshot.data['espace'] as string) ?? 'public';
  protected readonly base = this.espace === 'etudiant' ? '/app/explorer' : '/catalogue';
  protected readonly cours = computed(() => coursParId(this.id()));
  protected readonly m = computed(() => meta(this.id()));
  protected readonly niveau = computed(() => LIBELLE_NIVEAU[this.cours()?.niveau ?? 'DEBUTANT']);
  protected readonly duree = computed(() => formatDuree(this.cours() ? dureeTotale(this.cours()!) : 0));
  protected readonly notions = computed(() => NOTIONS.filter((n) => this.cours()?.notions.includes(n.id)));
  protected readonly similaires = computed(() =>
    COURS.filter((c) => c.id !== this.id() && c.categorie === this.cours()?.categorie).concat(COURS.filter((c) => c.id !== this.id() && c.categorie !== this.cours()?.categorie)).slice(0, 3).map((c) => tuile(c)),
  );

  protected readonly prix = computed(() => libellePrix(this.cours()?.prix ?? 0));
  protected readonly payant = computed(() => (this.cours()?.prix ?? 0) > 0);
  /** Inscription faite pendant la visite (aperçu) ; sinon l'état vient du cours. */
  private readonly inscritIci = signal(false);
  protected readonly inscrit = computed(() => this.espace === 'etudiant' && (this.cours()?.inscrit || this.inscritIci()));
  protected readonly certificat = computed(() => CERTIFICATS.find((c) => c.coursId === this.id()));

  protected readonly cta = computed(() => {
    const c = this.cours();
    if (this.espace !== 'etudiant' || !c) {
      return { label: this.payant() ? `S’inscrire et acheter, ${this.prix()}` : 'S’inscrire gratuitement', link: ['/register'] as unknown[], params: { cours: this.id() } };
    }
    if (!this.inscrit()) {
      return this.payant()
        ? { label: `Acheter le cours, ${this.prix()}`, link: ['/app/paiement', c.id], params: {} }
        : { label: 'S’inscrire au cours', link: null, params: {} };
    }
    if (c.progression >= 100) return { label: 'Revoir le cours', link: ['/app/cours', c.id, 'lecture'], params: {} };
    return { label: c.progression > 0 ? 'Continuer le cours' : 'Commencer le cours', link: ['/app/cours', c.id, 'lecture'], params: {} };
  });

  /** Inscription à un cours gratuit : un clic, puis on ouvre le premier chapitre. */
  protected inscrire(): void {
    this.inscritIci.set(true);
    this.router.navigate(['/app/cours', this.id(), 'lecture']);
  }

  protected readonly apprendre = [
    'Comprendre les notions clés du cours avec des exemples concrets',
    'Appliquer ce que tu apprends dans des exercices et des quiz',
    'Savoir quand utiliser chaque technique dans un vrai projet',
    'Repérer les erreurs fréquentes et les corriger',
  ];

  protected readonly repartition = REPARTITION_NOTES;

  /** Avis du cours ; à défaut (aperçu), ceux du cours 1 pour montrer la mise en page. */
  protected readonly avis = computed(() => {
    const l = AVIS.filter((a) => a.coursId === this.id());
    return (l.length ? l : AVIS.filter((a) => a.coursId === 1)).slice(0, 4);
  });
}
