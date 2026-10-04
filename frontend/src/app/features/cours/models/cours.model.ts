export type Niveau = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE';

/** Miroir de CoursResponse (cours-service). */
export interface Cours {
  id: number;
  titre: string;
  description: string | null;
  categorie: string | null;
  niveau: Niveau | null;
  fichierPdf: string | null;
  enseignantId: number;
  dateCreation: string;
  /** Absent tant que la ressource n'a jamais été modifiée (champs null omis). */
  dateModification?: string;
}

export interface CoursRequest {
  titre: string;
  description?: string;
  categorie?: string;
  niveau?: Niveau;
  fichierPdf?: string;
}
