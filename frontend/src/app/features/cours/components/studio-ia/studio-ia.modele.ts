import { Explication, Fiche, LangueExplication, ModeExplication, ResumeLocal, Traitement } from '../../models/cours.model';

/**
 * Logique pure du « Studio IA » : type, statut et moteur de chaque génération, construits à partir des réponses EXISTANTES de
 * cours-service (fiche, explication, résumé local et leurs traitements). Aucun appel HTTP ici.
 */
export type TypeGeneration = 'resume' | 'fiche' | 'explication';
export type StatutGeneration = 'EN_COURS' | 'GENERE' | 'VALIDE' | 'ECHEC';

export const LIBELLE_STATUT_GENERATION: Record<StatutGeneration, string> = {
  EN_COURS: 'En cours',
  GENERE: 'Généré',
  VALIDE: 'Validé',
  ECHEC: 'Échec',
};

export const ICONE_GENERATION: Record<TypeGeneration, string> = {
  resume: 'summarize',
  fiche: 'fact_check',
  explication: 'record_voice_over',
};

export const TITRE_GENERATION: Record<TypeGeneration, string> = {
  resume: 'Résumé automatique',
  fiche: 'Fiche de révision',
  explication: 'Explication orale',
};

/** Fournisseur ou algorithme affiché. `estime` : déduit du nom du modèle (cours-service n'enregistre pas encore le vrai fournisseur). */
export interface MoteurAffiche {
  libelle: string;
  estime: boolean;
}

/** « Gemini (estimé) » ou « Local · BARThez ». */
export function texteMoteur(moteur: MoteurAffiche | null): string {
  return moteur ? (moteur.estime ? `${moteur.libelle} (estimé)` : moteur.libelle) : '';
}

/** Algorithme du résumé local d'après son modèle : exact, puisque le modèle est exécuté sur le serveur. */
export function moteurResumeLocal(modele?: string | null): MoteurAffiche | null {
  if (!modele) return null;
  const m = modele.toLowerCase();
  let libelle: string;
  if (m.includes('textrank') || m.startsWith('extractif')) libelle = 'TextRank';
  else if (m.includes('barthez')) libelle = 'BARThez';
  else if (m.includes('distilbart')) libelle = 'DistilBART';
  else if (m.includes('bart-large-cnn')) libelle = 'BART';
  else libelle = modele.split('/').pop() ?? modele;
  return { libelle: `Local · ${libelle}`, estime: false };
}

/** Fournisseur LLM d'après le nom du modèle (Gemini, Groq ou OpenAI) : une estimation, d'où `estime`. Null si le modèle est inconnu. */
export function moteurGeneratif(modele?: string | null): MoteurAffiche | null {
  if (!modele || modele === 'stub') return null;
  const m = modele.toLowerCase();
  if (m.startsWith('gemini')) return { libelle: 'Gemini', estime: true };
  if (m.startsWith('openai/')) return { libelle: 'Groq', estime: true };
  if (m.startsWith('gpt')) return { libelle: 'OpenAI', estime: true };
  return { libelle: modele, estime: true };
}

export interface Generation {
  /** Identifiant stable de la ligne (pour le suivi de la liste). */
  cle: string;
  type: TypeGeneration;
  titre: string;
  /** Langue et mode, par exemple « Français · monologue ». */
  detail: string;
  statut: StatutGeneration;
  moteur: MoteurAffiche | null;
  date: string | null;
  langue?: LangueExplication;
  mode?: ModeExplication;
  /** Cette ligne peut-elle être validée depuis le menu ? Sinon `motif` dit pourquoi. */
  validable: boolean;
  motif?: string;
  /** Message d'échec lisible (ligne en échec). */
  erreur?: string;
}

const LIBELLE_LANGUE: Record<string, string> = { fr: 'Français', en: 'English' };
const LIBELLE_MODE: Record<string, string> = { monologue: 'monologue', dialogue: 'dialogue' };

interface Base {
  type: TypeGeneration;
  cle: string;
  detail: string;
  langue?: LangueExplication;
  mode?: ModeExplication;
}

