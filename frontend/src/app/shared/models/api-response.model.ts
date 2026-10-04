/** Enveloppe commune des réponses réussies (voir CONTRATS_API.md). */
export interface ApiResponse<T> {
  success: true;
  data: T;
  message?: string;
  timestamp: string;
}

/** Format commun des erreurs (voir CONTRATS_API.md). */
export interface ApiError {
  success: false;
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  details?: Record<string, string>;
}

/** Extrait un message lisible d'une HttpErrorResponse. */
export function errorMessage(err: unknown, fallback = 'Une erreur est survenue'): string {
  const body = (err as { error?: Partial<ApiError> } | null)?.error;
  if (body?.details) {
    return Object.values(body.details).join(' · ');
  }
  if ((err as { status?: number })?.status === 0) {
    return 'Serveur injoignable (api-gateway démarrée ?)';
  }
  return body?.message ?? fallback;
}
