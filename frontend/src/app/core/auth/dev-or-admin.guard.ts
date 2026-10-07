import { inject, isDevMode } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Accès réservé à l'ADMIN, ou à tous en mode développement (ng serve).
 * Utilisé par la page /styleguide. Confort d'UI uniquement.
 */
export const devOrAdminGuard: CanActivateFn = () => {
  if (isDevMode() || inject(AuthService).hasRole('ADMIN')) {
    return true;
  }
  return inject(Router).createUrlTree(['/forbidden']);
};
