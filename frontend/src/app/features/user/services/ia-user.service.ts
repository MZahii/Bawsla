import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { MetriquesIa } from '../models/ia.model';

/** Machine Learning du module User (ai-service, via la gateway). Métriques et entraînement : ADMIN uniquement. */
@Injectable({ providedIn: 'root' })
export class IaUserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/ai/user`;

  metriques(): Observable<MetriquesIa> {
    return this.http.get<ApiResponse<MetriquesIa>>(`${this.url}/metriques`).pipe(map((r) => r.data));
  }

  /** Nouvel entraînement des trois modèles (30 s à 1 min). */
  entrainer(): Observable<MetriquesIa> {
    return this.http.post<ApiResponse<MetriquesIa>>(`${this.url}/entrainer`, {}).pipe(map((r) => r.data));
  }
}
