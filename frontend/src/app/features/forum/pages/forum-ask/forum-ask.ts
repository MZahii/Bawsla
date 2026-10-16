import { Component, DestroyRef, OnInit, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, filter, switchMap, tap, catchError, of } from 'rxjs';

import { AuthService } from '../../../../core/auth/auth.service';
import { AiBox, AiLoading, BwButton } from '../../../../shared';
import { PageHeader } from '../../../../shared/ui';
import { ChampAide } from '../../components/champ-aide';
import { ForumEditeur } from '../../components/editeur';
import { REGLES, erreurTexte } from '../../components/validation';
import { messageErreur } from '../../components/outils';
import { AmeliorerResponse, CoursOption, Etiquette, SimilairesResponse, TypeDiscussion } from '../../models';
import { ForumService } from '../../services/forum.service';
import { VigieService } from '../../services/vigie.service';

const MODELE = `**Ce que j'essaie de faire :**

**Ce que j'ai essayé :**

**Le message d'erreur (ou le résultat obtenu) :**
\`\`\`
\`\`\`
`;

interface Brouillon {
  titre: string;
  contenu: string;
  etiquettes: string[];
}

/**
 * Nouvelle question (plan §7.2) : questions proches en direct pendant la saisie du titre, éditeur Markdown avec
 * modèle de question, étiquettes avec autocomplétion, « Améliorer ma question », anonymat, données personnelles
 * signalées avant publication, dialogue « doublon probable », brouillon sauvegardé et restauré.
 */
@Component({
  selector: 'app-forum-ask',
  imports: [FormsModule, RouterLink, MatIconModule, MatSelectModule, MatCheckboxModule, AiBox, AiLoading, BwButton, PageHeader, ForumEditeur, ChampAide],
  templateUrl: './forum-ask.html',
  styleUrl: './forum-ask.scss',
})
export class ForumAsk implements OnInit {
  private readonly forum = inject(ForumService);
  private readonly vigie = inject(VigieService);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly snack = inject(MatSnackBar);
  private readonly detruire = inject(DestroyRef);

  protected readonly modele = MODELE;
  protected readonly base = this.router.url.startsWith('/studio') ? '/studio/forum' : '/app/forum';
  protected readonly apprenant = computed(() => this.auth.role() === 'ETUDIANT');
  protected readonly cours = signal<CoursOption[]>([]);
  protected readonly coursId = signal<number | null>(null);
  protected readonly chapitreId = signal<number | null>(null);
  protected readonly page = signal<number | null>(null);
  protected readonly type = signal<TypeDiscussion>('QUESTION');
  protected readonly titre = signal('');
  protected readonly contenu = signal('');
  protected readonly etiquettes = signal<string[]>([]);
  protected readonly saisieEtiquette = signal('');
  protected readonly suggestionsEtiquettes = signal<Etiquette[]>([]);
  protected readonly anonyme = signal(false);
  protected readonly conversationId = signal<number | null>(null);

  protected readonly similaires = signal<SimilairesResponse | null>(null);
  protected readonly rechercheEnCours = signal(false);
  protected readonly amelioration = signal<AmeliorerResponse | null>(null);
  protected readonly ameliorationEnCours = signal(false);
  protected readonly alertes = signal<string[]>([]);
  protected readonly doublon = signal<string | null>(null);
  protected readonly envoi = signal(false);
  protected readonly erreur = signal<string | null>(null);
  protected readonly restaure = signal(false);

  protected readonly regles = REGLES;
  /** Champs quittés au moins une fois : leur erreur « obligatoire » s'affiche. */
  protected readonly touches = signal<Set<string>>(new Set());
  protected readonly erreurCours = computed(() => (this.coursId() ? null : 'Le cours est obligatoire'));
  protected readonly erreurTitre = computed(() => erreurTexte(REGLES.titre, this.titre()));
  protected readonly erreurContenu = computed(() => erreurTexte(REGLES.contenu, this.contenu()));
  protected readonly erreurEtiquette = computed(() => erreurTexte(REGLES.etiquette, this.saisieEtiquette()));
  protected readonly valide = computed(() => !this.erreurCours() && !this.erreurTitre() && !this.erreurContenu());
  private readonly titres = new Subject<string>();

