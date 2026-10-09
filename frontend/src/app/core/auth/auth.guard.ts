import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/**
 * Exige un utilisateur connecté. Un visiteur qui arrive sur la racine voit l'accueil public
 * (/bienvenue) ; ailleurs, il est envoyé vers /login?returnUrl=... pour revenir ensuite à la page demandée.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAuthenticated()) {
    return true;
  }
  const router = inject(Router);
  if (state.url === '/' || state.url === '') {
    return router.createUrlTree(['/bienvenue']);
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Pour /login et /register : un utilisateur déjà connecté est renvoyé à l'accueil. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/']) : true;
};
