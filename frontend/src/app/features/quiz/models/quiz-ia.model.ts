/** Miroirs des schémas de ai-service (module quiz) : /api/ai/quiz/generer et /api/ai/quiz/remedier. */

export type NiveauBloom = 'MEMORISER' | 'COMPRENDRE' | 'APPLIQUER' | 'ANALYSER' | 'EVALUER' | 'CREER';
export type Difficulte = 'FACILE' | 'INTERMEDIAIRE' | 'AVANCE' | 'DIFFICILE' | 'MASTER';
export type StatutQuestion = 'VALIDEE' | 'REJETEE';

export const NIVEAUX_BLOOM: { code: NiveauBloom; libelle: string }[] = [
  { code: 'MEMORISER', libelle: 'Se souvenir' },
  { code: 'COMPRENDRE', libelle: 'Comprendre' },
  { code: 'APPLIQUER', libelle: 'Appliquer' },
  { code: 'ANALYSER', libelle: 'Analyser' },
  { code: 'EVALUER', libelle: 'Évaluer' },
  { code: 'CREER', libelle: 'Créer' },
];

/** Les 5 niveaux, dans l'ordre. Le résumé reprend la grille envoyée au LLM (prompts/difficultes.py). */
export const DIFFICULTES: { code: Difficulte; libelle: string; resume: string }[] = [
  { code: 'FACILE', libelle: 'Facile', resume: 'Une notion, une phrase, reconnaître une définition ou un fait.' },
  { code: 'INTERMEDIAIRE', libelle: 'Intermédiaire', resume: 'Une notion appliquée à une situation simple ; erreurs de débutant.' },
  { code: 'AVANCE', libelle: 'Avancé', resume: 'Deux notions combinées, mise en situation ou court extrait de code.' },
  { code: 'DIFFICILE', libelle: 'Difficile', resume: 'Cas réaliste avec contrainte ; choisir la meilleure option.' },
  { code: 'MASTER', libelle: 'Master', resume: 'Cas limite ou piège subtil ; 4 choix plausibles pour un expert.' },
];

export function libelleBloom(code: string | null | undefined): string {
  return NIVEAUX_BLOOM.find((n) => n.code === code)?.libelle ?? code ?? '';
}

export function codeBloom(libelle: string): NiveauBloom {
  return NIVEAUX_BLOOM.find((n) => n.libelle === libelle)?.code ?? 'COMPRENDRE';
}

export function libelleDifficulte(code: Difficulte): string {
  return DIFFICULTES.find((d) => d.code === code)?.libelle ?? code;
}

export interface GenererQuizRequest {
  coursId: number;
  theme: string;
  competences: string[];
  niveauxBloom: NiveauBloom[];
  difficulte: Difficulte;
  nbQuestions: number;
}

export interface ChoixQcm {
  texte: string;
  estCorrecte: boolean;
  erreurAssociee?: string;
}

export interface QuestionQcm {
  enonce: string;
  niveauBloom?: NiveauBloom;
  competence?: string;
  choix: ChoixQcm[];
  explication: string;
  statut: StatutQuestion;
  raisonRejet?: string;
}

export interface GenererQuizResponse {
  coursId: number;
  questions: QuestionQcm[];
  nbValidees: number;
  nbRejetees: number;
  versionPrompt: string;
  difficulte: Difficulte;
  fournisseur?: string;
  alertes: string[];
}

/** Aucune donnée d'apprenant : le backend refuse tout champ en plus (400). */
export interface RemedierRequest {
  notion: string;
  niveauBloom: NiveauBloom;
  enonce: string;
  choixDonne: string;
  bonneReponse: string;
  erreurAssociee?: string;
  explication?: string;
}

export interface RemedierResponse {
  notion: string;
  explication: string;
  conseil: string;
  questionRattrapage?: QuestionQcm;
  versionPrompt: string;
  fournisseur?: string;
  alertes: string[];
}
