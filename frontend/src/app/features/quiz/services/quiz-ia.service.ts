import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { GenererQuizRequest, GenererQuizResponse, RemedierRequest, RemedierResponse } from '../models/quiz-ia.model';

/** IA du module Quiz, via la gateway (/api/ai/quiz/**). Aucune clé ni appel direct à un fournisseur LLM. */
@Injectable({ providedIn: 'root' })
export class QuizIaService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/ai/quiz`;

  generer(body: GenererQuizRequest): Observable<GenererQuizResponse> {
    return this.http.post<ApiResponse<GenererQuizResponse>>(`${this.url}/generer`, body).pipe(map((r) => r.data));
  }

  remedier(body: RemedierRequest): Observable<RemedierResponse> {
    return this.http.post<ApiResponse<RemedierResponse>>(`${this.url}/remedier`, body).pipe(map((r) => r.data));
  }
}

/** Message lisible d'une erreur de l'IA (enveloppe ApiError), sans détail technique. */
export function messageErreurIa(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Le service IA est injoignable. Réessaie dans un instant.';
    if (err.status === 429) return 'Le quota de l’IA est atteint. Réessaie dans une minute.';
    const message = (err.error as { message?: string } | null)?.message;
    if (message) return message;
  }
  return 'L’IA n’a pas pu répondre. Réessaie dans un instant.';
}
