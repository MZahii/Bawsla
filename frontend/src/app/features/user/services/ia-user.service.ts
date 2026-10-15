import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { IaApprenant, MetriquesIa } from '../models/ia.model';

/**
 * Machine Learning du module User. Métriques et entraînement : ai-service (ADMIN). Résultats d'un apprenant :
 * user-service, qui rassemble ses indicateurs et interroge l'IA (jamais d'identifiant envoyé au modèle).
 */
@Injectable({ providedIn: 'root' })
export class IaUserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/ai/user`;
  private readonly users = `${API_BASE_URL}/users`;

  /** Mon profil et mes cours recommandés (jamais mon risque d'échec). Le premier appel peut prendre une minute. */
  monIa(): Observable<IaApprenant> {
    return this.http.get<ApiResponse<IaApprenant>>(`${this.users}/me/ia`).pipe(map((r) => r.data));
  }

  /** ENSEIGNANT ou ADMIN : risque d'échec et profil d'un apprenant. */
  iaDUnApprenant(id: number): Observable<IaApprenant> {
    return this.http.get<ApiResponse<IaApprenant>>(`${this.users}/${id}/ia`).pipe(map((r) => r.data));
  }

  metriques(): Observable<MetriquesIa> {
    return this.http.get<ApiResponse<MetriquesIa>>(`${this.url}/metriques`).pipe(map((r) => r.data));
  }

  /** Nouvel entraînement des trois modèles (30 s à 1 min). */
  entrainer(): Observable<MetriquesIa> {
    return this.http.post<ApiResponse<MetriquesIa>>(`${this.url}/entrainer`, {}).pipe(map((r) => r.data));
  }
}
