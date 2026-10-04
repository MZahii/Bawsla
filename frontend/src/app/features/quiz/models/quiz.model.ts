/** Miroir de QuizResponse (quiz-service). */
export interface Quiz {
  id: number;
  titre: string;
  description: string | null;
  coursId: number;
  enseignantId: number;
  dateCreation: string;
  dateModification?: string;
}

export interface QuizRequest {
  titre: string;
  description?: string;
  coursId: number;
}
