# CLAUDE.md — Bawsla (البوصلة)

Ce fichier est lu par Claude Code (et par les humains) à chaque session. Il fait partie du **socle** : il ne se modifie que par pull request relue.

## Contexte

Plateforme e-learning **Bawsla** : mini-projet ESPRIT 5SAE9, module « IA for Software Engineering », 2026-2027.
Quatre étudiants, chacun propriétaire d'un module : **User**, **Cours**, **Quiz**, **Forum**.
Trois rôles : `ADMIN`, `ENSEIGNANT`, `ETUDIANT`.

Le socle (architecture, sécurité, conventions, contrats) est en place. Les fonctionnalités IA ne sont **pas** implémentées : seules les interfaces et des stubs existent dans `ai-service`.

## Règle d'or

> **Ne modifie que les fichiers de ton module. Toute modification du socle (core, gateway, CLAUDE.md, CONTRATS_API.md) passe par une pull request relue.**

| Membre | Fichiers qui lui appartiennent |
|---|---|
| user | `backend/user-service/**`, `ai-service/app/modules/user/**`, `frontend/src/app/features/user/**`, `prompts_journal/user/**` |
| cours | `backend/cours-service/**`, `ai-service/app/modules/cours/**`, `frontend/src/app/features/cours/**`, `prompts_journal/cours/**` |
| quiz | `backend/quiz-service/**`, `ai-service/app/modules/quiz/**`, `frontend/src/app/features/quiz/**`, `prompts_journal/quiz/**` |
| forum | `backend/forum-service/**`, `ai-service/app/modules/forum/**`, `frontend/src/app/features/forum/**`, `prompts_journal/forum/**` |

Font partie du **socle** (PR obligatoire) :
- `backend/eureka-server`, `backend/api-gateway`, `backend/pom.xml`
- le package `common` de chaque service (il doit rester identique dans les 4 services)
- `ai-service/app/core`, `ai-service/app/main.py`, `ai-service/requirements.txt`
- `frontend/src/app/core`, `frontend/src/app/shared`, `frontend/src/app/pages`, `app.routes.ts`, `app.config.ts`
- `docker-compose.yml`, `docker/`, `.env.example`
- `CLAUDE.md`, `CONTRATS_API.md`, `README.md`

## Règles de sécurité (non négociables)

- **aucune clé API dans le front ou le code, aucune donnée personnelle envoyée au LLM, tous les appels LLM passent par app/core de ai-service**
- Les secrets (`JWT_SECRET`, `LLM_API_KEY`, mots de passe) vivent uniquement dans `.env`, qui n'est jamais commité. Seul `.env.example` est versionné, avec des valeurs factices.
- Le JWT est validé **uniquement par l'api-gateway**. Elle supprime tout en-tête `X-User-*` reçu du client, puis ajoute `X-User-Id` et `X-User-Role` à partir du token. Les services leur font confiance : ils ne doivent donc **jamais** être exposés directement en dehors de la gateway.
- L'autorisation métier se fait **côté backend** avec `CurrentUser.requireAnyRole(...)` et `requireOwnerOrAdmin(...)`. Les guards Angular ne servent qu'au confort d'affichage.
- Tout texte destiné au LLM passe d'abord par `Sanitizer` (`app/core/sanitizer.py`), qui masque emails et téléphones et tronque. Ne jamais envoyer nom, prénom, email ou identifiant d'un utilisateur dans un prompt.
- Les mots de passe sont hachés en BCrypt et ne sont jamais renvoyés dans une réponse.

## Stack et versions

| Couche | Technologie |
|---|---|
| Front | Angular 21.2 (standalone, signals, zoneless), Angular Material 21.2, SCSS |
| Back | Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 (Gateway Server WebFlux, Eureka, OpenFeign), Spring Data JPA, Maven 3.9 |
| Auth | JWT HMAC (jjwt 0.12.7), BCrypt (spring-security-crypto) |
| IA | Python 3.11+, FastAPI 0.142, Pydantic 2.13, Uvicorn 0.54 (stubs uniquement) |
| BD | MySQL 8.4 (Docker), **une base par service** |

## Ports et bases

| Composant | Port | Base MySQL | Nom Eureka |
|---|---|---|---|
| eureka-server | 8761 | — | — |
| api-gateway | 8080 | — | API-GATEWAY |
| user-service | 8081 | `bawsla_user` | USER-SERVICE |
| cours-service | 8082 | `bawsla_cours` | COURS-SERVICE |
| quiz-service | 8083 | `bawsla_quiz` | QUIZ-SERVICE |
| forum-service | 8084 | `bawsla_forum` | FORUM-SERVICE |
| ai-service | 8000 | — | (URL statique, hors Eureka) |
| frontend | 4200 | — | — |
| MySQL | 3306 | — | — |

Le front ne connaît **que** la gateway (`http://localhost:8080/api`).

## Structure

```
bawsla/
├── backend/                 # Maven multi-module (pom agrégateur)
│   ├── eureka-server/
│   ├── api-gateway/         # routes, CORS, filtre JWT
│   └── <x>-service/src/main/java/tn/esprit/bawsla/<x>/
│       ├── common/          # SOCLE : ApiResponse, ApiError, exceptions, CurrentUser
│       ├── controller/  service/  repository/  entity/  dto/
│       └── client/          # clients Feign vers d'autres services
├── ai-service/app/
│   ├── core/                # SOCLE : config, llm_client, sanitizer, errors, security, schemas
│   └── modules/{user,cours,quiz,forum}/   router.py + schemas.py
├── frontend/src/app/
│   ├── core/                # SOCLE : auth (service, interceptor, guards), layout, config
│   ├── shared/              # SOCLE : models, composants réutilisables
│   ├── pages/               # SOCLE : login, register, home, forbidden, not-found
│   └── features/{user,cours,quiz,forum}/   <module>.routes.ts, pages/, services/, models/
├── prompts_journal/{user,cours,quiz,forum}/   JOURNAL.md + captures/
├── docker/mysql/init/       # création des 4 bases
├── docker-compose.yml  .env.example
└── CLAUDE.md  README.md  CONTRATS_API.md
```

