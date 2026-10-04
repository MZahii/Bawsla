import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Cours, CoursRequest, Niveau } from '../models/cours.model';

@Injectable({ providedIn: 'root' })
export class CoursService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/cours`;

  findAll(filters: { categorie?: string; niveau?: Niveau } = {}): Observable<Cours[]> {
    let params = new HttpParams();
    if (filters.categorie) params = params.set('categorie', filters.categorie);
    if (filters.niveau) params = params.set('niveau', filters.niveau);
    return this.http.get<ApiResponse<Cours[]>>(this.url, { params }).pipe(map((r) => r.data));
  }

  findById(id: number): Observable<Cours> {
    return this.http.get<ApiResponse<Cours>>(`${this.url}/${id}`).pipe(map((r) => r.data));
  }

  create(body: CoursRequest): Observable<Cours> {
    return this.http.post<ApiResponse<Cours>>(this.url, body).pipe(map((r) => r.data));
  }

  update(id: number, body: CoursRequest): Observable<Cours> {
    return this.http.put<ApiResponse<Cours>>(`${this.url}/${id}`, body).pipe(map((r) => r.data));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.url}/${id}`).pipe(map(() => undefined));
  }
}
