import { AfterViewInit, Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { CATEGORIES, FORMATEUR_DEFAUT, IMAGE_DEFAUT, LIBELLE_NIVEAU, formatDuree, libelleMatiere, libellePrix } from '../../../features/cours/models/cours.constantes';
import { Cours } from '../../../features/cours/models/cours.model';
import { CoursImageService } from '../../../features/cours/services/cours-image.service';
import { CoursService } from '../../../features/cours/services/cours.service';
import { FormateurService } from '../../../features/cours/services/formateur.service';
import { BwButton, EmptyState } from '../../../shared';
import { CourseTile, CourseTileData, SectionHead, Skeleton } from '../../../shared/ui';

/** Ce que fait l'IA sur Bawsla : une ligne par module, sans promesse que l'application ne tient pas. */
const CAPACITES_IA = [
  { icone: 'quiz', module: 'Quiz', titre: 'Des quiz générés depuis le cours', texte: 'Le formateur choisit les chapitres et les niveaux de Bloom ; chaque question est vérifiée par plusieurs générations avant de lui être proposée.' },
  { icone: 'psychology', module: 'Quiz', titre: 'Une remédiation sur tes erreurs', texte: 'Après un quiz raté, l’IA t’explique les notions manquées et te donne des questions d’entraînement nouvelles.' },
  { icone: 'headphones', module: 'Cours', titre: 'Résumé, fiche et version audio', texte: 'Chaque chapitre a son résumé relu par le formateur, une fiche de révision et une lecture à voix haute.' },
  { icone: 'forum', module: 'Forum', titre: 'La Vigie répond en citant le cours', texte: 'Pose ta question : la réponse s’appuie sur les passages du cours et indique d’où elle vient.' },
  { icone: 'explore', module: 'Progression', titre: 'Ta boussole personnelle', texte: 'Cours recommandés, conseil et plan de la semaine, calculés à partir de tes résultats, jamais de ton nom.' },
  { icone: 'workspace_premium', module: 'Certificat', titre: 'Un certificat vérifiable', texte: 'Réussis l’examen final : ton certificat porte un numéro unique que n’importe qui peut vérifier.' },
];

const FAQ = [
  { q: 'Bawsla est-il gratuit ?', r: 'Oui : l’inscription est gratuite et la plupart des cours aussi, examen final et certificat compris. Quand un cours est payant, son prix est affiché sur sa fiche.' },
  { q: 'À qui s’adresse Bawsla ?', r: 'À tout le monde : étudiants, professionnels, personnes en reconversion ou simples curieux. Pas de classe ni de calendrier imposé : tu choisis tes cours et tu avances à ton rythme.' },
  { q: 'Comment j’obtiens un certificat ?', r: 'Termine les chapitres d’un cours : l’examen final se débloque. Atteins le score demandé et ton certificat est créé tout de suite, avec un numéro que n’importe qui peut vérifier sur le site.' },
  { q: 'Comment l’IA choisit-elle les cours qu’elle me recommande ?', r: 'À partir de tes centres d’intérêt, des cours que tu suis et de tes résultats aux quiz. L’IA ne reçoit que des chiffres : jamais ton nom ni ton email.' },
  { q: 'Les réponses de l’IA sont-elles fiables ?', r: 'Elles sont toujours signalées comme générées par l’IA et citent le passage du cours utilisé. Les résumés et les quiz générés sont relus par le formateur avant d’être publiés.' },
  { q: 'Je veux enseigner, comment publier un cours ?', r: 'Crée un compte et demande à devenir formateur en présentant ton domaine. Une fois ta demande acceptée, tu publies tes cours depuis ton studio.' },
];

/**
 * Accueil public (visiteur non connecté) : recherche, vrais cours du catalogue (cours-service, lecture publique
 * via la gateway), chiffres calculés sur ce catalogue, place de l'IA, certificats, espace formateurs et FAQ.
 * Les sections apparaissent au défilement (IntersectionObserver + CSS), sauf si l'utilisateur limite les animations.
 */
@Component({
  selector: 'app-home',
  imports: [FormsModule, RouterLink, MatIconModule, MatExpansionModule, BwButton, EmptyState, CourseTile, SectionHead, Skeleton],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements AfterViewInit {
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly destroyRef = inject(DestroyRef);
  private readonly coursService = inject(CoursService);
  private readonly formateurs = inject(FormateurService);
  private readonly images = inject(CoursImageService);

  protected q = '';
  protected readonly populaires = ['Java', 'SQL', 'Angular', 'Mathématiques', 'Docker'];
  protected readonly capacites = CAPACITES_IA;
  protected readonly faq = FAQ;
  protected readonly collage = ['assets/demo/cours/donnees.jpg', 'assets/demo/cours/frontend.jpg', 'assets/demo/cours/backend.jpg'];
  protected readonly etapes = [
    { icone: 'travel_explore', titre: 'Choisis un cours', texte: 'Par domaine et par niveau. Ta boussole te propose par où commencer selon tes centres d’intérêt.' },
    { icone: 'menu_book', titre: 'Apprends à ton rythme', texte: 'PDF du cours, résumé validé par le formateur, fiche de révision et version audio.' },
    { icone: 'quiz', titre: 'Teste-toi', texte: 'Des quiz corrigés tout de suite, avec la notion exacte à revoir et une remédiation par l’IA.' },
    { icone: 'workspace_premium', titre: 'Obtiens ton certificat', texte: 'Réussis l’examen final et télécharge un certificat vérifiable par son numéro.' },
  ];

  protected readonly etat = signal<'chargement' | 'ok' | 'erreur'>('chargement');
  private readonly cours = signal<Cours[]>([]);
  private readonly noms = signal<Record<number, string>>({});
  private readonly imagesCours = signal<Record<number, string>>({});

  /** Les quatre cours les plus récents (aucune popularité n'est encore mesurée côté serveur). */
  protected readonly vitrine = computed<CourseTileData[]>(() =>
    [...this.cours()]
      .sort((a, b) => (b.dateCreation ?? '').localeCompare(a.dateCreation ?? ''))
      .slice(0, 4)
      .map((c) => ({
        id: c.id,
        titre: c.titre,
        description: c.description ?? undefined,
        categorie: libelleMatiere(c.categorie, c.categoriePrecision),
        niveau: c.niveau ? LIBELLE_NIVEAU[c.niveau] : '',
        enseignant: this.noms()[c.enseignantId] ?? FORMATEUR_DEFAUT,
        image: this.imagesCours()[c.id] ?? IMAGE_DEFAUT,
        prix: libellePrix(c.prix),
        chapitres: c.nbChapitres,
        duree: formatDuree(c.dureeTotaleMinutes) || undefined,
      })),
  );

  /** Domaines qui ont au moins un cours publié, avec leur nombre de cours. */
  protected readonly domaines = computed(() => {
    const parNom = new Map<string, number>();
    for (const c of this.cours()) if (c.categorie) parNom.set(c.categorie, (parNom.get(c.categorie) ?? 0) + 1);
    return [...parNom.entries()]
      .map(([nom, nb]) => ({ nom, nb, icone: CATEGORIES.find((x) => x.nom === nom)?.icone ?? 'school' }))
      .sort((a, b) => b.nb - a.nb);
  });

  /** Chiffres calculés sur le vrai catalogue : rien d'inventé. */
  protected readonly chiffres = computed(() => {
    const l = this.cours();
    const gratuits = l.filter((c) => !(c.prix > 0)).length;
    return [
      { valeur: l.length, libelle: l.length > 1 ? 'cours publiés' : 'cours publié' },
      { valeur: this.domaines().length, libelle: this.domaines().length > 1 ? 'domaines' : 'domaine' },
      { valeur: l.reduce((t, c) => t + (c.nbChapitres ?? 0), 0), libelle: 'chapitres à suivre' },
      { valeur: gratuits, libelle: gratuits > 1 ? 'cours gratuits' : 'cours gratuit' },
    ];
  });

  constructor() {
    // Les liens « #enseigner », « #certificat » et « #faq » du menu : la page défile dans la coque, pas dans la fenêtre.
    const sub = inject(ActivatedRoute).fragment.subscribe((f) => this.defiler(f));
    this.destroyRef.onDestroy(() => sub.unsubscribe());
    this.charger();
  }

  ngAfterViewInit(): void {
    const racine: HTMLElement = this.host.nativeElement;
    const sansAnimation = typeof IntersectionObserver === 'undefined' || matchMedia('(prefers-reduced-motion: reduce)').matches;
    const obs = sansAnimation ? null : new IntersectionObserver(
      (entrees) => entrees.filter((e) => e.isIntersecting).forEach((e) => { e.target.classList.add('vu'); obs!.unobserve(e.target); }),
      { threshold: 0.12 },
    );
    // Les cours et les chiffres arrivent après le premier rendu : on surveille aussi les éléments ajoutés ensuite.
    const suivre = () =>
      racine.querySelectorAll<HTMLElement>('[data-reveal]:not(.vu):not([data-suivi])').forEach((el) => {
        el.setAttribute('data-suivi', '');
        if (obs) obs.observe(el); else el.classList.add('vu');
      });
    suivre();
    const mut = new MutationObserver(suivre);
    mut.observe(racine, { childList: true, subtree: true });
    this.destroyRef.onDestroy(() => { mut.disconnect(); obs?.disconnect(); });
  }

  protected charger(): void {
    this.etat.set('chargement');
    this.coursService.findAll().subscribe({
      next: (liste) => {
        const publies = liste.filter((c) => !c.statut || c.statut === 'PUBLIE');
        this.cours.set(publies);
        this.etat.set('ok');
        for (const id of new Set(publies.map((c) => c.enseignantId))) {
          this.formateurs.nom(id).subscribe((nom) => this.noms.update((n) => ({ ...n, [id]: nom })));
        }
        for (const c of publies.slice(0, 8)) {
          this.images.url(c).subscribe((url) => this.imagesCours.update((m) => ({ ...m, [c.id]: url })));
        }
      },
      error: () => this.etat.set('erreur'),
    });
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
