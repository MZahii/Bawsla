import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Role } from '../../shared/models/user.model';
import { AuthService } from './auth.service';

/** Espace d'accueil de chaque rôle : chacun a sa propre interface. */
export const ESPACE: Record<Role, string> = { ETUDIANT: '/app', ENSEIGNANT: '/studio', ADMIN: '/admin' };

/**
 * Restreint une route à certains rôles. Un utilisateur d'un autre rôle est renvoyé vers son propre espace.
 * Rappel : c'est du confort d'UI. La vraie autorisation est vérifiée côté backend.
 */
export function roleGuard(...roles: Role[]): CanActivateFn {
  return (_route, state) => {
    const auth = inject(AuthService);
    const router = inject(Router);
    if (!auth.isAuthenticated()) {
      return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
    }
    const role = auth.role();
    return auth.hasRole(...roles) ? true : router.createUrlTree([role ? ESPACE[role] : '/bienvenue']);
  };
}

/** Racine « / » : le visiteur voit l'accueil public, l'utilisateur connecté arrive dans son espace. */
export const espaceGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const role = auth.role();
  return inject(Router).createUrlTree([auth.isAuthenticated() && role ? ESPACE[role] : '/bienvenue']);
};
