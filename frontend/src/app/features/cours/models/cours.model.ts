export type Niveau = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';

/** Seul PUBLIE est visible par les apprenants. */
export type StatutCours = 'BROUILLON' | 'PUBLIE' | 'ARCHIVE';

/** Miroir de CoursResponse (cours-service). */
export interface Cours {
  id: number;
  titre: string;
  description: string | null;
  /** Matière du cours (liste commune avec le module Quiz). */
  categorie: string | null;
  /** Précision de la matière « Autre » (2 à 80 caractères) ; absente pour toute autre matière. */
  categoriePrecision?: string | null;
  niveau: Niveau | null;
  /** Nom d'origine du PDF (affichage), pas son chemin. */
  fichierPdf: string | null;
  enseignantId: number;
  dateCreation: string;
  /** Absent tant que la ressource n'a jamais été modifiée (champs null omis). */
  dateModification?: string | null;
  /** En dinars ; 0 = gratuit. */
  prix: number;
  statut: StatutCours;
  /** Route de l'image (/api/cours/{id}/image?v=…). Elle exige le JWT : voir CoursImageService. */
  imageUrl: string | null;
  nbChapitres: number;
  dureeTotaleMinutes: number;
  aPdf: boolean;
  pdfTaille: number | null;
}

/** Corps de POST et PUT /api/cours. `prix` absent : inchangé en modification, gratuit à la création. */
export interface CoursRequest {
  titre: string;
  description?: string;
  categorie?: string;
  /** Obligatoire si `categorie` vaut « Autre », interdite sinon. */
  categoriePrecision?: string;
  niveau?: Niveau;
  prix?: number;
}

/** Miroir de ChapitreResponse. */
export interface Chapitre {
  id: number;
  ordre: number;
  titre: string;
  contenu: string | null;
  dureeMinutes: number;
  videoUrl: string | null;
}

export interface ChapitreRequest {
  titre: string;
  contenu?: string;
  dureeMinutes?: number;
  /** Vide, lien YouTube ou fichier .mp4 en http(s). */
  videoUrl?: string;
}

export interface PageTexte {
  numero: number;
  texte: string;
}

/** Contrat GET /api/cours/{id}/texte. `pages` et `estScanne` sont absents quand le cours n'a pas de PDF. */
export interface CoursTexte {
  coursId: number;
  texte: string;
  stub: boolean;
  pages?: PageTexte[];
  estScanne?: boolean;
}

// ---------------------------------------------------------------- fiche de révision générée par l'IA

/** GENEREE : brouillon relu par le formateur seul. VALIDEE : visible des apprenants. */
export type StatutFiche = 'GENEREE' | 'VALIDEE';

export interface NotionFiche {
  id: number;
  ordre: number;
  titre: string;
  definition: string;
  /** Passage du cours qui justifie la notion, recopié mot pour mot. */
  extraitSource: string | null;
  /** Réservé au formateur : false si l'extrait est introuvable dans le texte du cours (notion suspecte). */
  justifiee?: boolean;
}

/** Miroir de FicheResponse. Score, alertes, modèle et version du prompt ne sont renvoyés qu'au formateur et à l'ADMIN. */
export interface Fiche {
  id: number;
  statut: StatutFiche;
  resume: string;
  motsCles: string[];
  notions: NotionFiche[];
  niveau: Niveau | null;
  aAudio: boolean;
  /** Fournisseur vocal de l'audio : elevenlabs, gemini, gtts ou edge (absent s'il n'y a pas d'audio). */
  fournisseurAudio?: string;
  dateGeneration: string;
  dateValidation?: string;
  scoreFidelite?: number;
  alertes?: string[];
  versionPrompt?: string;
  modele?: string;
  modifieeParFormateur?: boolean;
  /** Une fiche validée existe (visible des apprenants), même si la plus récente est encore un brouillon. */
  valideeDisponible?: boolean;
}

export interface FicheRequest {
  resume: string;
  motsCles: string[];
  notions: { titre: string; definition: string; extraitSource: string }[];
}

export type EtapeIA = 'TEXTE' | 'FICHE' | 'SCRIPT' | 'TRADUCTION' | 'RESUME' | 'AUDIO';
export type StatutTraitement = 'EN_ATTENTE' | 'EN_COURS' | 'TERMINE' | 'ECHEC' | 'ANNULE';