interface Resultat {
  valide: boolean;
  moteur: MoteurAffiche | null;
  date: string | null;
  validable: boolean;
  motif?: string;
}

/**
 * Lignes d'un type de génération : « En cours » si un traitement tourne, « Échec » si une étape AVANT l'audio a échoué (l'audio seul
 * n'empêche pas d'avoir un résultat), puis le résultat le plus récent (Généré ou Validé).
 */
function lignes(base: Base, resultat: Resultat | null, t: Traitement | null): Generation[] {
  const commun = { type: base.type, titre: TITRE_GENERATION[base.type], detail: base.detail, langue: base.langue, mode: base.mode };
  const liste: Generation[] = [];
  if (t?.enCours) {
    liste.push({ ...commun, cle: `${base.cle}:cours`, statut: 'EN_COURS', moteur: null, date: null, validable: false });
  } else {
    const echec = t?.etapes.find((e) => e.statut === 'ECHEC' && e.etape !== 'AUDIO');
    if (echec) {
      liste.push({
        ...commun, cle: `${base.cle}:echec`, statut: 'ECHEC', moteur: null, date: null, validable: false,
        erreur: echec.erreur ?? 'La génération a échoué.',
      });
    }
  }
  if (resultat) {
    liste.push({
      ...commun,
      cle: `${base.cle}:resultat`,
      statut: resultat.valide ? 'VALIDE' : 'GENERE',
      moteur: resultat.moteur,
      date: resultat.date,
      validable: resultat.validable,
      motif: resultat.motif,
    });
  }
  return liste;
}

export function lignesFiche(f: Fiche | null, t: Traitement | null): Generation[] {
  const resultat: Resultat | null = f
    ? { valide: f.statut === 'VALIDEE', moteur: moteurGeneratif(f.modele), date: f.dateValidation ?? f.dateGeneration, validable: f.statut !== 'VALIDEE' }
    : null;
  return lignes({ type: 'fiche', cle: 'fiche', detail: 'Résumé, mots-clés et notions' }, resultat, t);
}

export function detailExplication(langue: string, mode: string): string {
  return `${LIBELLE_LANGUE[langue] ?? langue} · ${LIBELLE_MODE[mode] ?? mode}`;
}

export function lignesExplication(langue: LangueExplication, mode: ModeExplication, e: Explication | null, t: Traitement | null): Generation[] {
  const valide = e?.statut === 'VALIDEE';
  const resultat: Resultat | null = e
    ? {
        valide,
        moteur: moteurGeneratif(e.modele),
        date: e.dateValidation ?? e.dateGeneration,
        validable: !valide && !!e.aPuEcouter,
        motif: !valide && !e.aPuEcouter ? 'Écoute d’abord un aperçu audio (menu « Ouvrir »)' : undefined,
      }
    : null;
  return lignes({ type: 'explication', cle: `explication:${langue}:${mode}`, detail: detailExplication(langue, mode), langue, mode }, resultat, t);
}

export function lignesResume(r: ResumeLocal | null, t: Traitement | null): Generation[] {
  const resultat: Resultat | null = r
    ? {
        valide: r.statut === 'VALIDE',
        moteur: moteurResumeLocal(r.modele),
        date: r.dateValidation ?? r.dateCreation,
        validable: r.statut !== 'VALIDE',
      }
    : null;
  const detail = r ? `${LIBELLE_LANGUE[r.langue] ?? r.langue} · ${r.modeUtilise === 'extractif' ? 'phrases clés' : 'reformulé'}` : 'Modèle local';
  return lignes({ type: 'resume', cle: 'resume', detail }, resultat, t);
}

/** « En cours » d'abord, puis du plus récent au plus ancien. */
export function trier(generations: Generation[]): Generation[] {
  return [...generations].sort((a, b) => {
    if ((a.statut === 'EN_COURS') !== (b.statut === 'EN_COURS')) return a.statut === 'EN_COURS' ? -1 : 1;
    return (b.date ?? '').localeCompare(a.date ?? '');
  });
}
