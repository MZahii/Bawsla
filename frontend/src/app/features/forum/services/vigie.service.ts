import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  AmeliorerRequest,
  AmeliorerResponse,
  BrouillonQuestion,
  ConversationVigie,
  FiltreVigie,
  IndexCours,
  MessageVigie,
  MessageVigieRequest,
  ResumeFil,
  VigieAVerifier,
  VigieFil,
  VigieResponse,
} from '../models';
import { parametres } from './forum.service';

/** La Vigie, l'IA du forum : réponse sourcée d'un fil, avis, décisions du formateur, assistant privé, aides. */
@Injectable({ providedIn: 'root' })
export class VigieService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/forum`;

  fil(discussionId: number): Observable<VigieFil> {
    return this.get(`${this.url}/discussions/${discussionId}/vigie`);
  }

  regenerer(discussionId: number): Observable<VigieFil> {
    return this.post(`${this.url}/discussions/${discussionId}/vigie/regenerer`, {});
  }

  /** Niveau 3, sur demande de l'apprenant après une abstention. */
  generale(discussionId: number): Observable<VigieResponse> {
    return this.post(`${this.url}/discussions/${discussionId}/vigie/general`, {});
  }

  avis(id: number, utile: boolean): Observable<VigieResponse> {
    return this.post(`${this.url}/vigie/${id}/avis`, { utile });
  }

  valider(id: number): Observable<VigieResponse> {
    return this.post(`${this.url}/vigie/${id}/valider`, {});
  }

  corriger(id: number, texte: string, motif: string): Observable<VigieResponse> {
    return this.post(`${this.url}/vigie/${id}/corriger`, { texte, motif });
  }

  rejeter(id: number, motif: string): Observable<VigieResponse> {
    return this.post(`${this.url}/vigie/${id}/rejeter`, { motif });
  }

  duCours(coursId: number, filtre: FiltreVigie): Observable<VigieAVerifier[]> {
    return this.get(`${this.url}/vigie`, { coursId, filtre });
  }

  // ------------------------------------------------------------ Assistant privé « Demander au cours »

  conversations(): Observable<ConversationVigie[]> {
    return this.get(`${this.url}/vigie/conversations`);
  }

  creerConversation(coursId: number): Observable<ConversationVigie> {
    return this.post(`${this.url}/vigie/conversations`, { coursId });
  }

  conversation(id: number): Observable<ConversationVigie> {
    return this.get(`${this.url}/vigie/conversations/${id}`);
  }

  envoyer(id: number, corps: MessageVigieRequest): Observable<MessageVigie> {
    return this.post(`${this.url}/vigie/conversations/${id}/messages`, corps);
  }

  brouillon(id: number): Observable<BrouillonQuestion> {
    return this.post(`${this.url}/vigie/conversations/${id}/publier`, {});
  }

  // ------------------------------------------------------------ Aides

  resume(discussionId: number): Observable<ResumeFil | null> {
    return this.get(`${this.url}/discussions/${discussionId}/resume`);
  }

  ameliorer(corps: AmeliorerRequest): Observable<AmeliorerResponse> {
    return this.post(`${this.url}/composer/ameliorer`, corps);
  }

  index(coursId: number): Observable<IndexCours> {
    return this.get(`${this.url}/index/${coursId}`);
  }

  reindexer(coursId: number): Observable<IndexCours> {
    return this.post(`${this.url}/index/${coursId}/reindexer`, {});
  }

  private get<T>(url: string, params: object = {}): Observable<T> {
    return this.http.get<ApiResponse<T>>(url, { params: parametres(params) }).pipe(map((r) => r.data));
  }

  private post<T>(url: string, corps: unknown): Observable<T> {
    return this.http.post<ApiResponse<T>>(url, corps).pipe(map((r) => r.data));
  }
}
