import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Quiz, QuizRequest } from '../models/quiz.model';

@Injectable({ providedIn: 'root' })
export class QuizService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/quiz`;

  findAll(coursId?: number): Observable<Quiz[]> {
    const params = coursId ? new HttpParams().set('coursId', coursId) : undefined;
    return this.http.get<ApiResponse<Quiz[]>>(this.url, { params }).pipe(map((r) => r.data));
  }

  create(body: QuizRequest): Observable<Quiz> {
    return this.http.post<ApiResponse<Quiz>>(this.url, body).pipe(map((r) => r.data));
  }
}
