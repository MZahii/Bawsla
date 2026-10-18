# Rapport d'intégration — branche `integration/finale`

Analyse et tests réalisés le 2026-10-18 sur `integration/finale-suite` (créée depuis `origin/integration/finale`, commit `c94a225`), **sans aucune correction de code**. Docker n'a pas été utilisé (il ne fonctionne pas sur ce poste) : tout ce qui demande la stack complète est listé en §7.

> **Mise à jour :** le tableau « Statut des points » en tête du §6 indique quels problèmes sont corrigés sur `integration/finale` depuis cette analyse.

Légende des efforts : **S** < 1 h · **M** ½ journée · **L** 1 à 2 jours.

---

## 1. Résumé

- **Le code compile et toutes les suites de tests exécutables passent** : backend 449/449, ai-service 667 réussis + 2 ignorés, build Angular OK (1 avertissement `rootDir`).
- **Les liaisons entre modules sont cohérentes** : les 8 contrats ont leur fournisseur, les DTO correspondent des deux côtés, les noms Eureka et Feign concordent, et tous les appels HTTP du front ont une route backend (y compris les 67 appels du forum).
- **L'application n'est pas encore « démontrable telle quelle »** : 12 écrans restent sur des données fictives codées en dur (`core/demo`), dont les 3 tableaux de bord d'accueil, et les envois de fichiers de cours vont probablement échouer en Docker.
- Il y a plusieurs trous d'autorisation entre modules : un formateur non validé peut publier, un formateur peut créer un examen sur le cours d'un autre, et les routes IA sont ouvertes à tout utilisateur connecté. Le visiteur ne peut pas parcourir le catalogue public.
- Bilan : **2 BLOQUANT, 11 IMPORTANT, 10 MINEUR**. Aucun problème bloquant dans les chemins Forum.

---

## 2. État des branches

### 2.1 Sauvegarde et branche de travail

| Action | Résultat |
|---|---|
| `git status` initial | propre (aucune modification non commitée) |
| `git fetch --all --prune` | OK (`integration/finale` nouvelle, `feature/user-ia` et `integration/user-cours-quiz` mises à jour) |
| Tag `forum-v1` | créé sur `origin/feature/forum` = `3a707ea` et poussé (`* [new tag] forum-v1 -> forum-v1`) |
| Branche de travail | `integration/finale-suite` depuis `origin/integration/finale` |

Remarque : GitHub signale `This repository moved. Please use the new location: https://github.com/MZahii/Bawsla.git`. Le push fonctionne, mais il vaut mieux mettre à jour l'URL du remote (`git remote set-url origin …`).

### 2.2 Branches distantes fusionnées dans `integration/finale`

Commande : `git merge-base --is-ancestor origin/<branche> HEAD` pour chaque branche distante.

| Branche | Fusionnée ? |
|---|---|
| chore/gateway-verification-certificat, develop, docs/contrat-quiz-ia, docs/contrat-user-ia, feature/cours, feature/forum, feature/quiz, feature/socle-llm-gemini, feature/user, feature/user-ia, fix/front-logo-tsconfig, integration/quiz-cours, integration/user-cours-quiz-forum, main | **oui** |
| `integration/user-cours-quiz` | **non** (+4 commits) |

Les 4 commits de `integration/user-cours-quiz` (`5c966f3`, `b9840af`, `4da7045`, `4f71cfa`) sont **uniquement des merges** de `feature/user-ia`. `git cherry` ne trouve aucun patch absent de HEAD. Sur les fichiers qu'ils touchent, la seule différence est **une ligne vide** dans `prompts_journal/user/JOURNAL.md` (HEAD en a une de plus). **Aucun contenu réel à récupérer.**

### 2.3 Fichiers du Forum

`git diff --stat origin/feature/forum HEAD -- backend/forum-service frontend/src/app/features/forum ai-service/app/modules/forum ai-service/tests/forum prompts_journal/forum` → **sortie vide**. Les fichiers du Forum sont **identiques** à `origin/feature/forum`.

---

## 3. Résultats des tests

### 3.1 Backend (`cd backend && ./mvnw test -fae`)

Le JDK du PATH est Java 17 alors que le projet exige Java 21. Les tests ont été lancés avec le JDK 21 déjà installé dans `C:\Users\hp\.jdks\jdk-21.0.12.1+1` (`JAVA_HOME` positionné pour la commande). Résultat : `BUILD SUCCESS`, durée 4 min 31 s.

| Service | Lancés | Réussis | Échoués | Erreurs | Ignorés | Remarque |
|---|---|---|---|---|---|---|
| eureka-server | 0 | — | — | — | — | aucun dossier `src/test` |
| api-gateway | 0 | — | — | — | — | aucun dossier `src/test` : filtre JWT et routes non testés |
| user-service | 101 | 101 | 0 | 0 | 0 | tests unitaires Mockito uniquement (`@ExtendWith(MockitoExtension.class)`), sans contexte Spring ni base |
| cours-service | 170 | 170 | 0 | 0 | 0 | profil `test`, H2 en mémoire (`src/test/resources/application-test.yml`) |
| quiz-service | 17 | 17 | 0 | 0 | 0 | H2 en mémoire |
| forum-service | 161 | 161 | 0 | 0 | 0 | H2 en mémoire |
| **Total** | **449** | **449** | **0** | **0** | **0** | |

Aucun test n'utilise MySQL, Eureka ou un vrai appel Feign : l'intégration réelle entre services n'est donc **pas** couverte par ces tests (voir §7).

### 3.2 ai-service (`pytest -q`)

- Python 3.14.0. Nouveau venv `ai-service/venv`, ignoré par git (`.gitignore:27`), puis `pip install -r requirements-dev.txt` : OK.
- Premier essai dans le dossier temporaire refusé par Windows (chemin > 260 caractères dans `elevenlabs`) : le venv doit rester sur un chemin court.
- Le `.venv` existant **ne suffit pas** : il ne contient pas `scikit-learn`, `pandas`, `numpy` ni `joblib` (ML du module User).

