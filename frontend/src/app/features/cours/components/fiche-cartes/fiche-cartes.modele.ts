import { Fiche } from '../../models/cours.model';

/**
 * Logique pure de la « Vue fiches » : construction des cartes à partir de la fiche EXISTANTE (aucun appel HTTP), bornes de navigation,
 * annonce pour les lecteurs d'écran et sens d'un glissement tactile.
 */
export type VueFiche = 'cartes' | 'liste';

export interface CarteResume {
  type: 'resume';
  cle: string;
  titre: string;
  resume: string;
  motsCles: string[];
}

export interface CarteNotion {
  type: 'notion';
  cle: string;
  /** Nom de la notion : recto de la carte. */
  titre: string;
  /** Rang de la notion (1, 2, 3…), affiché sur le recto. */
  rang: number;
  definition: string;
  /** Passage du cours qui justifie la notion : même visibilité que dans la vue liste (apprenant compris). */
  extrait: string | null;
  /** Extrait introuvable dans le cours : réservé au formateur (jamais renvoyé à l'apprenant). */
  suspecte: boolean;
}

export type Carte = CarteResume | CarteNotion;

/** Carte 1 : résumé et mots-clés ; puis une carte par notion, dans l'ordre de la fiche. */
export function construireCartes(fiche: Fiche, gestionnaire: boolean): Carte[] {
  const cartes: Carte[] = [{ type: 'resume', cle: 'resume', titre: 'Résumé', resume: fiche.resume, motsCles: fiche.motsCles }];
  [...fiche.notions]
    .sort((a, b) => a.ordre - b.ordre)
    .forEach((n, i) =>
      cartes.push({
        type: 'notion',
        cle: `notion-${n.id}`,
        titre: n.titre,
        rang: i + 1,
        definition: n.definition,
        extrait: n.extraitSource?.trim() ? n.extraitSource.trim() : null,
        suspecte: gestionnaire && n.justifiee === false,
      }),
    );
  return cartes;
}

/** Ramène un index dans [0, total - 1] : on ne boucle jamais (ni après la dernière carte, ni avant la première). */
export function borner(index: number, total: number): number {
  return Math.max(0, Math.min(index, Math.max(0, total - 1)));
}

/** « Carte 3 sur 9 : INNER JOIN », annoncé par la zone aria-live. */
export function annonce(index: number, total: number, titre: string): string {
  return `Carte ${index + 1} sur ${total} : ${titre}`;
}

/**
 * Sens d'un glissement : -1 (carte précédente, glissement vers la droite), +1 (carte suivante, vers la gauche) ou 0 (trop court, ou
 * surtout vertical : c'est un défilement de la page).
 */
export function sensGlissement(dx: number, dy: number, seuil = 50): -1 | 0 | 1 {
  if (Math.abs(dx) < seuil || Math.abs(dx) < Math.abs(dy) * 1.5) return 0;
  return dx < 0 ? 1 : -1;
}