## Règles d'architecture

- **Une base par service.** Aucune clé étrangère entre bases : on stocke l'**ID** (`coursId`, `enseignantId`, `auteurId`, `etudiantId`…) et on vérifie l'existence par appel REST (Feign) si nécessaire. Exemple de référence : `quiz-service/client/CoursClient`.
- Appels entre services : **OpenFeign** avec `@FeignClient(name = "<x>-service")`, résolu par Eureka. `FeignHeadersInterceptor` propage `X-User-*`. Une erreur Feign se traduit en `BadRequestException` (ressource liée absente) ou `ServiceUnavailableException`, jamais en 500 brut.
- Le front et les services Java appellent l'IA **via la gateway** (`/api/ai/**`). Seul `ai-service/app/core/llm_client.py` parle à un fournisseur LLM.
- Toute nouvelle route inter-modules est d'abord décrite dans `CONTRATS_API.md` (PR), puis implémentée.
- `ddl-auto: update` en dev. Pas de données de démo en dur, hormis le compte ADMIN créé au démarrage.

## Format des réponses API

Succès (`ApiResponse<T>`) :
```json
{ "success": true, "data": { }, "message": "optionnel", "timestamp": "2026-10-04T22:08:10Z" }
```
Erreur (`ApiError`, produit par `GlobalExceptionHandler`) :
```json
{ "success": false, "status": 400, "error": "BAD_REQUEST", "message": "Données invalides",
  "path": "/api/cours", "timestamp": "…", "details": { "titre": "Le titre est obligatoire" } }
```
- Les champs `null` sont omis.
- Pour signaler une erreur, on lève une exception de `common.exception` : `ResourceNotFoundException` (404), `BadRequestException` (400), `ConflictException` (409), `ForbiddenException` (403), `UnauthorizedException` (401) ou `ServiceUnavailableException` (503). Jamais de `ResponseEntity` d'erreur construit à la main.
- `ai-service` respecte la même enveloppe, en camelCase.

## Conventions de nommage

**Java**
- Package racine `tn.esprit.bawsla.<module>`. Classes en PascalCase, méthodes et champs en camelCase, constantes en UPPER_SNAKE.
- Suffixes : `XxxController`, `XxxService`, `XxxRepository`, `XxxRequest` / `XxxResponse` (DTO en `record`), `XxxClient` (Feign), `XxxDto` (DTO d'un autre service).
- Entités : nom métier français au singulier (`Cours`, `Quiz`, `Discussion`), sans suffixe. Ne jamais exposer une entité dans une réponse : passer par `XxxResponse.from(entity)`.
- Injection par constructeur (`private final`), pas de `@Autowired` sur les champs.
- URLs : `/api/<module>/...`, ressources au pluriel ou au nom du module, en kebab-case (`/notions-ratees`).

**Angular**
- Fichiers en kebab-case, sans suffixe de type (convention Angular 21) : `cours-list.ts` contient la classe `CoursList`. Les services gardent le suffixe : `cours.service.ts` contient `CoursService`.
- Composants standalone uniquement ; état local en `signal()` ; injection avec `inject()`.
- Chaque feature expose `<MODULE>_ROUTES` dans `<module>.routes.ts`, chargé en lazy depuis `app.routes.ts`.
- Les appels HTTP passent par un service de la feature et utilisent `API_BASE_URL`. Jamais d'URL de service en dur.
- Interfaces de modèles miroir des DTO backend, dans `features/<x>/models/`.

**Python**
- PEP 8 : modules et fonctions en snake_case, classes en PascalCase.
- Schémas Pydantic hérités de `CamelModel` (JSON en camelCase, Python en snake_case).
- Un `APIRouter` par module, préfixe `/api/ai/<module>`. Dépendances injectées par `Depends(get_llm_client)`, `Depends(get_sanitizer)` et `Depends(get_current_user)`.

## Git

- Branches : `main` (stable, démo), `develop` (intégration), `feature/<module>` (une par membre). Pour un sous-sujet : `feature/<module>-<sujet>`.
- Flux : `feature/<module>` → PR vers `develop` (1 relecture) → `develop` → `main` aux jalons.
- Messages de commit **Conventional Commits** : `feat(cours): upload du PDF`, `fix(quiz): ...`, `docs: ...`, `chore: ...`, `refactor: ...`, `test: ...`.

## Commandes utiles

```bash
docker compose up -d --build                           # toute la stack → http://localhost:4200
docker compose up -d mysql                             # MySQL seul (dev local)
cd backend && ./mvnw -q package -DskipTests            # build back (JDK 21+)
cd backend/<svc> && ../mvnw spring-boot:run            # lancer un service
cd ai-service && .venv/Scripts/python -m uvicorn app.main:app --port 8000 --reload
cd frontend && npx ng serve                            # http://localhost:4200
cd frontend && npx ng build
```

## Journal des prompts

Chaque membre documente ses prompts IA dans `prompts_journal/<module>/JOURNAL.md`, avec ses captures dans `captures/` (voir `prompts_journal/README.md`).
