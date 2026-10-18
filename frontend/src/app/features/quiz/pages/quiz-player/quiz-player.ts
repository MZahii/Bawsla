import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, inject, input, numberAttribute, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AiBox, AiLoading, Badge, BwButton, EmptyState, Route } from '../../../../shared';
import { Correction, Passage, RattrapageResultat, Remediation, TentativeResultat } from '../../models/quiz.model';
import { libelleBloom } from '../../models/quiz-ia.model';
import { QuizService } from '../../services/quiz.service';

type Phase = 'chargement' | 'introuvable' | 'question' | 'envoi' | 'resultat';
type EtatRemediation = 'inactif' | 'chargement' | 'pret' | 'erreur';

/** Une erreur de la tentative, avec les textes des deux réponses. */
interface Erreur {
  numero: number;
  correction: Correction;
  donnee: string;
  bonne: string;
}

/**
 * Passage d'un quiz : une question par écran, puis la tentative est envoyée à quiz-service qui la corrige
 * (le front ne connaît jamais les bonnes réponses avant). Résultat, erreurs et remédiation IA : quiz-service
 * appelle l'IA via la gateway avec la seule question ratée, rien sur l'apprenant, et garde le résultat.
 */
@Component({
  selector: 'app-quiz-player',
  imports: [RouterLink, MatIconModule, AiBox, AiLoading, Badge, BwButton, EmptyState, Route],
  templateUrl: './quiz-player.html',
  styleUrl: './quiz-player.scss',
})
export class QuizPlayer {
  private readonly quizService = inject(QuizService);

  readonly id = input(0, { transform: numberAttribute });

  protected readonly passage = signal<Passage | null>(null);
  protected readonly quiz = computed(() => this.passage()?.quiz);
  protected readonly questions = computed(() => this.passage()?.questions ?? []);
  protected readonly lettres = ['A', 'B', 'C', 'D', 'E', 'F'];
  protected readonly bloom = libelleBloom;

  protected readonly phase = signal<Phase>('chargement');
  protected readonly erreurEnvoi = signal('');
  protected readonly index = signal(0);
  /** Choix de l'apprenant par question (identifiant d'option). */
  protected readonly reponses = signal<(number | null)[]>([]);
  protected readonly resultat = signal<TentativeResultat | null>(null);

  protected readonly question = computed(() => this.questions()[this.index()]);
  protected readonly choix = computed(() => this.reponses()[this.index()] ?? null);
  protected readonly derniere = computed(() => this.index() === this.questions().length - 1);
  /** Tentatives encore possibles après celle qui vient d'être corrigée ; null = illimité. */
  protected readonly restantesApres = computed(() => {
    const r = this.passage()?.tentativesRestantes;
    return r == null ? null : Math.max(0, r - 1);
  });

  /** Itinéraire : questions répondues, puis ratées une fois la correction reçue. */
  protected readonly etapes = computed(() => {
    const corrections = new Map(this.resultat()?.corrections.map((c) => [c.questionId, c]));
    return this.questions().map((q, i) => ({
      titre: `Question ${i + 1}`,
      fait: this.reponses()[i] != null || corrections.has(q.id),
      rate: corrections.get(q.id)?.correcte === false,
    }));
  });

  protected readonly erreurs = computed<Erreur[]>(() => {
    const r = this.resultat();
    if (!r) return [];
    const textes = new Map(this.questions().flatMap((q) => q.choix.map((c) => [c.id, c.texte] as const)));
    return r.corrections
      .map((c, i) => ({
        numero: i + 1,
        correction: c,
        donnee: c.optionChoisieId != null ? (textes.get(c.optionChoisieId) ?? '') : 'Pas de réponse',
        bonne: textes.get(c.optionCorrecteId) ?? '',
      }))
      .filter((e) => !e.correction.correcte);
  });

  protected readonly mention = computed(() => {
    const r = this.resultat();
    if (!r) return '';
    if (r.pourcentage === 100) return 'Parfait, tu maîtrises ce quiz.';
    if (r.reussie) return 'Quiz réussi. Encore un petit effort sur les points ci-dessous.';
    if (r.pourcentage >= 40) return `Tu es sur la bonne route : il faut ${r.seuilReussite} % pour valider. Revois les notions ci-dessous.`;
    return 'Ce quiz révèle un point à retravailler. C’est exactement à ça qu’il sert.';
  });

  // Remédiation de l'erreur sélectionnée. La bonne réponse du rattrapage n'est révélée qu'après validation.
  protected readonly selection = signal<Erreur | null>(null);
  protected readonly etatRemediation = signal<EtatRemediation>('inactif');
  protected readonly remediation = signal<Remediation | null>(null);
  protected readonly erreurIa = signal('');
  protected readonly choixRattrapage = signal<number | null>(null);
  protected readonly verdict = signal<RattrapageResultat | null>(null);
  /** Secondes écoulées depuis le lancement de la remédiation (diagnostic puis vérification : 20 à 60 s). */
  protected readonly attente = signal(0);
  protected readonly messageAttente = computed(() => {
    const s = this.attente();
    const etape = s < 15
      ? 'Étape 1 sur 2 : l’IA analyse ta réponse pas à pas…'
      : s < 60
        ? 'Étape 2 sur 2 : une seconde lecture vérifie la question de rattrapage…'
        : 'L’IA est très sollicitée, encore un instant…';
    return `${etape} (${s} s)`;
  });
  private demande?: Subscription;
  private chrono?: ReturnType<typeof setInterval>;