| Module | Collectés | Réussis | Échoués | Ignorés | Raison des tests ignorés |
|---|---|---|---|---|---|
| core | 112 | 112 | 0 | 0 | |
| cours | 225 | 223 | 0 | 2 | `tests/cours/conftest.py:99` (test lent, `RUN_SLOW=1`) ; `tests/cours/test_resume_local.py:437` (PDF de test absent) |
| quiz | 139 | 139 | 0 | 0 | |
| user | 87 | 87 | 0 | 0 | |
| forum | 106 | 106 | 0 | 0 | |
| **Total** | **669** | **667** | **0** | **2** | durée 158 s |

Un seul avertissement : `DeprecationWarning: '_UnionGenericAlias'` dans `google/genai/types.py:42` (bibliothèque tierce, Python 3.17). Aucun appel LLM réel : les tests utilisent des faux clients.

### 3.3 Frontend

| Étape | Résultat |
|---|---|
| `npm ci` | OK. `4 vulnerabilities (2 high, 2 critical)`, **toutes dans l'outillage de dev** : `piscina` (via `@angular/build`) et `@modelcontextprotocol/sdk` (via `@angular/cli`). `npm audit --omit=dev` → `found 0 vulnerabilities` |
| `npx ng build` | **OK** (`Application bundle generation complete. [32.911 seconds]`), 1 avertissement : `Duplicate key "rootDir" in object literal`, `tsconfig.app.json:8:4` (original en `tsconfig.app.json:6:4`) → **confirmé** |
| `npx ng test --watch=false` | **non exécutable** : `Error: Unknown argument: watch` ; `npx ng test` → `Cannot determine project or target for command.` Il n'y a pas de cible `test` dans `angular.json` (seuls `@angular/build:application` et `dev-server`) et **0 fichier `*.spec.ts`** dans `src` |

---

## 4. Contrats et liaisons entre modules

### 4.1 Contrats 1 à 8