  constructor() {
    // Brouillon automatique, par cours (localStorage) ; seulement hors publication depuis l'assistant.
    effect(() => {
      const cle = this.cleBrouillon();
      const b: Brouillon = { titre: this.titre(), contenu: this.contenu(), etiquettes: this.etiquettes() };
      if (this.conversationId() === null && (b.titre || b.contenu)) {
        try {
          localStorage.setItem(cle, JSON.stringify(b));
        } catch {
          // stockage plein ou interdit : le brouillon n'est simplement pas gardé
        }
      }
    });
  }

  ngOnInit(): void {
    this.forum.cours().subscribe({ next: (c) => this.cours.set(c), error: () => this.cours.set([]) });
    const q = this.route.snapshot.queryParamMap;
    const nombre = (cle: string) => (q.get(cle) ? Number(q.get(cle)) : null);
    this.coursId.set(nombre('coursId'));
    this.chapitreId.set(nombre('chapitreId'));
    this.page.set(nombre('page'));
    this.conversationId.set(nombre('conversationId'));
    const etat = history.state as { contenu?: string } | null;
    if (q.get('titre')) {
      this.titre.set(q.get('titre')!);
    }
    if (etat?.contenu) {
      this.contenu.set(etat.contenu);
    } else {
      this.restaurer();
    }
    this.titres
      .pipe(
        debounceTime(600),
        distinctUntilChanged(),
        tap((t) => {
          if (t.trim().length < 15 || !this.coursId()) {
            this.similaires.set(null);
          }
        }),
        filter((t) => t.trim().length >= 15 && !!this.coursId()),
        tap(() => this.rechercheEnCours.set(true)),
        switchMap((t) => this.forum.similaires({ coursId: this.coursId()!, texte: t.trim() }).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(this.detruire),
      )
      .subscribe((r) => {
        this.similaires.set(r);
        this.rechercheEnCours.set(false);
      });
    if (this.titre()) {
      this.titres.next(this.titre());
    }
  }

  protected toucher(champ: string): void {
    if (!this.touches().has(champ)) {
      this.touches.set(new Set([...this.touches(), champ]));
    }
  }

  /** Erreur affichée sous le champ : un champ vide n'est signalé qu'une fois quitté. */
  protected visible(champ: string, erreur: string | null, valeur: string | null | undefined): string | null {
    return erreur && (this.touches().has(champ) || `${valeur ?? ''}`.trim()) ? erreur : null;
  }

  protected changerTitre(t: string): void {
    this.titre.set(t);
    this.titres.next(t);
  }

  protected changerCours(id: number): void {
    this.coursId.set(id);
    this.titres.next(`${this.titre()} `);
  }

  // ------------------------------------------------------------ Étiquettes

  protected chercherEtiquettes(q: string): void {
    this.saisieEtiquette.set(q);
    if (q.trim().length < 1) {
      this.suggestionsEtiquettes.set([]);
      return;
    }
    this.forum.etiquettes(this.coursId(), q.trim()).subscribe({
      next: (e) => this.suggestionsEtiquettes.set(e.filter((x) => !this.etiquettes().includes(x.libelle)).slice(0, 6)),
      error: () => this.suggestionsEtiquettes.set([]),
    });
  }

  protected ajouterEtiquette(libelle: string): void {
    const e = libelle.trim().toLowerCase();
    if (e && e.length <= REGLES.etiquette.max && !this.etiquettes().includes(e) && this.etiquettes().length < 5) {
      this.etiquettes.set([...this.etiquettes(), e]);
    }
    this.saisieEtiquette.set('');
    this.suggestionsEtiquettes.set([]);
  }

  protected retirerEtiquette(e: string): void {
    this.etiquettes.set(this.etiquettes().filter((x) => x !== e));
  }

  // ------------------------------------------------------------ Aides

  protected ameliorer(): void {
    if (!this.coursId() || !this.titre().trim()) {
      this.erreur.set('Choisis un cours et écris un titre avant de demander de l’aide.');
      return;
    }
    this.ameliorationEnCours.set(true);
    this.vigie.ameliorer({ coursId: this.coursId()!, titre: this.titre().trim(), contenu: this.contenu() }).subscribe({
      next: (r) => {
        this.amelioration.set(r);
        this.ameliorationEnCours.set(false);
      },
      error: (e) => {
        this.erreur.set(messageErreur(e));
        this.ameliorationEnCours.set(false);
      },
    });
  }

  protected accepterTitre(t: string): void {
    this.changerTitre(t);
    this.amelioration.update((a) => (a ? { ...a, titre: '' } : a));
  }

  protected reponseTrouvee(id: number): void {
    this.forum.reponseTrouvee(id).subscribe({ complete: () => this.router.navigate([this.base, id]), error: () => this.router.navigate([this.base, id]) });
    this.effacerBrouillon();
  }

  // ------------------------------------------------------------ Publication

  /** Données personnelles d'abord (sans IA), puis publication ; doublon probable : confirmation demandée. */
  protected publier(confirmer = false): void {
    if (!this.valide() || this.envoi()) {
      return;
    }
    this.erreur.set(null);
    this.envoi.set(true);
    if (!confirmer && !this.alertes().length) {
      this.forum.verifier(this.titre(), this.contenu()).subscribe({
        next: (v) => {
          if (v.donneesPersonnelles) {
            this.alertes.set(v.alertes);
            this.envoi.set(false);
          } else {
            this.envoyer(false);
          }
        },
        error: () => this.envoyer(false),
      });
      return;
    }
    this.envoyer(confirmer);
  }

  private envoyer(confirmer: boolean): void {
    this.envoi.set(true);
    this.forum
      .publier({
        coursId: this.coursId()!,
        chapitreId: this.chapitreId() ?? undefined,
        page: this.page() ?? undefined,
        type: this.type(),
        titre: this.titre().trim(),
        contenu: this.contenu().trim(),
        etiquettes: this.etiquettes(),
        anonyme: this.anonyme(),
        confirmer,
        conversationId: this.conversationId() ?? undefined,
      })
      .subscribe({
        next: (d) => {
          this.effacerBrouillon();
          this.snack.open('Question publiée. La Vigie prépare une réponse.', 'OK', { duration: 4000 });
          this.router.navigate([this.base, d.id]);
        },
        error: (e) => {
          this.envoi.set(false);
          if (e?.status === 409) {
            this.doublon.set(messageErreur(e));
          } else {
            this.erreur.set(messageErreur(e));
          }
        },
      });
  }

  protected publierQuandMeme(): void {
    this.alertes.set([]);
    this.doublon.set(null);
    this.envoyer(true);
  }

  // ------------------------------------------------------------ Brouillon

  private cleBrouillon(): string {
    return `bawsla.forum.brouillon.${this.coursId() ?? 0}`;
  }

  private restaurer(): void {
    try {
      const b = JSON.parse(localStorage.getItem(this.cleBrouillon()) ?? 'null') as Brouillon | null;
      if (b && (b.titre || b.contenu) && !this.titre()) {
        this.titre.set(b.titre);
        this.contenu.set(b.contenu);
        this.etiquettes.set(b.etiquettes ?? []);
        this.restaure.set(true);
      }
    } catch {
      // brouillon illisible : ignoré
    }
  }

  protected effacerBrouillon(): void {
    try {
      localStorage.removeItem(this.cleBrouillon());
    } catch {
      // rien à faire
    }
    if (this.restaure()) {
      this.restaure.set(false);
    }
  }

  protected toutEffacer(): void {
    this.effacerBrouillon();
    this.titre.set('');
    this.contenu.set('');
    this.etiquettes.set([]);
  }
}
