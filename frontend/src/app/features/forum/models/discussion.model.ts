/** Miroir de DiscussionResponse (forum-service). */
export interface Discussion {
  id: number;
  titre: string;
  contenu: string;
  coursId: number | null;
  auteurId: number;
  dateCreation: string;
  dateModification?: string;
}

export interface DiscussionRequest {
  titre: string;
  contenu: string;
  coursId?: number;
}