| Contrat | Fournisseur | Consommateur(s) vérifié(s) | Statut | Détail |
|---|---|---|---|---|
| 1. `GET /api/cours/{id}` | `CoursController.java:47` (`CoursResponse`) | quiz `client/CoursClient.java:18` (création de quiz `QuizService.java:224`, certificat `CertificatService.java:69`) ; forum `client/CoursClient.java:20` ; front `cours.service.ts:25` | **OK** | Les DTO consommateurs (`id, titre, categorie, niveau, enseignantId`) sont un sous-ensemble de `CoursResponse`. `niveau` est un enum côté fournisseur et un `String` côté consommateur : compatible en JSON |
| 2. `GET /api/cours/{id}/texte` | `CoursController.java:53`, `CoursService.java:149` | forum `CoursClient.java:24` (`CoursTexteDto(coursId, texte, stub)`) ; front `cours.service.ts:125` | **OK** | ai-service est déclaré consommateur mais ne l'appelle pas : c'est cours-service qui envoie le texte (`IaClient` cours). Documentation à ajuster |
| 3. `GET /api/quiz/notions-ratees` | `PassageController.java:74` | user `QuizClient.java:16` (`IndicateursService.java:104`, `IaGenerativeService.java:302`) ; forum `QuizClient.java:15` ; front `quiz.service.ts:90` | **OK** | `NotionRateeDto(notion, coursId, nbErreurs, tauxReussite)` identique dans quiz, user et forum. `etudiantId` absent → `MissingServletRequestParameterException` → 400 (`GlobalExceptionHandler`, l. 45-46). cours-service est déclaré consommateur mais n'appelle pas cette route |
| 4. `GET /api/users/{id}` | `UserController.java:129` (`UserPublicDto`) | forum `UserClient.java:13` ; front `formateur.service.ts:29` | **KO** | Le contrat impose : *« cours-service doit refuser la publication d'un cours (403) si `formateurValide` est `false` »* (`CONTRATS_API.md:107`). cours-service **n'a aucun client vers user-service** et `CoursService.changerStatut` (`CoursService.java:139-146`) ne vérifie que la présence d'un chapitre. Voir I2 |
| 5. `/api/ai/{module}/…` | `ai-service/app/main.py:19-22` + routeurs des modules | user : `AiClient.java:22-52` (8 routes) + front `ia-user.service.ts:35,40` (métriques, entraîner) ; cours : `IaClient.java:22-62` (9 routes, toutes présentes dans `router.py` et ses sous-routeurs) ; quiz : `remedier` par `quiz/IaClient.java:21`, `generer` par le front `quiz-ia.service.ts:16` ; forum : `IaClient.java:21-53` (9 routes) | **OK** | `POST /api/ai/cours/resume` et `POST /api/ai/forum/questions-similaires` n'ont aucun appelant (stubs historiques). Le contrat ne décrit pas les 8 vraies routes IA du module Cours (M4). `CONTRATS_API.md:163` dit que `generer` est appelée par quiz-service, alors que c'est le front qui l'appelle (M4) |
| 6. `GET /api/quiz/tentatives` | `PassageController.java:68` (`TentativeLigne`) | user `QuizClient.java:27` (`IndicateursService.java:87`) | **OK** | `TentativeDto` (user) et `TentativeLigne` (quiz) ont les mêmes 7 champs, de mêmes types |
| 7. `GET /api/cours/inscriptions` et `/{id}/inscriptions` | `InscriptionController.java:84,90` (`InscriptionResponse`) | user `CoursClient.java:17,21` (`IndicateursService.java:106`, `IaGenerativeService.java:312`, `ApprenantsService.java:86`) ; front `cours.service.ts:81` | **OK** | `InscriptionDto` (user) identique à `InscriptionResponse` (7 champs) |
| 8. `GET /api/cours/inscriptions/toutes` | `InscriptionController.java:78` (`InscriptionResumeResponse`) | **aucun** (seul `InscriptionApiTest.java:519-528` l'appelle) | **à vérifier** | Fournisseur en place, mais user-service ne l'appelle pas : le filtrage collaboratif reste entraîné sur le jeu simulé (`ai-service/app/modules/user/ml/donnees/*.csv`, `donneesSimulees: true`). Voir I10 |

### 4.2 Appels Feign entre services

| Appelant → cible | Méthode et chemin | Endpoint cible | Statut |
|---|---|---|---|
| quiz → cours | `GET /api/cours/{id}` | `CoursController.java:47` | OK |
| forum → cours | `GET /api/cours/{id}`, `/{id}/texte`, `/{id}/chapitres` | `CoursController.java:47,53`, `ChapitreController.java:35` | OK (`/chapitres` hors contrat, voir I11) |
| forum → quiz | `GET /api/quiz/notions-ratees?etudiantId` | `PassageController.java:74` | OK |
| forum → user | `GET /api/users/{id}` | `UserController.java:129` | OK (`UserDto` sans `formateurValide` : champ inconnu ignoré) |
| user → cours | `GET /api/cours/inscriptions`, `/{id}/inscriptions`, `GET /api/cours` | `InscriptionController.java:84,90`, `CoursController.java:39` | OK (`GET /api/cours` hors contrat, voir I11) |
| user → quiz | `GET /api/quiz/notions-ratees`, `/tentatives`, `/tentatives/mes` | `PassageController.java:74,68,62` | OK (`/tentatives/mes` hors contrat, voir I11) |
| user, cours, quiz, forum → ai (par la gateway) | `POST /api/ai/<module>/…` avec `Authorization` | routeurs ai-service | OK |

Pannes d'un autre service : chaque service traduit les erreurs Feign en exceptions métier. On compte les `catch (FeignException…)` et les `new ServiceUnavailableException(` (hors `common`) : user 2 et 5, cours 1 et 13, quiz 4 et 6. Exemple : `QuizService.java:227-230` (404 → 400, autre erreur → 503) ; `CertificatService.java:67-73` (une panne de cours ne bloque pas la délivrance). **Le comportement réel en panne reste à tester avec la stack** (§7).

### 4.3 Frontend → backend

- **Hors forum** : chaque URL de `core/auth/auth.service.ts` et de `features/{cours,quiz,user}/services/*.service.ts` a été comparée aux `@*Mapping` des contrôleurs : **toutes ont une route correspondante** (même méthode, même chemin).
- **Forum** : script de comparaison (lecture seule) des appels `${this.url}/…` des 3 services du forum avec les 93 routes de forum-service. Les 67 appels correspondent. Les 2 chemins non résolus automatiquement sont composés à l'exécution et existent bien : `forum.service.ts:143-144` → `ReponseController.java:97,105` ; `moderation.service.ts:97` → `ReponseController.java:79,87,126,134`.
- Aucune URL en dur : `API_BASE_URL = '/api'` (`core/config/api.config.ts`), transmise à la gateway par nginx ou le proxy de `ng serve` (M7 corrigé).

### 4.4 Gateway, rôles, Eureka, Docker

| Point | Constat | Statut |
|---|---|---|
| Routes gateway | `/api/auth/**`, `/api/users/**` → USER-SERVICE ; `/api/cours/**`, `/api/quiz/**`, `/api/forum/**` ; `/api/ai/**` → `AI_SERVICE_URL` (`api-gateway/application.yml:17-41`) | OK |
| Propagation du JWT | `JwtAuthenticationFilter.java:53-58` supprime `X-User-*`, `:83-86` ajoute `X-User-Id`/`X-User-Role` | OK |
| Chemins publics | `/api/auth/**,/api/*/health,/api/quiz/certificats/verification/*` (`application.yml:48`) | **KO pour le catalogue visiteur** (I1) |
| Actuator gateway | `include: health,info,gateway` (`application.yml:63`) sur le port 8080 exposé ; le filtre JWT est un `GlobalFilter` (requêtes routées seulement) | **à vérifier** (I7) |
| CORS | origine `CORS_ALLOWED_ORIGINS` (défaut `http://localhost:4200`), en-têtes `Authorization, Content-Type, Accept` (`CorsConfig.java`) | OK |
| Rôles | guards front : `/app` ETUDIANT, `/studio` ENSEIGNANT, `/admin` ADMIN (`app.routes.ts:41,62,79`) ; backend : `CurrentUser.requireAnyRole`/`requireOwnerOrAdmin` (pas de Spring Security ni de `@PreAuthorize` dans les services) ; ai-service : `ROLES_GENERATION` (quiz `router.py:24`), `ROLES_RISQUE`/`ROLE_ADMIN` (user `router.py:55-56`) | cohérent, avec les trous I2, I3, I9 |
| Noms Eureka | `spring.application.name` : `user-service`, `cours-service`, `quiz-service`, `forum-service`, `api-gateway` = noms des `@FeignClient` ; gateway en `lb://USER-SERVICE…` (insensible à la casse) | OK |
| Package `common` | contenu identique dans les 4 services (même empreinte md5 après normalisation du nom de package) | OK |
| docker-compose | 9 services, ports 4200, 8080, 8761 et 3306 seulement ; services métier et ai-service non exposés (`docker-compose.yml:6-8`) | OK, sauf le stockage de cours (B2) |
| `.env.example` | toutes les variables lues sans valeur par défaut y figurent. Variables facultatives absentes (toutes ont un défaut) : `LLM_EMBEDDING_MODEL`, `LLM_EMBEDDING_DIMENSION`, `OPENAI_EMBEDDING_MODEL`, `GROQ_MARGE_REFLEXION`, `SANITIZER_MAX_CHARS` (`ai-service/app/core/config.py:29,30,42,54,57`), `COURS_STOCKAGE_DOSSIER` (`cours-service/application.yml:32`), `USER_ML_DOSSIER` (`user/ml/entrainement.py:42`) | MINEUR (M8) |

---

## 5. Parcours utilisateurs

| Parcours | Statut | Preuves |
|---|---|---|
| **a) inscription → connexion → questionnaire** | **incomplet** | Inscription et connexion : `auth.service.ts:36,42` → `AuthController.java:28,34`. Après l'inscription, l'apprenant va sur `/demarrer` (`register.ts:84`) et le formateur sur `/studio` (`register.ts:82`). Le questionnaire enregistre par `PUT /me/parcours` (`onboarding.ts:52`), mais il propose les catégories **fictives** `Données, Back-end, Front-end, DevOps, Outils` (`onboarding.ts:6`, `core/demo/demo-plus.ts:36-42`). Les vrais domaines sont `Informatique, Mathématiques…` (`ListesParcours.java:11`, `cours.constantes.ts:12-25`). Rien ne valide ces valeurs côté serveur : les domaines enregistrés ne correspondent à aucune catégorie de cours (I4) |
| **b) catalogue → inscription/paiement → lecture → progression** | **incomplet** | Catalogue `/app/explorer` réel (`catalogue.ts:86` → `GET /api/cours`). Inscription `cours-detail.ts:88` → `InscriptionController.java:37`. Lecture et progression : `lecteur.ts:113,120,152` → `/activite`, `/progression`, `/chapitres/{id}/terminer`. **Pas de paiement** : *« Paiement : il n'existe pas dans le projet »* (`InscriptionService.java:30-31`) ; `/app/paiement/:id` n'est lié depuis nulle part et lit `demo-data` (`paiement.ts:10,70`) ; « Mes achats » (`user-menu.ts:35`) lit `VENTES` en dur (`achats.ts:6`) (I5). Le visiteur non connecté sur `/catalogue` est renvoyé vers `/login` (I1) |
| **c) quiz → tentative → certificat → notions ratées → conseil IA** | **OK** | Tentative `quiz.service.ts:82` → `PassageController.java:55`. Notions ratées et certificat à la correction (`TentativeService.java:35,72`, `CertificatService.java:38`). Vérification publique : `CertificatController.java:39` (chemin public de la gateway). Page Progrès : `progres.ts:41-44,61` → `/me/ia`, `/me/ia/conseil`, `/me/ia/plan`. user-service lit les notions ratées (`IaGenerativeService.java:302`) et les envoie au LLM sous forme de libellés. L'accueil apprenant, lui, est fictif (B1) |
| **d) formateur : cours + quiz → apprenants + risque + rapport IA** | **incomplet** | Création de cours (`CoursService.java:93`) et de quiz (`QuizService.java:93-95`) : réelles. Page Apprenants : `ia-user.service.ts:31` → `/formateur/apprenants` (`ApprenantsService.java:66,86,131-132`, contrat 7 + `risque` + `profil`). Rapport IA : `ia-generative.service.ts:53-57` → `IaGenerativeService.java:185`. **Mais** l'accueil du studio est fictif (B1), « Avis » est fictif (`studio-avis.ts:7-8`), l'envoi d'image et de PDF est à risque en Docker (B2), et ni la publication par un formateur non validé (I2) ni un quiz sur le cours d'un autre formateur (I3) ne sont bloqués |
| **e) forum : discussion liée à un cours, auteurs, notions, IA** | **OK** | `CoursClient` (cours, texte, chapitres), `UserClient` (noms d'auteurs), `QuizClient` (notions ratées), `IaClient` (9 routes IA) : tous les endpoints cibles existent (§4.2). 161 tests backend et 106 tests IA verts. Fichiers identiques à `forum-v1` |
| **f) admin : utilisateurs, formateurs, catalogue, certificats, ventes, IA, forum, audit** | **incomplet** | Réels : utilisateurs (`user.service.ts:73-90`), demandes formateur (`:94-102`), certificats (`quiz.service.ts:109`), IA (`ia-user.service.ts:35,40`), modération du forum (`FORUM_ADMIN_ROUTES`). **Fictifs** : accueil admin (`admin-home.ts:5`), catalogue (`admin-catalogue.ts:14,48`, *« À brancher : CoursService »* l. 30), ventes (`admin-ventes.ts:4-5`), audit (`audit.ts:6`, *« À brancher : GET /api/users/audit »* l. 22) ; paramètres sans aucun service injecté (`parametres.ts`, 0 `inject(...Service)`) |

