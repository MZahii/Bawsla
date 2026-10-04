import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { API_BASE_URL } from '../../../core/config/api.config';
import { ApiResponse } from '../../../shared/models/api-response.model';
import { User } from '../../../shared/models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly http = inject(HttpClient);
  private readonly url = `${API_BASE_URL}/users`;

  /** ADMIN uniquement (403 sinon). */
  findAll(): Observable<User[]> {
    return this.http.get<ApiResponse<User[]>>(this.url).pipe(map((r) => r.data));
  }
}
