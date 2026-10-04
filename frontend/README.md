# Bawsla — frontend

Angular 21 (standalone, signals, zoneless) avec Angular Material. Conventions et propriété des fichiers : voir [../CLAUDE.md](../CLAUDE.md).

```bash
npm install
npx ng serve        # http://localhost:4200 (la gateway doit tourner sur :8080)
npx ng build        # sortie dans dist/frontend
```

```
src/app/
├── core/                 # SOCLE (PR obligatoire)
│   ├── auth/             # AuthService (signals + localStorage), authInterceptor, authGuard/guestGuard, roleGuard
│   ├── config/           # API_BASE_URL (gateway uniquement)
│   └── layout/           # sidenav + menu filtré par rôle (menu.ts)
├── shared/               # SOCLE : models (ApiResponse, User, Role), composants (empty-state)
├── pages/                # SOCLE : login, register, home, forbidden, not-found
└── features/<module>/    # propriété du membre : <module>.routes.ts, pages/, services/, models/
```

Ajouter une page à son module : créer le composant dans `features/<module>/pages/`, puis l'ajouter dans `<module>.routes.ts`. Aucune modification du socle n'est nécessaire. Ajouter une entrée au menu latéral (`core/layout/menu.ts`) passe par une PR.