---

## 6. Problèmes trouvés

### Statut des points (mis à jour sur `integration/finale`)

L'analyse ci-dessous décrit l'état de `c94a225`. Le statut indique où en est chaque point dans les commits suivants de `integration/finale`.

| # | Niveau | Statut | Où |
|---|---|---|---|
| B1 | BLOQUANT | **Corrigé** | `b5cb9e6` (accueils des 3 rôles, accueil public, admin, agenda) ; `9d93786` (paiement, ventes, avis) ; commit `fix(front)` du questionnaire d'accueil (onboarding, « Mon compte », menu Explorer, pied de page public, ancienne page `profile` supprimée). Seule la vitrine `/admin/composants` garde des exemples fictifs, c'est voulu |
| B2 | BLOQUANT | **Corrigé** | `26c07fc` (volume et `COURS_STOCKAGE_DOSSIER` pour cours-service) |
| I1 | IMPORTANT | **Corrigé** | `cddb86c` (GET publics du catalogue à la gateway, visiteur accepté par cours-service, pas de déconnexion sans jeton) |
| I2 | IMPORTANT | **Corrigé** | `fix(cours)` et `fix(quiz)` : `FormateurVerifier` (Feign `GET /api/users/{id}`) refuse en 403 la publication d'un cours, d'un quiz ou d'un examen par un formateur non validé (ADMIN exempté) |
| I3 | IMPORTANT | **Corrigé** | `fix(quiz)` : création et modification d'un quiz réservées au formateur du cours (ou à l'admin), test `unFormateurNeCreePasDeQuizSurLeCoursDUnAutre` |
| I4 | IMPORTANT | **Corrigé** | `fix(front)` : onboarding et « Mon compte » sur `parcours.constantes.ts` (miroir de `ListesParcours`) |
| I5 | IMPORTANT | **Corrigé** | `9d93786` : plus de faux chiffres. `feat(cours)` : paiement simulé (`POST /api/cours/{id}/commandes`, carte de test, aucune somme débitée), Mes achats et Ventes sur données réelles ; contenu d'un cours payant réservé aux acheteurs, inscription directe refusée (`403`). Limite : `/texte` (contrat 2) reste lisible pour le forum et le quiz |
| I6 | IMPORTANT | **Corrigé** | `b5cb9e6`, `9d93786` (aucun écran hors vitrine n'importe plus `core/demo`) |
| I7 | IMPORTANT | **Corrigé** | `fix(gateway)` : actuator limité à `health,info` |
| I8 | IMPORTANT | **Corrigé (avertissement)** | `c48742f` : gateway et user-service journalisent un avertissement au démarrage si `JWT_SECRET` ou `ADMIN_PASSWORD` gardent leur valeur publique. Refuser le démarrage reste possible avant une mise en ligne |
| I9 | IMPORTANT | **Corrigé** | `fix(ai)` : toutes les routes `/api/ai/cours/*` exigent `ENSEIGNANT` ou `ADMIN`. Forum : un filtre par rôle casserait forum-service, qui relaie le jeton de l'apprenant ; les routes qui appellent le LLM sont donc limitées à 40 appels par minute et par apprenant (`forum/limiteur.py`, `FORUM_APPELS_IA_PAR_MINUTE`, `429` au-delà), formateurs et admin exemptés |
| I10 | IMPORTANT | **Corrigé (documenté)** | `e0f868c` : contrats 5 et 8 indiquent l'entraînement sur données simulées, affiché dans Admin → IA. Brancher les vraies inscriptions reste à faire (module User) |
| I11 | IMPORTANT | **Corrigé** | `e0f868c` : contrats 9 à 12 (`GET /api/cours`, `/api/cours/{id}/chapitres`, `/api/quiz/tentatives/mes`, `/api/users`) |
| M1 à M10 | MINEUR | voir la colonne « Statut » du tableau MINEUR | |

### BLOQUANT

**B1 — Les accueils des trois rôles et 9 autres écrans affichent des données fictives**
- Module : socle (`pages/`, `core/`) et Cours (`features/cours/pages/`).
- Fichiers :
  - accueils : `pages/student-home/student-home.ts:7-8`, `pages/teacher-home/teacher-home.ts:6,16`, `pages/admin/admin-home.ts:5` ;
  - autres écrans : `pages/admin/audit.ts:6`, `pages/agenda/agenda.ts:6`, `pages/public/home/home.ts:7-8`, `core/layouts/parts/explorer-menu.ts:7-8`, `features/cours/pages/{paiement,achats,admin-ventes,admin-catalogue,studio-avis}/*.ts`.
- Description : ces écrans importent `core/demo/demo-data.ts` ou `demo-plus.ts` (1 026 lignes) et n'appellent aucun service (`student-home` et `teacher-home` n'injectent que `AuthService`). C'est le premier écran après la connexion de chaque rôle. Le bouton principal « Continuer » de l'apprenant pointe vers un identifiant de cours fictif (`student-home.html:18`), « Relire » du studio vers `/studio/cours/1` en dur (`teacher-home.html:42`). Cela contredit le CLAUDE.md : *« Pas de données de démo en dur »*.
- Correctif proposé :
  - brancher les accueils sur les API existantes : `mesInscriptions`, `/me/ia`, `certificats/mes` et `tentatives/mes` pour l'apprenant ; `mes-quiz`, `/formateur/apprenants` et `GET /api/cours` pour le formateur ; `GET /api/users`, `demandes-formateur` et `metriques` pour l'admin ;
  - pour les écrans sans backend (ventes, achats, avis, audit, agenda) : les masquer du menu ou les marquer clairement « aperçu ».
