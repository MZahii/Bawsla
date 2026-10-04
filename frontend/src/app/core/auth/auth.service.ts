import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map, tap } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { ApiResponse } from '../../shared/models/api-response.model';
import { AuthResponse, LoginRequest, RegisterRequest, Role, User } from '../../shared/models/user.model';

const TOKEN_KEY = 'bawsla.token';
const USER_KEY = 'bawsla.user';

/** Source de vérité de la session côté front (token + utilisateur courant). */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<User | null>(null);

  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null && this.userSignal() !== null);
  readonly role = computed(() => this.userSignal()?.role ?? null);

  constructor() {
    this.restore();
  }

  get token(): string | null {
    return this.tokenSignal();
  }

  login(request: LoginRequest): Observable<User> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${API_BASE_URL}/auth/login`, request)
      .pipe(map((res) => this.store(res.data)));
  }

  register(request: RegisterRequest): Observable<User> {
    return this.http
      .post<ApiResponse<AuthResponse>>(`${API_BASE_URL}/auth/register`, request)
      .pipe(map((res) => this.store(res.data)));
  }

  /** Recharge le profil depuis GET /api/users/me. */
  refreshMe(): Observable<User> {
    return this.http.get<ApiResponse<User>>(`${API_BASE_URL}/users/me`).pipe(
      map((res) => res.data),
      tap((user) => {
        this.userSignal.set(user);
        this.write(USER_KEY, JSON.stringify(user));
      }),
    );
  }

  logout(redirect = true): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    this.remove(TOKEN_KEY);
    this.remove(USER_KEY);
    if (redirect) {
      this.router.navigate(['/login']);
    }
  }

  hasRole(...roles: Role[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  private store(auth: AuthResponse): User {
    this.tokenSignal.set(auth.token);
    this.userSignal.set(auth.user);
    this.write(TOKEN_KEY, auth.token);
    this.write(USER_KEY, JSON.stringify(auth.user));
    return auth.user;
  }

  private restore(): void {
    const token = this.read(TOKEN_KEY);
    const user = this.read(USER_KEY);
    if (!token || !user || isExpired(token)) {
      this.logout(false);
      return;
    }
    try {
      this.userSignal.set(JSON.parse(user) as User);
      this.tokenSignal.set(token);
    } catch {
      this.logout(false);
    }
  }

  private read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private write(key: string, value: string): void {
    try {
      localStorage.setItem(key, value);
    } catch {
      /* stockage indisponible : la session reste en mémoire */
    }
  }

  private remove(key: string): void {
    try {
      localStorage.removeItem(key);
    } catch {
      /* ignoré */
    }
  }
}

/** Lecture (non vérifiée) de l'expiration : la vraie validation est faite par la gateway. */
function isExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now();
  } catch {
    return true;
  }
}