  constructor() {
    effect(() => this.charger(this.id()));
    inject(DestroyRef).onDestroy(() => this.arreterAttente());
  }

  protected choisir(optionId: number): void {
    if (this.phase() !== 'question') return;
    this.reponses.update((r) => r.map((x, i) => (i === this.index() ? optionId : x)));
  }

  protected precedente(): void {
    this.index.update((i) => Math.max(0, i - 1));
  }

  protected suivante(): void {
    if (this.derniere()) {
      this.soumettre();
    } else {
      this.index.update((i) => i + 1);
    }
  }

  protected recommencer(): void {
    this.charger(this.id());
  }

  /** Change l'erreur travaillée sans appeler l'IA : l'apprenant lance la remédiation lui-même. */
  protected selectionner(e: Erreur): void {
    this.arreterAttente();
    this.selection.set(e);
    this.etatRemediation.set('inactif');
    this.remediation.set(null);
    this.erreurIa.set('');
    this.choixRattrapage.set(null);
    this.verdict.set(null);
  }

  protected remedier(e: Erreur): void {
    if (this.etatRemediation() === 'chargement') return;
    this.arreterAttente();
    this.selection.set(e);
    this.etatRemediation.set('chargement');
    this.remediation.set(null);
    this.erreurIa.set('');
    this.choixRattrapage.set(null);
    this.verdict.set(null);
    this.attente.set(0);
    this.chrono = setInterval(() => this.attente.update((s) => s + 1), 1000);
    this.demande = this.quizService.remedier(e.correction.reponseId).subscribe({
      next: (r) => {
        this.arreterAttente();
        this.remediation.set(r);
        this.etatRemediation.set('pret');
      },
      error: (err: HttpErrorResponse) => {
        this.arreterAttente();
        this.erreurIa.set(err.error?.message ?? 'L’assistant IA est indisponible pour le moment.');
        this.etatRemediation.set('erreur');
      },
    });
  }

  /** Bouton Annuler de bw-ai-loading : on abandonne la requête et on revient à la correction. */
  protected annulerRemediation(): void {
    this.arreterAttente();
    this.etatRemediation.set('inactif');
  }

  private arreterAttente(): void {
    this.demande?.unsubscribe();
    this.demande = undefined;
    clearInterval(this.chrono);
  }

  protected choisirRattrapage(i: number): void {
    if (!this.verdict()) this.choixRattrapage.set(i);
  }

  protected validerRattrapage(): void {
    const r = this.remediation();
    const i = this.choixRattrapage();
    if (!r || i === null) return;
    this.quizService.rattrapage(r.notionRateeId, i).subscribe({
      next: (v) => this.verdict.set(v),
      error: (err: HttpErrorResponse) => this.erreurIa.set(err.error?.message ?? 'Correction impossible.'),
    });
  }

  protected etatOptionRattrapage(i: number): 'bonne' | 'fausse' | 'choisie' | '' {
    const v = this.verdict();
    if (v) {
      if (i === v.indexBonne) return 'bonne';
      if (i === this.choixRattrapage()) return 'fausse';
      return '';
    }
    return i === this.choixRattrapage() ? 'choisie' : '';
  }

  private soumettre(): void {
    const p = this.passage();
    if (!p) return;
    this.phase.set('envoi');
    this.erreurEnvoi.set('');
    this.quizService
      .soumettre(p.quiz.id, { reponses: p.questions.map((q, i) => ({ questionId: q.id, optionId: this.reponses()[i] ?? null })) })
      .subscribe({
        next: (r) => {
          this.resultat.set(r);
          this.phase.set('resultat');
          const premiere = this.erreurs()[0];
          if (premiere) this.selection.set(premiere);
        },
        error: (err: HttpErrorResponse) => {
          this.erreurEnvoi.set(err.error?.message ?? 'Envoi impossible, réessaie.');
          this.phase.set('question');
        },
      });
  }

  private charger(id: number): void {
    this.arreterAttente();
    this.phase.set('chargement');
    this.resultat.set(null);
    this.index.set(0);
    this.selection.set(null);
    this.etatRemediation.set('inactif');
    this.remediation.set(null);
    this.verdict.set(null);
    this.erreurEnvoi.set('');
    if (!id) {
      this.phase.set('introuvable');
      return;
    }
    this.quizService.passage(id).subscribe({
      next: (p) => {
        this.passage.set(p);
        this.reponses.set(p.questions.map(() => null));
        this.phase.set('question');
      },
      error: () => this.phase.set('introuvable'),
    });
  }
}