- Effort : **L** (accueils M chacun ; masquage S).

**B2 — Envoi d'image ou de PDF de cours probablement impossible en Docker, et fichiers perdus à chaque recréation**
- Module : socle (`docker-compose.yml`) et Cours (configuration).
- Fichiers : `backend/Dockerfile:18,20` (`WORKDIR /app` créé par root, puis `USER bawsla`) ; `cours-service/application.yml:32` (`./uploads/cours`, donc `/app/uploads/cours`) ; `StockageService.java:57-60` (`Files.createDirectories`, sinon `UncheckedIOException`, donc une 500 par le gestionnaire générique) ; `docker-compose.yml:95-103` (aucun volume pour cours-service).
- Description : le compose le dit lui-même pour le forum : *« /app appartient à root et n'est pas inscriptible »* (`docker-compose.yml:123-124`). Le forum a reçu un volume et une variable, pas cours-service. Or le PDF est la base de la fiche, de l'explication, du résumé et de l'indexation RAG du forum. **À confirmer avec Docker** (§7, test 4).
- Correctif proposé : comme pour le forum, ajouter `COURS_STOCKAGE_DOSSIER: /home/bawsla/uploads/cours` et un volume `cours_fichiers:/home/bawsla` à `cours-service` (PR socle).
- Effort : **S**.

### IMPORTANT

**I1 — Le visiteur ne peut pas parcourir le catalogue public**
- Module : socle (gateway) et Cours.
- Fichiers : `api-gateway/application.yml:48` ; `auth.interceptor.ts:24-25` ; `catalogue.ts:86` ; routes `/catalogue` et `/catalogue/:id` (`app.routes.ts:29`).
- Description : sans jeton, `GET /api/cours` reçoit une 401 de la gateway, et l'intercepteur appelle `auth.logout('/login')`. Le visiteur qui clique « Catalogue » depuis `/bienvenue` est donc renvoyé vers la connexion. `CoursService.java:165-167` prévoit le visiteur (*« user peut être null : visiteur sans en-têtes, qui ne voit que les cours publiés »*). Mais même si la gateway laissait passer la requête, le `CurrentUserArgumentResolver` du package `common` la refuserait : il lève `UnauthorizedException` dès que `X-User-Id` ou `X-User-Role` manque (`cours/common/security/CurrentUserArgumentResolver.java:26-28`). `findAll` en dépend (`CoursController.java:39-44`).
- Correctif proposé :
  - gateway : un chemin public en **GET seulement** pour `/api/cours`, `/api/cours/{id}`, `/api/cours/{id}/image` et `/api/cours/{id}/chapitres` ;
  - `common` (socle, identique dans les 4 services) : un `CurrentUser` facultatif, par exemple une annotation ou un `Optional<CurrentUser>` sur ces routes ;
  - front : ne pas déconnecter sur une 401 quand aucun jeton n'était envoyé.
- Effort : **M**.

