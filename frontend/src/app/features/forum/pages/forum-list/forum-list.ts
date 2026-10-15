import { Component, DestroyRef, ElementRef, HostListener, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { BwButton, EmptyState } from '../../../../shared';
import { Avatar, PageHeader, Skeleton } from '../../../../shared/ui';
import { DiscussionLigne } from '../../components/discussion-ligne';
import { messageErreur } from '../../components/outils';
import {
  ClassementEntree,
  CoursOption,
  DiscussionResume,
  DiscussionsFiltres,
  FiltreDiscussions,
  PourToi,
  Reputation,
  TriDiscussions,
} from '../../models';
import { ForumService } from '../../services/forum.service';

const TAILLE = 20;

/**
 * Accueil du forum (plan §7.1) : recherche sémantique (bascule « Mots exacts »), filtres reflétés dans l'URL,
 * annonces épinglées en tête, « Charger plus », colonne « Pour toi », questions sans réponse à aider, classement
 * de la semaine, ma carte, et le bouton « Demander au cours ». Raccourcis : / recherche, n nouvelle question,
 * j / k fil suivant / précédent, ? aide.
 */
@Component({
  selector: 'app-forum-list',
  imports: [FormsModule, RouterLink, MatIconModule, MatSelectModule, MatSlideToggleModule, BwButton, EmptyState, Avatar, PageHeader, Skeleton, DiscussionLigne],
  templateUrl: './forum-list.html',
  styleUrl: './forum-list.scss',
})
export class ForumList implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly detruire = inject(DestroyRef);
  private readonly hote: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly champ = viewChild<ElementRef<HTMLInputElement>>('champ');

  protected readonly statuts = [
    { code: '', libelle: 'Toutes' },
    { code: 'SANS_REPONSE', libelle: 'Sans réponse' },
    { code: 'OUVERTE', libelle: 'Ouvertes' },
    { code: 'RESOLUE', libelle: 'Résolues' },
  ];
  protected readonly mesFiltres: { code: FiltreDiscussions | ''; libelle: string; icone: string }[] = [
    { code: '', libelle: 'Tout le forum', icone: 'forum' },
    { code: 'miennes', libelle: 'Mes questions', icone: 'person' },
    { code: 'repondues', libelle: 'Mes réponses', icone: 'chat' },
    { code: 'suivies', libelle: 'Suivies', icone: 'notifications_active' },
    { code: 'favoris', libelle: 'Favoris', icone: 'bookmark' },
    { code: 'non-lues', libelle: 'Non lues', icone: 'mark_unread_chat_alt' },
  ];
  protected readonly tris: { code: TriDiscussions; libelle: string }[] = [
    { code: 'activite', libelle: 'Activité récente' },
    { code: 'recentes', libelle: 'Nouvelles' },
    { code: 'votes', libelle: 'Votes' },
    { code: 'sans-reponse', libelle: 'Sans réponse d’abord' },
    { code: 'pertinence', libelle: 'Pertinence' },
  ];

  protected readonly cours = signal<CoursOption[]>([]);
  protected readonly filtres = signal<DiscussionsFiltres>({});
  protected readonly saisie = signal('');
  protected readonly discussions = signal<DiscussionResume[]>([]);
  protected readonly total = signal(0);
  protected readonly suivante = signal(false);
  protected readonly chargement = signal(true);
  protected readonly erreur = signal<string | null>(null);
  protected readonly lexical = computed(() => this.filtres().mode === 'lexical');
  protected readonly pourToi = signal<PourToi | null>(null);
  protected readonly sansReponse = signal<DiscussionResume[]>([]);
  protected readonly classement = signal<ClassementEntree[]>([]);
  protected readonly reputation = signal<Reputation | null>(null);
  protected readonly aide = signal(false);
  protected readonly prochainBadge = computed(() =>
    this.reputation()?.badges.filter((b) => !b.obtenu).sort((a, b) => b.progression / b.objectif - a.progression / a.objectif)[0],
  );

  ngOnInit(): void {
    this.forum.cours().subscribe({ next: (c) => this.cours.set(c), error: () => this.cours.set([]) });
    this.forum.reputation().subscribe({ next: (r) => this.reputation.set(r), error: () => this.reputation.set(null) });
    this.forum.pourMoi().subscribe({ next: (p) => this.pourToi.set(p), error: () => this.pourToi.set(null) });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.detruire)).subscribe((q) => {
      const f: DiscussionsFiltres = {
        coursId: q.get('coursId') ? Number(q.get('coursId')) : undefined,
        statut: (q.get('statut') as DiscussionsFiltres['statut']) ?? undefined,
        filtre: (q.get('filtre') as FiltreDiscussions) ?? undefined,
        tri: (q.get('tri') as TriDiscussions) ?? undefined,
        etiquette: q.get('etiquette') ?? undefined,
        q: q.get('q') ?? undefined,
        mode: q.get('mode') === 'lexical' ? 'lexical' : undefined,
      };
      this.filtres.set(f);
      this.saisie.set(f.q ?? '');
      this.charger(false);
      this.chargerColonne(f.coursId);
    });
  }

  /** Met à jour l'URL (lien partageable) ; le chargement suit le changement d'URL. */
  protected changer(modif: Partial<DiscussionsFiltres>): void {
    const f = { ...this.filtres(), ...modif };
    if (modif.q !== undefined && f.q && !modif.tri) {
      f.tri = 'pertinence';
    }
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        coursId: f.coursId ?? null,
        statut: f.statut || null,
        filtre: f.filtre || null,
        tri: f.tri || null,
        etiquette: f.etiquette || null,
        q: f.q || null,
        mode: f.mode === 'lexical' ? 'lexical' : null,
      },
      replaceUrl: true,
    });
  }

  protected chercher(): void {
    this.changer({ q: this.saisie().trim() || undefined });
  }

  protected poser(): void {
    this.router.navigate(['/app/forum/nouvelle'], {
      queryParams: { titre: this.saisie().trim() || null, coursId: this.filtres().coursId ?? null },
    });
  }

  protected reinitialiser(): void {
    this.saisie.set('');
    this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }

  protected chargerPlus(): void {
    this.charger(true);
  }

  protected toutMarquerLu(): void {
    this.forum.toutMarquerLu(this.filtres().coursId ?? null).subscribe(() => this.charger(false));
  }

  private charger(suite: boolean): void {
    const page = suite ? Math.ceil(this.discussions().length / TAILLE) : 0;
    this.chargement.set(true);
    this.erreur.set(null);
    this.forum.lister({ ...this.filtres(), page, taille: TAILLE }).subscribe({
      next: (p) => {
        this.discussions.set(suite ? [...this.discussions(), ...p.contenu] : p.contenu);
        this.total.set(p.total);
        this.suivante.set(p.suivante);
        this.chargement.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e, 'Le forum ne répond pas pour le moment.'));
        this.chargement.set(false);
      },
    });
  }

  private chargerColonne(coursId?: number): void {
    this.forum.lister({ coursId, statut: 'SANS_REPONSE', tri: 'recentes', taille: 4 }).subscribe({
      next: (p) => this.sansReponse.set(p.contenu),
      error: () => this.sansReponse.set([]),
    });
    if (coursId) {
      this.forum.classement(coursId).subscribe({ next: (c) => this.classement.set(c.slice(0, 5)), error: () => this.classement.set([]) });
    } else {
      this.classement.set([]);
    }
  }

  // ------------------------------------------------------------ Raccourcis clavier

  @HostListener('document:keydown', ['$event'])
  protected raccourci(e: KeyboardEvent): void {
    const cible = e.target as HTMLElement;
    if (e.ctrlKey || e.metaKey || e.altKey || ['INPUT', 'TEXTAREA', 'SELECT'].includes(cible.tagName) || cible.isContentEditable) {
      return;
    }
    if (e.key === '/') {
      e.preventDefault();
      this.champ()?.nativeElement.focus();
    } else if (e.key === 'n') {
      this.poser();
    } else if (e.key === '?') {
      this.aide.set(!this.aide());
    } else if (e.key === 'j' || e.key === 'k') {
      const titres = Array.from(this.hote.nativeElement.querySelectorAll<HTMLAnchorElement>('a.titre'));
      const i = titres.indexOf(document.activeElement as HTMLAnchorElement);
      const suivant = e.key === 'j' ? Math.min(titres.length - 1, i + 1) : Math.max(0, i - 1);
      titres[suivant]?.focus();
    }
  }
}
