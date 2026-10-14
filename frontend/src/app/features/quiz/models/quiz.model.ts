/** Miroirs des DTO de quiz-service. */
import { Difficulte, NiveauBloom } from './quiz-ia.model';

export type TypeQuiz = 'QUIZ' | 'EXAMEN';
export type StatutQuiz = 'BROUILLON' | 'PUBLIE';

export interface Quiz {
  id: number;
  titre: string;
  description?: string;
  coursId: number;
  enseignantId: number;
  type: TypeQuiz;
  statut: StatutQuiz;
  theme?: string;
  matiere?: string;
  difficulte?: Difficulte;
  seuilReussite: number;
  /** 0 = illimité. */
  tentativesMax: number;
  dureeMinutes: number;
  nbQuestions: number;
  dateCreation: string;
  dateModification?: string;
  datePublication?: string;
}

export interface OptionRequest {
  texte: string;
  estCorrecte: boolean;
  erreurAssociee?: string;
}

export interface QuestionRequest {
  enonce: string;
  niveauBloom?: NiveauBloom;
  competence?: string;
  explication?: string;
  choix: OptionRequest[];
}

export interface QuizRequest {
  titre: string;
  description?: string;
  coursId: number;
  type?: TypeQuiz;
  theme?: string;
  matiere?: string;
  difficulte?: Difficulte;
  seuilReussite?: number;
  tentativesMax?: number;
  dureeMinutes?: number;
  /** Absent : les questions existantes sont gardées (PUT). */
  questions?: QuestionRequest[];
}

/** Vue éditeur (auteur ou admin), avec les bonnes réponses. */
export interface QuizDetail {
  quiz: Quiz;
  questions: {
    id: number;
    enonce: string;
    niveauBloom?: NiveauBloom;
    competence?: string;
    explication?: string;
    choix: { id: number; texte: string; estCorrecte: boolean; erreurAssociee?: string }[];
  }[];
}

/** Vue de passage : aucune bonne réponse. */
export interface QuestionPassage {
  id: number;
  enonce: string;
  niveauBloom?: NiveauBloom;
  competence?: string;
  choix: { id: number; texte: string }[];
}

export interface Passage {
  quiz: Quiz;
  questions: QuestionPassage[];
  tentativesFaites: number;
  /** null si illimité. */
  tentativesRestantes: number | null;
}

export interface TentativeRequest {
  reponses: { questionId: number; optionId: number | null }[];
}

export interface Correction {
  reponseId: number;
  questionId: number;
  enonce: string;
  competence?: string;
  niveauBloom?: NiveauBloom;
  optionChoisieId?: number;
  optionCorrecteId: number;
  correcte: boolean;
  explication?: string;
  diagnostic?: string;
}

export interface TentativeResultat {
  tentativeId: number;
  quizId: number;
  nbCorrectes: number;
  nbQuestions: number;
  pourcentage: number;
  reussie: boolean;
  seuilReussite: number;
  certificatNumero?: string;
  corrections: Correction[];
}

export interface HistoriqueLigne {
  tentativeId: number;
  quizId: number;
  titre: string;
  coursId: number;
  type: TypeQuiz;
  pourcentage: number;
  reussie: boolean;
  dateTentative: string;
  moyenneAutres?: number;
}

export interface StudioLigne {
  id: number;
  titre: string;
  coursId: number;
  type: TypeQuiz;
  statut: StatutQuiz;
  nbQuestions: number;
  nbTentatives: number;
  moyenne?: number;
  tauxReussite?: number;
  dateCreation: string;
}

export interface StatistiquesQuiz {
  quizId: number;
  nbTentatives: number;
  moyenne?: number;
  tauxReussite?: number;
  /** 5 tranches : 0-19, 20-39, 40-59, 60-79, 80-100. */
  distribution: number[];
  questions: { questionId: number; numero: number; enonce: string; competence?: string; nbReponses: number; tauxErreur: number }[];
}

export interface Remediation {
  notionRateeId: number;
  notion: string;
  diagnostic?: string;
  explication?: string;
  conseil?: string;
  questionRattrapage?: { enonce: string; choix: string[] };
  resolue: boolean;
  fournisseur?: string;
  alertes?: string[];
}

export interface RattrapageResultat {
  correcte: boolean;
  indexBonne: number;
  explication?: string;
}

export interface Certificat {
  numero: string;
  coursId: number;
  /** Titre du cours copié à l'émission (absent si cours-service ne répondait pas). */
  titreCours?: string;
  quizId: number;
  titreExamen: string;
  score: number;
  dateEmission: string;
  /** Absent dans une vérification publique. */
  etudiantId?: number;
}

/** Contrat 3 (consommé par le module User). */
export interface NotionRatee {
  notion: string;
  coursId: number;
  nbErreurs: number;
  tauxReussite: number;
}