**I2 — Contrat 4 non appliqué : un formateur non validé peut publier**
- Module : Cours (et Quiz pour les quiz et examens).
- Fichiers : `CONTRATS_API.md:107` ; `CoursService.java:139-146` ; `AuthService.java:47,60` (l'inscription avec `role: ENSEIGNANT` donne tout de suite le rôle `ENSEIGNANT`, avec `formateurValide=false`) ; `role.guard` (`/studio` n'exige que le rôle).
- Description : l'étape « Admin → Demandes formateurs » n'empêche rien. N'importe qui peut s'inscrire formateur, publier des cours et créer des examens qui délivrent des certificats.
- Correctif proposé : ajouter à cours-service un `UserClient` Feign (`GET /api/users/{id}`) et lever un `ForbiddenException` dans `changerStatut(PUBLIE)` si `formateurValide == false` (sauf ADMIN). Même vérification dans `QuizService.publier`.
- Effort : **M**.

**I3 — Un formateur peut créer un quiz ou un examen sur le cours d'un autre formateur**
- Module : Quiz.
- Fichiers : `QuizService.java:93-95` (`create`), `:111` (`update`), `:224-231` (`verifierCoursExiste` n'appelle `getCours` que pour l'existence et ignore `enseignantId`).
- Description : pour tout cours publié (donc visible), un autre ENSEIGNANT peut créer un quiz de type `EXAMEN`. La réussite délivre un certificat au nom du cours (`TentativeService.java:72`).
- Correctif proposé : lire `CoursDto.enseignantId()` et appeler `user.requireOwnerOrAdmin(enseignantId)`.
- Effort : **S**.

**I4 — Le questionnaire d'accueil enregistre des domaines qui n'existent pas**
- Module : socle (`pages/onboarding`) ; signalé par le module User dans `prompts_journal/user/JOURNAL.md` (entrée 19).
- Fichiers : `onboarding.ts:6,31-32` ; `demo-plus.ts:36-42` ; à comparer avec `ListesParcours.java:11` et `features/user/models/parcours.constantes.ts`.
- Description : les recommandations par domaine et le contexte du conseil IA reçoivent des libellés (`Back-end`…) absents du catalogue. Le même questionnaire existe en version réelle dans « Mon compte » (`mon-compte.ts:15`, avec `ONBOARDING` pour profils, objectifs et temps uniquement).
- Correctif proposé : utiliser dans `onboarding.ts` les listes de `features/user/models/parcours.constantes.ts`. Option : faire valider les valeurs par user-service avec `ListesParcours`.
- Effort : **S**.

