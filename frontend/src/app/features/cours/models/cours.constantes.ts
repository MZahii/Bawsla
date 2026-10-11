import { Niveau, StatutCours } from './cours.model';

/**
 * Listes de référence du module Cours, partagées par l'éditeur du studio et le catalogue.
 * La catégorie est enregistrée telle quelle dans cours-service : un seul libellé par domaine.
 */
export const CATEGORIES = [
  { nom: 'Données', icone: 'database' },
  { nom: 'Back-end', icone: 'dns' },
  { nom: 'Front-end', icone: 'web' },
  { nom: 'DevOps', icone: 'deployed_code' },
  { nom: 'Outils', icone: 'build' },
] as const;

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

/** Image affichée tant qu'un cours n'a pas de couverture. */
export const IMAGE_DEFAUT = 'assets/demo/cours/outils.jpg';
export const FORMATEUR_DEFAUT = 'Formateur';
/** Valeur d'une donnée que cours-service ne fournit pas encore (inscrits, note, achèvement…). */
export const VIDE = '—';

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
