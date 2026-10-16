import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import {
  Certificat,
  HistoriqueLigne,
  NotionRatee,
  Passage,
  Quiz,
  QuizDetail,
  QuizRequest,
  RattrapageResultat,
  Remediation,
  StatistiquesQuiz,
  StudioLigne,
  TentativeRequest,
  TentativeResultat,
  TypeQuiz,
} from '../models/quiz.model';

/** Toutes les routes de quiz-service (/api/quiz/**), via la gateway. */
@Injectable({ providedIn: 'root' })
export class QuizService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/quiz`;

  // ---------- Formateur ----------

  findAll(coursId?: number, type?: TypeQuiz): Observable<Quiz[]> {
    let params = new HttpParams();
    if (coursId) params = params.set('coursId', coursId);
    if (type) params = params.set('type', type);
    return this.get<Quiz[]>(this.url, params);
  }

  findById(id: number): Observable<Quiz> {
    return this.get<Quiz>(`${this.url}/${id}`);
  }

  mesQuiz(): Observable<StudioLigne[]> {
    return this.get<StudioLigne[]>(`${this.url}/mes-quiz`);
  }

  detail(id: number): Observable<QuizDetail> {
    return this.get<QuizDetail>(`${this.url}/${id}/detail`);
  }

  statistiques(id: number): Observable<StatistiquesQuiz> {
    return this.get<StatistiquesQuiz>(`${this.url}/${id}/statistiques`);
  }

  create(body: QuizRequest): Observable<Quiz> {
    return this.http.post<ApiResponse<Quiz>>(this.url, body).pipe(map((r) => r.data));
  }

  update(id: number, body: QuizRequest): Observable<Quiz> {
    return this.http.put<ApiResponse<Quiz>>(`${this.url}/${id}`, body).pipe(map((r) => r.data));
  }

  publier(id: number): Observable<Quiz> {
    return this.post<Quiz>(`${this.url}/${id}/publier`);
  }

  depublier(id: number): Observable<Quiz> {
    return this.post<Quiz>(`${this.url}/${id}/depublier`);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(map(() => undefined));
  }

  // ---------- Apprenant ----------

  passage(id: number): Observable<Passage> {
    return this.get<Passage>(`${this.url}/${id}/passage`);
  }

  soumettre(id: number, body: TentativeRequest): Observable<TentativeResultat> {
    return this.post<TentativeResultat>(`${this.url}/${id}/tentatives`, body);
  }

  historique(): Observable<HistoriqueLigne[]> {
    return this.get<HistoriqueLigne[]>(`${this.url}/tentatives/mes`);
  }

  notionsRatees(etudiantId: number): Observable<NotionRatee[]> {
    return this.get<NotionRatee[]>(`${this.url}/notions-ratees`, new HttpParams().set('etudiantId', etudiantId));
  }

  /** Explication IA d'une mauvaise réponse (le serveur appelle l'IA une seule fois par erreur). */
  remedier(reponseId: number): Observable<Remediation> {
    return this.post<Remediation>(`${this.url}/reponses/${reponseId}/remediation`);
  }

  rattrapage(notionRateeId: number, indexChoix: number): Observable<RattrapageResultat> {
    return this.post<RattrapageResultat>(`${this.url}/remediations/${notionRateeId}/rattrapage`, { indexChoix });
  }

  // ---------- Certificats ----------

  mesCertificats(): Observable<Certificat[]> {
    return this.get<Certificat[]>(`${this.url}/certificats/mes`);
  }

  tousLesCertificats(): Observable<Certificat[]> {
    return this.get<Certificat[]>(`${this.url}/certificats`);
  }

  /** Route publique : appelable sans compte (public-paths de la gateway). */
  verifierCertificat(numero: string): Observable<Certificat> {
    return this.get<Certificat>(`${this.url}/certificats/verification/${encodeURIComponent(numero)}`);
  }

  private get<T>(url: string, params?: HttpParams): Observable<T> {
    return this.http.get<ApiResponse<T>>(url, { params }).pipe(map((r) => r.data));
  }

  private post<T>(url: string, body: unknown = {}): Observable<T> {
    return this.http.post<ApiResponse<T>>(url, body).pipe(map((r) => r.data));
  }
}