**I5 — Le paiement n'existe pas, mais l'interface le montre**
- Module : Cours (décision d'équipe).
- Fichiers : `InscriptionService.java:30-31` ; `paiement.ts:19` (*« À brancher : POST /api/cours/{id}/commandes »*) ; `achats.ts`, `admin-ventes.ts`, `parametres.ts:71` (« Prestataire de paiement ») ; `cours-detail.ts:24`.
- Description : un cours payant s'inscrit et se lit gratuitement. Son PDF et son texte sont accessibles à tout utilisateur connecté (`getTexte` → `chargerVisible`). « Mes achats » et « Ventes » montrent de faux chiffres.
- Correctif proposé : décider en équipe. Soit un paiement simulé réel (entité `Commande` dans cours-service, contrat dans `CONTRATS_API.md`, verrouillage du contenu payant), soit l'affichage « gratuit pendant la démo » avec masquage d'achats et de ventes.
- Effort : **L** (simulé complet) ou **S** (masquage).

**I6 — Écrans d'administration et de formateur non branchés** (complète B1 pour les écrans hors accueil)
- Module : Cours (`admin-catalogue`, `studio-avis`) et socle (`audit`, `agenda`, `parametres`).
- Description : voir §5 f et d. `admin-catalogue` liste `COURS` en dur alors que `GET /api/cours` (ADMIN : tout) existe déjà.
- Correctif proposé : brancher `admin-catalogue` sur `CoursService.findAll()` (S). Pour audit, agenda et avis : contrat à écrire ou masquage.
- Effort : **S** à **M**.

**I7 — Endpoint actuator `gateway` exposé sans authentification sur le port 8080** — *à vérifier*
- Module : socle (gateway).
- Fichiers : `api-gateway/application.yml:63` ; `JwtAuthenticationFilter.java:36` (un `GlobalFilter` ne s'applique qu'aux requêtes routées, pas à `/actuator/**`).
- Description : `/actuator/gateway/routes` révèle la configuration. Selon la version de Spring Cloud Gateway, il peut aussi accepter l'ajout de routes (POST) et un `refresh`. Le port 8080 est exposé (`docker-compose.yml:76-77`).
- Correctif proposé : `include: health,info` uniquement (le healthcheck Docker n'utilise que `/actuator/health`).
- Effort : **S**.

**I8 — Secret JWT et mot de passe admin par défaut connus**
- Module : socle.
- Fichiers : `api-gateway/application.yml:45` et `user-service/application.yml:33` (`${JWT_SECRET:change-me-dev-secret-…}`) ; `user-service/application.yml:37` (`Admin123!`) ; `docker-compose.yml:12-14` (`.env` avec `required: false`).
- Description : sans `.env`, la stack démarre avec un secret public. N'importe qui peut alors fabriquer un jeton ADMIN valide.
- Correctif proposé : supprimer la valeur par défaut du secret (démarrage refusé sans `JWT_SECRET`), ou au minimum journaliser un avertissement au démarrage.
- Effort : **S**.

**I9 — Les routes `/api/ai/**` sont appelables directement par tout utilisateur connecté**
- Module : Cours (ai-service), socle (gateway) ; partie forum **À traiter par Malek**.
- Fichiers : `ai-service/app/modules/cours/router.py:79-120`, `explication/routes.py:60-103`, `vision/routes.py:34`, `local/routes.py:20` (seulement `Depends(get_current_user)`, aucun contrôle de rôle) ; `app/core/security.py:19-26`.
- Description : un apprenant peut appeler `fiche`, `vision`, `audio` ou `explication` avec n'importe quel texte et consommer le quota LLM et TTS. Quiz et User filtrent déjà par rôle (`quiz/router.py:24,37` ; `user/router.py:55-61`).
- **Forum (À traiter par Malek)** : les routes `/api/ai/forum/*` sont, par conception, appelées par forum-service avec le jeton de l'utilisateur (`CONTRATS_API.md:165`). Un apprenant peut donc aussi les appeler directement, sans passer par les règles de forum-service (modération, limites). Aucun correctif proposé ici.
- Correctif proposé (Cours) : exiger `ENSEIGNANT`/`ADMIN` sur les routes de génération, comme `quiz/router.py`. À plus long terme (socle) : réserver `/api/ai/**` aux appels des services.
- Effort : **S** (Cours) ; **M** (socle).

**I10 — Contrat 8 sans consommateur : les modèles ML restent sur données simulées**
- Module : User.
- Fichiers : `InscriptionController.java:78` (fournisseur) ; aucune référence à `inscriptions/toutes` dans `backend/user-service` ni dans `ai-service` ; `ai-service/app/modules/user/ml/donnees/{apprenants,cours,inscriptions}.csv`.
- Description : le contrat est déclaré « implémenté » (`CONTRATS_API.md:225`), mais l'entraînement n'utilise pas les vraies inscriptions (`donneesSimulees: true`).
- Correctif proposé : à décider. Soit brancher `POST /entrainer` sur les contrats 6 à 8 (pseudonymisation dans user-service), soit écrire clairement « entraîné sur données simulées » dans le contrat et l'interface admin.
- Effort : **M**.

**I11 — Routes utilisées entre modules mais déclarées « internes » (pouvant changer sans PR)**
- Module : socle (`CONTRATS_API.md`).
- Description :
  - `GET /api/cours` : user `CoursClient.java:25`, front quiz `cours-titres.service.ts:32`, front forum `forum.service.ts:214` ;
  - `GET /api/cours/{id}/chapitres` : forum `CoursClient.java:28` ;
  - `GET /api/quiz/tentatives/mes` : user `QuizClient.java:23` ;
  - `GET /api/users` (ADMIN) : front forum `moderation.service.ts:124`.
  
  `CONTRATS_API.md:242-243` les classe en « hors contrat ».
- Correctif proposé : en faire les contrats 9 à 12 (champs garantis), par PR.
- Effort : **S**.

### MINEUR

| # | Module | Fichier(s) | Description | Correctif proposé | Effort | Statut |
|---|---|---|---|---|---|---|
| M1 | socle | `frontend/tsconfig.app.json:6,8` | `"rootDir"` en double (avertissement du build, confirmé) | supprimer la ligne 8 | S | **Corrigé** (`b5cb9e6`) |
| M2 | socle | `frontend/package-lock.json` | 4 vulnérabilités (2 high, 2 critical) dans l'outillage de dev (`piscina`, `@modelcontextprotocol/sdk`, via `@angular/build` et `@angular/cli`) ; 0 en production | monter `@angular/cli` et `@angular/build` au dernier patch 21.2.x, puis `npm audit` | S | **Corrigé** (`@angular/cli` et `@angular/build` 21.2.27 ; `npm audit` : 0 vulnérabilité) |
| M3 | tous | `angular.json`, `backend/api-gateway`, `backend/eureka-server` | aucun test front (pas de cible `test`, 0 `*.spec.ts`) ; aucun test pour la gateway (filtre JWT) | ajouter un test du filtre JWT (suppression de `X-User-*`, chemins publics) ; ajouter la cible Vitest d'Angular 21 si l'équipe veut des tests front | M | **Partiellement corrigé** (`JwtAuthenticationFilterTest` : 12 tests du filtre de la gateway ; toujours aucun test front) |
| M4 | socle | `CONTRATS_API.md:133,163` | contrat 5 Cours : seul `resume` (stub sans appelant) est décrit, pas les 8 vraies routes (`fiche`, `fidelite`, `explication`, `explication/traduire`, `explication/fidelite`, `explication/audio`, `resume-local`, `vision`, `audio`). La l. 163 dit que `generer` est appelée par quiz-service, or c'est le front (`quiz-ia.service.ts:16`). Consommateurs inexacts aux contrats 2 et 3 (§4.1) | mettre la documentation à jour (PR) | S | **Corrigé** (`e0f868c`) |
| M5 | User | `user-service/.../client/CoursClient.java:16,20`, `QuizClient.java:19-26` | commentaires périmés (*« (proposé) … 404 tant que la route n'existe pas »*), alors que les contrats 6 et 7 sont implémentés | mettre les commentaires à jour | S | **Corrigé** (`e0f868c`) |
| M6 | Forum, Quiz, Cours | `features/forum/components/outils.ts:10` (`messageErreur`) et `shared/models/api-response.model.ts:21` (`errorMessage`) ; trois caches de la liste des cours (`cours.service.ts`, `quiz/services/cours-titres.service.ts`, `forum.service.ts:213-217`) | logique dupliquée entre modules. **Partie forum : À traiter par Malek** | utiliser `errorMessage` de `shared` ; à terme, un seul service partagé de titres de cours dans `shared` (PR socle) | S | **Partiellement corrigé** (`messageErreur` du forum s'appuie sur `errorMessage` de `shared` ; les trois caches de cours restent, PR socle à prévoir) |
| M7 | socle | `core/config/api.config.ts:5` | `http://localhost:8080/api` en dur : la démo ne marche que si le navigateur tourne sur la machine qui héberge la stack | à garder pour la démo locale ; sinon, un proxy `/api` dans `nginx.conf` et une URL relative | S | **Corrigé** (`API_BASE_URL = '/api'` ; proxy `/api` dans `nginx.conf` et `proxy.conf.json` pour `ng serve`) |
| M8 | socle | `.env.example` | variables facultatives non documentées (liste en §4.4) | les ajouter en commentaire avec leur valeur par défaut | S | **Corrigé** (`e0f868c`) |
| M9 | socle | poste de dev | Java 17 dans le PATH (JDK 21 requis, présent dans `~/.jdks`) ; le `.venv` d'ai-service est incomplet (pas de scikit-learn, pandas, numpy ni joblib) ; un venv sur un chemin long échoue (limite Windows de 260 caractères) | documenter dans le README (`JAVA_HOME`, `pip install -r requirements-dev.txt`, chemin court) | S | **Partiellement corrigé** (`26c07fc` : README à jour) |
| M10 | git | `origin/integration/user-cours-quiz`, remote | branche non fusionnée sans contenu réel (§2.2) ; le remote a déménagé vers `MZahii/Bawsla.git` | supprimer ou archiver la branche ; `git remote set-url origin https://github.com/MZahii/Bawsla.git` | S | **Commande fournie** (suppression de la branche distante et `git remote set-url`), à lancer par le propriétaire du dépôt |

Rien d'autre n'a été trouvé :
- aucun `TODO`, `FIXME` ou `XXX` dans `backend/*/src/main`, `ai-service/app` ni `frontend/src/app` ;
- aucune couleur HEX dans `frontend/src/app` (DESIGN.md respecté sur ce point) ;
- aucune URL de service en dur dans le Java ;
- aucun écart dans les chemins Forum par rapport à `forum-v1`.

---

## 7. Ce qui reste à tester avec Docker (checklist pour Zahi)

Préparation : `.env` complet (`JWT_SECRET`, `LLM_API_KEY`, éventuellement `LLM_SECOURS` et sa clé), puis `docker compose up -d --build`.

- [ ] 1. Les 9 conteneurs passent à `healthy` ou `running` (`docker compose ps`). Les 5 services apparaissent dans Eureka (`http://localhost:8761`) : USER-, COURS-, QUIZ-, FORUM-SERVICE et API-GATEWAY.
- [ ] 2. Les 4 bases sont créées par `docker/mysql/init` (`bawsla_user`, `bawsla_cours`, `bawsla_quiz`, `bawsla_forum`), et le compte ADMIN est créé au démarrage.
- [ ] 3. `GET http://localhost:8080/api/{users,cours,quiz,forum,ai}/health` sans jeton → 200.
- [ ] 4. **B2** : en formateur, envoyer une image puis un PDF de cours. Noter la réponse (200 ou 500) et les logs de `bawsla-cours`. Recréer le conteneur et vérifier que le fichier est toujours là.
- [ ] 5. **I1** : sans être connecté, ouvrir `http://localhost:4200/catalogue` (renvoi vers `/login` attendu aujourd'hui).
- [ ] 6. **I7** : `curl http://localhost:8080/actuator/gateway/routes` sans jeton. Noter s'il répond 200. Tester aussi `POST /actuator/gateway/refresh`.
- [ ] 7. Usurpation : `curl -H "X-User-Id: 1" -H "X-User-Role: ADMIN" http://localhost:8080/api/users` sans jeton → 401 attendu. Avec un jeton ETUDIANT et ces en-têtes → 403 attendu.
- [ ] 8. Parcours a : inscription d'un apprenant, puis questionnaire, puis « Mon compte » : vérifier les domaines enregistrés (I4).
- [ ] 9. Parcours b : inscription à un cours (gratuit, puis payant), lecture, chapitre terminé, puis `GET /api/cours/{id}/progression` et le temps passé.
- [ ] 10. Parcours c : quiz raté (notions ratées, remédiation IA), puis examen réussi (certificat), puis vérification publique `/verifier`, puis Progrès (conseil et plan IA, avec le nom du fournisseur LLM).
- [ ] 11. Parcours d : formateur **non validé** → tenter de publier un cours (I2). Formateur B → créer un examen sur le cours de A (I3). Page Apprenants (risque et rapport IA).
- [ ] 12. Parcours e : discussion liée à un cours, nom de l'auteur affiché, La Vigie (RAG sur le PDF du cours), modération, résumé.
- [ ] 13. Parcours f : admin → accepter une demande formateur, puis vérifier que `formateurValide` vaut `true` dans `GET /api/users/{id}` ; certificats ; IA (`metriques`, `entrainer` ≈ 30 s) ; modération du forum.
- [ ] 14. Pannes : `docker stop bawsla-cours`, puis créer un quiz (503 propre attendu, pas de 500) et ouvrir Progrès et le forum (dégradation sans plantage). Même chose avec `bawsla-ai` (messages « IA indisponible », le ML et les pages restent utilisables).
- [ ] 15. Quota LLM : forcer une 429 (ou une clé invalide), vérifier le passage au secours si `LLM_SECOURS` est défini, et le code `LLM_QUOTA_EXCEEDED` sinon.
- [ ] 16. Voix : génération audio d'une fiche (chaîne `TTS_FOURNISSEURS`) dans le conteneur ai-service, qui tourne en non-root.

---

## 8. Prochaines étapes recommandées (dans l'ordre)

1. **Stack Docker** (Zahi) : corriger B2 (volume et `COURS_STOCKAGE_DOSSIER`, PR socle, S), puis dérouler la checklist §7 et compléter ce rapport avec les résultats.
2. **Sécurité de la gateway** (PR socle, S) : I7 (actuator), I8 (secret par défaut). En même temps : I1 (catalogue public en GET), en coordination avec Cours.
3. **Autorisations entre modules** : I2 (contrat 4 dans cours-service, et dans quiz pour les examens) et I3 (propriétaire du cours à la création d'un quiz). Ce sont les deux trous qui faussent les certificats.
4. **Données fictives** (B1, I6) : brancher les 3 accueils et `admin-catalogue` sur les API existantes ; masquer ou marquer « aperçu » ventes, achats, avis, audit et agenda.
5. **Décision d'équipe sur le paiement** (I5) et sur l'entraînement ML réel (I10). Écrire le résultat dans `CONTRATS_API.md`.
6. **Questionnaire d'accueil** (I4, S) et **routes IA du module Cours** limitées par rôle (I9, S).
7. **Documentation des contrats** (I11, M4) : contrats 9 à 12, routes IA du module Cours, consommateurs exacts.
8. **Mineurs** M1 à M10 (une PR « chore » groupée, environ 2 h), puis un test du filtre JWT (M3).

Forum (Malek) : rien de bloquant. À examiner : la partie forum de I9 (routes `/api/ai/forum/*` appelables directement) et de M6 (`messageErreur` en double de `errorMessage`).
