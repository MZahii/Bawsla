export type Niveau = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';

/** Seul PUBLIE est visible par les apprenants. */
export type StatutCours = 'BROUILLON' | 'PUBLIE' | 'ARCHIVE';

/** Miroir de CoursResponse (cours-service). */
export interface Cours {
  id: number;
  titre: string;
  description: string | null;
  categorie: string | null;
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
