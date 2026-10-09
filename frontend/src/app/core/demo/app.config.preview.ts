/**
 * CONFIGURATION D'APERÇU UNIQUEMENT (build `ng build --configuration preview`).
 * Remplace app.config.ts pour publier une maquette cliquable sans backend :
 * routage par hash et faux backend qui répond aux appels de l'api-gateway.
 * À NE PAS intégrer dans le projet.
 */
import { HttpInterceptorFn, HttpResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { provideRouter, withComponentInputBinding, withHashLocation } from '@angular/router';
import { of } from 'rxjs';

import { routes } from '../../app.routes';
import { authInterceptor } from '../auth/auth.interceptor';
import { ThemeService } from '../theme/theme.service';
import { Role } from '../../shared/models/user.model';

const TOKEN = 'demo.eyJleHAiOjk5OTk5OTk5OTl9.demo';

const demoBackend: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/')) return next(req);
  const body = (req.body ?? {}) as { email?: string; prenom?: string; nom?: string; role?: Role };
  const email = (body.email ?? '').toLowerCase();
  const role: Role = body.role ?? (email.startsWith('admin') ? 'ADMIN' : email.startsWith('prof') ? 'ENSEIGNANT' : 'ETUDIANT');
  const noms: Record<Role, [string, string]> = { ETUDIANT: ['Malek', 'Ouji'], ENSEIGNANT: ['Imen', 'Ayari'], ADMIN: ['Admin', 'Bawsla'] };
  const user = {
    id: 1,
    prenom: body.prenom || noms[role][0],
    nom: body.nom || noms[role][1],
    email: email || 'malek.ouji@gmail.com',
    role,
    actif: true,
    dateCreation: '2026-09-02',
  };
  const data = req.url.includes('/auth/') ? { token: TOKEN, tokenType: 'Bearer', expiresIn: 86400, user } : [];
  return of(new HttpResponse({ status: 200, body: { success: true, data } }));
};

/** Petit bandeau d'aperçu : explique comment entrer dans la maquette avec chaque rôle. */
function showDemoHint(): void {
  const hint = document.createElement('p');
  hint.setAttribute('role', 'note');
  hint.textContent =
    'Aperçu sans backend : connecte-toi avec n’importe quel email et mot de passe. ' +
    'Un email qui commence par « prof » ouvre le studio formateur, « admin » l’administration.';
  Object.assign(hint.style, {
    position: 'fixed',
    left: '50%',
    bottom: '12px',
    transform: 'translateX(-50%)',
    zIndex: '2000',
    margin: '0',
    maxWidth: 'min(560px, calc(100vw - 32px))',
    padding: '8px 14px',
    borderRadius: '8px',
    background: 'var(--color-gold-100)',
    color: 'var(--color-gold-700)',
    border: '1px solid var(--color-gold-500)',
    font: '500 13px/18px var(--font-body)',
    boxShadow: 'var(--shadow-md)',
  });
  const close = document.createElement('button');
  close.type = 'button';
  close.textContent = '×';
  close.setAttribute('aria-label', 'Fermer le bandeau d’aperçu');
  Object.assign(close.style, { marginLeft: '10px', border: '0', background: 'none', color: 'inherit', font: '700 16px/1 sans-serif', cursor: 'pointer' });
  close.addEventListener('click', () => hint.remove());
  hint.append(close);
  document.body.append(hint);
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withHashLocation()),
    provideHttpClient(withInterceptors([authInterceptor, demoBackend])),
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass('material-symbols-rounded');
      inject(ThemeService);
      showDemoHint();
    }),
  ],
};
