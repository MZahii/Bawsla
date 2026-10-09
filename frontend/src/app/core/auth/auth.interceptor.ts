import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { API_BASE_URL } from '../config/api.config';
import { AuthService } from './auth.service';

/**
 * Ajoute "Authorization: Bearer <jwt>" aux appels vers la gateway uniquement,
 * et déconnecte l'utilisateur si la gateway répond 401 (token expiré/invalide).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const isApi = req.url.startsWith(API_BASE_URL);
  const isAuthCall = req.url.startsWith(`${API_BASE_URL}/auth/`);
  const token = auth.token;

  const request = isApi && !isAuthCall && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401 && isApi && !isAuthCall) {
        auth.logout('/login');
      }
      return throwError(() => err);
    }),
  );
};
