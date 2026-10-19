import { Niveau, StatutCours } from './cours.model';

/**
 * Listes de référence du module Cours, partagées par l'éditeur du studio et le catalogue.
 * La catégorie d'un cours est une MATIÈRE, choisie dans la même liste que le module Quiz (mêmes libellés EXACTS, accents compris) :
 * cours-service refuse toute autre valeur. « Autre » demande une précision (2 à 80 caractères).
 */
export const MATIERE_AUTRE = 'Autre';
export const PRECISION_MIN = 2;
export const PRECISION_MAX = 80;

export const CATEGORIES = [
  { nom: 'Informatique', icone: 'computer' },
  { nom: 'Mathématiques', icone: 'calculate' },
  { nom: 'Physique-Chimie', icone: 'science' },
  { nom: 'Sciences de la vie et de la Terre', icone: 'biotech' },
  { nom: 'Histoire', icone: 'history_edu' },
  { nom: 'Géographie', icone: 'public' },
  { nom: 'Langues', icone: 'translate' },
  { nom: 'Économie et gestion', icone: 'payments' },
  { nom: 'Psychologie', icone: 'psychology' },
  { nom: 'Philosophie', icone: 'lightbulb' },
  { nom: 'Arts', icone: 'palette' },
  { nom: MATIERE_AUTRE, icone: 'category' },
] as const;

/** Anciennes catégories du catalogue : toutes relevaient de l'informatique (cours-service les a converties en « Informatique »). */
export const ANCIENS_LIBELLES_INFORMATIQUE: readonly string[] = ['Données', 'Back-end', 'Front-end', 'DevOps', 'Outils'];

/**
 * Matière à filtrer d'après le paramètre d'URL `?domaine=` : un ancien libellé (anciens favoris ou menus) donne « Informatique »
 * au lieu d'une liste vide ; la forme Unicode décomposée est ramenée à la forme composée.
 */
export function matiereDepuisUrl(valeur: string | null | undefined): string {
  const propre = (valeur ?? '').normalize('NFC').trim();
  return ANCIENS_LIBELLES_INFORMATIQUE.includes(propre) ? 'Informatique' : propre;
}

/** « Mathématiques », ou « Autre · Cryptographie » pour un cours « Autre » qui a une précision. */
export function libelleMatiere(categorie: string | null | undefined, precision?: string | null): string {
  if (!categorie) return '';
  return categorie === MATIERE_AUTRE && precision ? `${MATIERE_AUTRE} · ${precision}` : categorie;
}

export const NIVEAUX: readonly { code: Niveau; libelle: string }[] = [
  { code: 'DEBUTANT', libelle: 'Débutant' },
  { code: 'INTERMEDIAIRE', libelle: 'Intermédiaire' },
  { code: 'AVANCE', libelle: 'Avancé' },
];

export const LIBELLE_NIVEAU: Record<Niveau, string> = {
  DEBUTANT: 'Débutant',
  INTERMEDIAIRE: 'Intermédiaire',
  AVANCE: 'Avancé',
};

export const LIBELLE_STATUT: Record<StatutCours, string> = {
  BROUILLON: 'Brouillon',
  PUBLIE: 'Publié',
  ARCHIVE: 'Archivé',
};

/** Rang d'un niveau pour <bw-badge variant="niveau">. */
export const RANG_NIVEAU: Record<Niveau, 1 | 2 | 3> = { DEBUTANT: 1, INTERMEDIAIRE: 2, AVANCE: 3 };

/** Banque de couvertures de remplacement (assets/demo/cours/<thème>-<n>.jpg, photos Unsplash 1280×720) : nombre d'images par thème. */
const BANQUE: Record<string, number> = {
  code: 6, web: 5, donnees: 4, serveur: 4, reseau: 1, securite: 2, ia: 3, materiel: 1,
  maths: 3, physique: 3, svt: 4, histoire: 3, geo: 5, langues: 4, eco: 4, psycho: 3, philo: 3, arts: 4, etude: 5,
};
const image = (theme: string, n: number) => `assets/demo/cours/${theme}-${n}.jpg`;
const lot = (...themes: string[]) => themes.flatMap((t) => Array.from({ length: BANQUE[t] }, (_, i) => image(t, i + 1)));

/** Image affichée tant qu'un cours n'a pas de couverture. */
export const IMAGE_DEFAUT = image('etude', 1);