export interface EtapeTraitement {
  etape: EtapeIA;
  statut: StatutTraitement;
  dureeMs?: number;
  /** Message lisible, sans détail technique. */
  erreur?: string;
}

/** Suivi du dernier lancement de l'IA sur un cours. `enCours` reste vrai tant qu'une étape n'est pas terminée. */
export interface Traitement {
  execution: string | null;
  enCours: boolean;
  etapes: EtapeTraitement[];
}

// ------------------------------------------------------------------ explication orale par l'IA

export type LangueExplication = 'fr' | 'en';
export type ModeExplication = 'monologue' | 'dialogue';
export type TypeSegment = 'intro' | 'explication' | 'analogie' | 'exemple' | 'demonstration' | 'question' | 'recap';

/** Miroir de SegmentExplicationResponse. L'extrait source et `justifiee` ne sont renvoyés qu'au formateur et à l'ADMIN. */
export interface SegmentExplication {
  id: number;
  ordre: number;
  type: TypeSegment;
  locuteur: 'prof' | 'etudiant';
  texte: string;
  notion?: string;
  page?: number;
  analogie: boolean;
  debutMs?: number;
  finMs?: number;
  extraitSource?: string;
  justifiee?: boolean;
}

/** Miroir de ExplicationResponse. Les champs de contrôle (score, alertes, modèle…) sont réservés au formateur. */
export interface Explication {
  id: number;
  langue: LangueExplication;
  mode: ModeExplication;
  statut: StatutFiche;
  segments: SegmentExplication[];
  aAudio: boolean;
  fournisseurAudio?: string;
  dureeMs?: number;
  minutageEstime: boolean;
  dateGeneration: string;
  dateValidation?: string;
  scoreFidelite?: number;
  alertes?: string[];
  versionPrompt?: string;
  modele?: string;
  nbMots?: number;
  dureeEstimeeSecondes?: number;
  modifieeParFormateur?: boolean;
  notionsChoisiesParIa?: boolean;
  aRetraduire?: boolean;
  /** L'audio correspond au script actuel. */
  audioAJour?: boolean;
  /** Le formateur a un aperçu (même ancien) : condition pour valider. */
  aPuEcouter?: boolean;
  valideeDisponible?: boolean;
}

export interface DisponibiliteExplication {
  langue: LangueExplication;
  mode: ModeExplication;
  statut: StatutFiche;
  aAudio: boolean;
  dureeMs?: number;
}

export interface CorrectionExplicationRequest {
  segments: { ordre: number; texte: string; extraitSource?: string }[];
}

// ------------------------------------------------------------------ résumé automatique LOCAL (modèle Hugging Face, sans IA générative)

export type LongueurResume = 'courte' | 'moyenne' | 'longue';
export type ModeResume = 'auto' | 'abstractif' | 'extractif';
export type StatutResumeLocal = 'GENERE' | 'VALIDE';

/** Miroir de ResumeLocalResponse.Phrase. L'extrait source et `justifiee` ne sont renvoyés qu'au formateur et à l'ADMIN. */
export interface PhraseResumeLocal {
  ordre: number;
  texte: string;
  page?: number;
  extraitSource?: string;
  justifiee?: boolean;
}

/** Miroir de ResumeLocalResponse. Modèle, score, alertes et durée de calcul sont réservés au formateur et à l'ADMIN. */
export interface ResumeLocal {
  id: number;
  statut: StatutResumeLocal;
  /** Langue détectée du cours : fr ou en. */
  langue: 'fr' | 'en';
  /** abstractif (reformulé) ou extractif (phrases clés du cours). */
  modeUtilise: 'abstractif' | 'extractif';
  texte: string;
  phrases: PhraseResumeLocal[];
  aAudio: boolean;
  dureeAudio?: number;
  fournisseurAudio?: string;
  dateCreation: string;
  dateValidation?: string;
  modele?: string;
  scoreFidelite?: number;
  /** Score du reformulé même s'il a été remplacé par les phrases clés. */
  scoreReformule?: number;
  alertes?: string[];
  dureeCalculMs?: number;
  valideeDisponible?: boolean;
}
