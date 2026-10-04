import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { Discussion, DiscussionRequest } from '../models/discussion.model';

@Injectable({ providedIn: 'root' })
export class ForumService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/forum/discussions`;

  findAll(coursId?: number): Observable<Discussion[]> {
    const params = coursId ? new HttpParams().set('coursId', coursId) : undefined;
    return this.http.get<ApiResponse<Discussion[]>>(this.url, { params }).pipe(map((r) => r.data));
  }

  create(body: DiscussionRequest): Observable<Discussion> {
    return this.http.post<ApiResponse<Discussion>>(this.url, body).pipe(map((r) => r.data));
  }
}