/** Thèmes d'images de chaque matière (hors Informatique, qui se précise par le titre). */
const IMAGES_MATIERE: Record<string, string[]> = {
  'Mathématiques': lot('maths'),
  'Physique-Chimie': lot('physique'),
  'Sciences de la vie et de la Terre': lot('svt'),
  'Histoire': lot('histoire'),
  'Géographie': lot('geo'),
  'Langues': lot('langues'),
  'Économie et gestion': lot('eco'),
  'Psychologie': lot('psycho'),
  'Philosophie': lot('philo'),
  'Arts': lot('arts'),
};

/** Mots-clés du titre : le premier qui correspond choisit le lot (vaut pour toutes les matières, utile pour « Autre » et l'informatique). */
const IMAGES_TITRE: [RegExp, string[]][] = [
  [/\bia\b|intelligence artificielle|machine learning|apprentissage automatique|r[ée]seaux? de neurones|\bllm\b|deep learning/i, lot('ia')],
  [/cyber|s[ée]curit[ée] informatique|crypto|hack|r[ée]seaux? informatique|\btcp\b/i, lot('securite', 'reseau')],
  [/\bsql\b|bases? de donn|donn[ée]es|\bdata\b|statisti|excel|\bbi\b/i, lot('donnees')],
  [/angular|react|vue\.?js|front-?end|html|\bcss\b|\bweb\b|\bux\b|\bui\b|javascript|typescript/i, lot('web')],
  [/\bapi\b|\brest\b|http|microservice|spring|\bjava\b|back-?end|serveur|node\.?js|docker|devops|cloud|linux|\bgit\b|d[ée]ploi/i, lot('serveur', 'reseau')],
  [/arduino|[ée]lectroni|architecture des ordinateurs|syst[èe]mes? embarqu|mat[ée]riel informatique/i, lot('materiel', 'securite')],
  [/langage c\b|\bc\+\+|\bc#|algorithm|programm|python|\bcode\b|d[ée]veloppe/i, lot('code')],
  [/math|alg[èe]bre|g[ée]om[ée]tr|probabilit|calcul/i, lot('maths')],
  [/anglais|fran[çc]ais|arabe|espagnol|allemand|langues?\b|grammaire/i, lot('langues')],
];

/** Image d'un cours sans couverture : choisie selon le titre puis la matière, variée d'un cours à l'autre, toujours la même pour un cours donné. */
export function imageParDefaut(cours?: { id?: number; titre?: string; categorie?: string | null } | null): string {
  const titre = cours?.titre ?? '';
  const choix =
    IMAGES_TITRE.find(([motif]) => motif.test(titre))?.[1] ??
    IMAGES_MATIERE[cours?.categorie ?? ''] ??
    (cours?.categorie === 'Informatique' ? lot('code', 'web', 'donnees', 'serveur', 'ia') : lot('etude', 'code', 'maths', 'langues', 'arts'));
  return choix[(cours?.id ?? 0) % choix.length];
}

export const FORMATEUR_DEFAUT = 'Formateur';
/** Valeur d'une donnée absente : pas encore de note, aucun inscrit pour calculer un achèvement… */
export const VIDE = '—';

/** 0,65 → « 65 % » (progression de 0 à 1 renvoyée par cours-service). */
export function pourcent(progression: number | null | undefined): string {
  return `${Math.round((progression ?? 0) * 100)} %`;
}

/** « Gratuit » ou « 49 DT » (« 49,5 DT » s'il y a des centimes). */
export function libellePrix(prix: number | null | undefined): string {
  if (!prix || prix <= 0) return 'Gratuit';
  return `${Number.isInteger(prix) ? prix : prix.toFixed(2).replace('.', ',')} DT`;
}

/** 45 → « 45 min », 80 → « 1 h 20 », 120 → « 2 h ». Vide si la durée est nulle. */
export function formatDuree(minutes: number | null | undefined): string {
  if (!minutes || minutes <= 0) return '';
  if (minutes < 60) return `${minutes} min`;
  const reste = minutes % 60;
  return reste ? `${Math.floor(minutes / 60)} h ${String(reste).padStart(2, '0')}` : `${minutes / 60} h`;
}

/** Date ISO du backend (2026-10-11T18:05:59) en JJ/MM/AAAA. */
export function formatDate(iso: string | undefined | null): string {
  if (!iso) return VIDE;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? VIDE : d.toLocaleDateString('fr-FR');
}

/** 1,2 Mo ou 340 Ko. */
export function formatTaille(octets: number | null | undefined): string {
  if (!octets) return '';
  return octets >= 1e6 ? `${(octets / 1e6).toFixed(1).replace('.', ',')} Mo` : `${Math.max(1, Math.round(octets / 1e3))} Ko`;
}
