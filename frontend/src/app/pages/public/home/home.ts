import { Component, DestroyRef, ElementRef, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { COURS } from '../../../core/demo/demo-data';
import { CATEGORIES, FAQ, meta, tuile } from '../../../core/demo/demo-plus';
import { BwButton } from '../../../shared';
import { CourseTile, SectionHead } from '../../../shared/ui';

/**
 * Accueil public (visiteur non connecté) : recherche, domaines, cours populaires, fonctionnement,
 * place de l'IA, certificats, espace formateurs et questions fréquentes.
 * À brancher : les cours affichés viendront d'un endpoint public (voir INTEGRATION.md).
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, RouterLink, MatIconModule, MatExpansionModule, BwButton, CourseTile, SectionHead],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected q = '';
  protected readonly populaires = ['SQL', 'Spring Boot', 'Angular', 'Docker', 'Git'];
  protected readonly categories = CATEGORIES;
  protected readonly cours = COURS.filter((c) => meta(c.id).statut === 'PUBLIE')
    .sort((a, b) => meta(b.id).inscrits - meta(a.id).inscrits)
    .slice(0, 4)
    .map((c) => tuile(c));
  protected readonly images = [meta(3).image, meta(1).image, meta(4).image];
  protected readonly faq = FAQ;
  protected readonly etapes = [
    { icone: 'travel_explore', titre: 'Choisis un cours', texte: 'Gratuit ou payant, par domaine et par niveau. L’IA te propose par où commencer selon ton objectif.' },
    { icone: 'headphones', titre: 'Apprends à ton rythme', texte: 'Vidéo, texte, résumé validé par le formateur et version audio. Aucun calendrier imposé.' },
    { icone: 'quiz', titre: 'Teste-toi', texte: 'Un quiz après chaque chapitre, corrigé tout de suite, avec la notion exacte à revoir.' },
    { icone: 'workspace_premium', titre: 'Obtiens ton certificat', texte: 'Tous les chapitres terminés ? Passe l’examen final et télécharge ton certificat.' },
  ];

  constructor() {
    // Les liens « #enseigner », « #certificat » et « #faq » du menu : la page défile dans la coque, pas dans la fenêtre.
    const sub = inject(ActivatedRoute).fragment.subscribe((f) => this.defiler(f));
    inject(DestroyRef).onDestroy(() => sub.unsubscribe());
  }

  private defiler(f: string | null): void {
    if (!f) return;
    setTimeout(
      () => this.host.nativeElement.querySelector('#' + f)?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      50,
    );
  }

  protected chercher(): void {
    this.router.navigate(['/catalogue'], { queryParams: this.q.trim() ? { q: this.q.trim() } : {} });
  }
}
