import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../../shared/models/user.model';
import { AuthService } from './auth.service';

/**
 * Restreint une route à certains rôles. Usage :
 * { path: 'users', canActivate: [roleGuard('ADMIN')], loadChildren: ... }
 * Rappel : c'est du confort d'UI. La vraie autorisation est vérifiée côté backend.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login']);
    }
    return auth.hasRole(...roles) ? true : router.createUrlTree(['/forbidden']);
  };
}
