import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { DiscussionDetail, DiscussionRequest, DiscussionResume, DiscussionsFiltres, PageResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class ForumService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/forum/discussions`;

  findAll(filtres: DiscussionsFiltres = {}): Observable<PageResponse<DiscussionResume>> {
    let params = new HttpParams();
    for (const [cle, valeur] of Object.entries(filtres)) {
      if (valeur !== undefined && valeur !== null && valeur !== '') {
        params = params.set(cle, valeur);
      }
    }
    return this.http
      .get<ApiResponse<PageResponse<DiscussionResume>>>(this.url, { params })
      .pipe(map((r) => r.data));
  }

  create(body: DiscussionRequest): Observable<DiscussionDetail> {
    return this.http.post<ApiResponse<DiscussionDetail>>(this.url, body).pipe(map((r) => r.data));
  }
}
