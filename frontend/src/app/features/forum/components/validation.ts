/**
 * Règles de saisie du forum, recopiées de forum-service avec les mêmes messages (DTO Jakarta Validation,
 * MotifValidator, Repetitions, ParametresService). Le serveur reste juge : ces règles servent à prévenir avant
 * l'envoi, à afficher l'erreur sous le champ et à désactiver le bouton tant que le formulaire est invalide.
 */

export interface Regle {
  /** Message si le champ est vide (absent = facultatif). */
  obligatoire?: string;
  min?: number;
  max: number;
  longueur: string;
}

export const REGLES = {
  titre: { obligatoire: 'Le titre est obligatoire', min: 10, max: 200, longueur: 'Le titre fait entre 10 et 200 caractères' },
  contenu: { obligatoire: 'Le contenu est obligatoire', max: 20000, longueur: 'Le contenu fait au plus 20 000 caractères' },
  reponse: { obligatoire: 'Le contenu est obligatoire', max: 20000, longueur: 'La réponse fait au plus 20 000 caractères' },
  commentaire: { obligatoire: 'Le commentaire est obligatoire', max: 600, longueur: 'Un commentaire fait au plus 600 caractères' },
  question: { obligatoire: 'La question est obligatoire', max: 2000, longueur: 'La question fait au plus 2000 caractères' },
  correction: { obligatoire: 'Le texte corrigé est obligatoire', max: 10000, longueur: 'Le texte fait au plus 10 000 caractères' },
  modele: { max: 4000, longueur: 'Le modèle fait au plus 4000 caractères' },
  etiquette: { max: 40, longueur: 'Une étiquette fait au plus 40 caractères' },
} satisfies Record<string, Regle>;

/** null si la valeur respecte la règle, sinon le message du serveur. Les espaces de début et de fin ne comptent pas. */
export function erreurTexte(regle: Regle, valeur: string | null | undefined): string | null {
  const t = (valeur ?? '').trim();
  if (!t) {
    return regle.obligatoire ?? null;
  }
  return t.length > regle.max || (regle.min !== undefined && t.length < regle.min) ? regle.longueur : null;
}

// ---------------------------------------------------------------- Motifs de modération

export const MOTIF_MIN = 10;
export const MOTIF_MAX = 500;
export const ERREURS_MOTIF = {
  obligatoire: 'Le motif est obligatoire',
  repetitif: 'Le motif est trop répétitif : explique la raison en quelques mots',
  longueur: `Le motif fait entre ${MOTIF_MIN} et ${MOTIF_MAX} caractères`,
  mots: 'Le motif doit contenir au moins 2 mots',
};

/** Mots comparés sans accents ni majuscules (Repetitions.mots). */
function mots(texte: string): string[] {
  return texte.toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '').match(/[\p{L}\p{N}]+/gu) ?? [];
}

const LETTRE = /\p{L}/u;

/**
 * Règle « repetitions » de la modération adaptée aux textes courts (Repetitions.motifRepetitif) : 4 fois la même
 * lettre, un mot fait d'une seule lettre répétée (« kkk »), au moins 4 mots dont moins de la moitié distincts, ou
 * moins de 4 lettres différentes sur 10 caractères (« kjkjkjkjkj »).
 */
export function motifRepetitif(texte: string): boolean {
  if (/(\p{L})\1{3,}/iu.test(texte)) {
    return true;
  }
  const liste = mots(texte);
  if (liste.length >= 4 && new Set(liste).size / liste.length < 0.5) {
    return true;
  }
  const lettres = new Set<string>();
  for (const mot of liste) {
    if (mot.length >= 3 && new Set(mot).size === 1 && LETTRE.test(mot[0])) {
      return true;
    }
    [...mot].filter((c) => LETTRE.test(c)).forEach((c) => lettres.add(c));
  }
  return texte.trim().length >= 10 && lettres.size < 4;
}

/** Même ordre de vérification que MotifValidator.erreur. */
export function erreurMotif(motif: string | null | undefined): string | null {
  const t = (motif ?? '').trim();
  if (!t) {
    return ERREURS_MOTIF.obligatoire;
  }
  if (motifRepetitif(t)) {
    return ERREURS_MOTIF.repetitif;
  }
  if (t.length < MOTIF_MIN || t.length > MOTIF_MAX) {
    return ERREURS_MOTIF.longueur;
  }
  return new Set(mots(t).filter((m) => m.length >= 2)).size < 2 ? ERREURS_MOTIF.mots : null;
}

export type ContexteMotif =
  | 'decision'
  | 'sanction'
  | 'masquage'
  | 'retablissement'
  | 'deplacement'
  | 'fusion'
  | 'rejet-vigie'
  | 'correction-vigie';

/** Motifs proposés en un clic (modifiables ensuite), selon la décision. */
export const MOTIFS_PREDEFINIS: Record<ContexteMotif, string[]> = {
  decision: [
    'Message hors du sujet du cours',
    'Propos irrespectueux envers un membre',
    'Publicité ou spam',
    'Partage de réponses d’examen',
    'Signalement sans fondement, message conforme',
    'Récidive : sanction à envisager par l’administration',
  ],
  sanction: [
    'Propos irrespectueux répétés envers des membres',
    'Publicité ou spam répété',
    'Partage de réponses d’examen',
    'Tentative de manipulation de La Vigie',
  ],
  masquage: [
    'Message hors du sujet du cours',
    'Propos irrespectueux envers un membre',
    'Publicité ou spam',
    'Données personnelles visibles',
  ],
  retablissement: ['Masquage injustifié après vérification', 'Message corrigé par son auteur'],
  deplacement: ['Question rattachée au bon chapitre', 'Question rattachée à la bonne page'],
  fusion: ['Doublon d’une question déjà posée', 'Même question posée deux fois'],
  'rejet-vigie': ['Réponse incorrecte au regard du cours', 'Réponse hors du sujet de la question', 'Sources citées non pertinentes'],
  'correction-vigie': ['Réponse incomplète, précision ajoutée', 'Erreur dans la réponse, corrigée', 'Formulation clarifiée pour les apprenants'],
};

// ---------------------------------------------------------------- Réglages globaux (ParametresService.valider)

export const ERREURS_REGLAGE = {
  seuil: 'Un seuil est un nombre entre 0 et 1 (ex. 0.85)',
  limite: 'Une limite est un nombre entier positif ou nul',
  interrupteur: 'La valeur est true (activé) ou false (désactivé)',
};

export function erreurReglage(cle: string, valeur: string | null | undefined): string | null {
  const v = (valeur ?? '').trim();
  if (cle.includes('seuil')) {
    const n = v === '' ? NaN : Number(v);
    return Number.isNaN(n) || n < 0 || n > 1 ? ERREURS_REGLAGE.seuil : null;
  }
  if (cle.startsWith('flood.') || cle === 'vote.points-minimum-negatif') {
    return /^\d{1,9}$/.test(v) ? null : ERREURS_REGLAGE.limite;
  }
  if (cle === 'vigie.reponse-generale-active') {
    return v === 'true' || v === 'false' ? null : ERREURS_REGLAGE.interrupteur;
  }
  return null;
}
