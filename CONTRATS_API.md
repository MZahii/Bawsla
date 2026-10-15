# CONTRATS_API.md — contrats entre modules

Fichier du **socle** : toute modification passe par une pull request relue par le propriétaire du service concerné.

Un contrat engage le service **fournisseur** : il ne peut pas changer l'URL, les champs ou leur sens sans PR. Ajouter un champ optionnel est autorisé ; supprimer ou renommer un champ ne l'est pas.

## Règles communes

- Toutes les URLs passent par la gateway : `http://localhost:8080/api/...`. Entre services Java, les appels se font par Feign avec le nom Eureka (`@FeignClient(name = "cours-service")`) et le même chemin.
- Authentification : `Authorization: Bearer <jwt>` depuis le front. La gateway transmet `X-User-Id` et `X-User-Role` ; Feign les propage automatiquement (`FeignHeadersInterceptor`).
- Les **réponses réussies** utilisent l'enveloppe suivante, et c'est `data` qui porte le contrat :
  ```json
  { "success": true, "data": { }, "message": "…", "timestamp": "ISO-8601" }
  ```
- Les **erreurs** utilisent ce format :
  ```json
  { "success": false, "status": 404, "error": "NOT_FOUND", "message": "…", "path": "/api/…", "timestamp": "…", "details": { } }
  ```
- Les champs `null` sont omis, les dates sont en ISO-8601 et le JSON est en camelCase.
- On référence une ressource d'un autre service **par son ID uniquement** : aucune clé étrangère entre bases.

---

## 1. `GET /api/cours/{id}`

| | |
|---|---|
| Fournisseur | cours-service |
| Consommateurs | quiz-service (existence du cours à la création d'un quiz, déjà implémenté), forum-service, front |
| Rôles | tout utilisateur authentifié |
| Statut | **implémenté** |

`200` :
```json
{
  "success": true,
  "data": {
    "id": 1,
    "titre": "Introduction à Spring Boot",
    "description": "Bases",
    "categorie": "Backend",
    "niveau": "DEBUTANT",
    "fichierPdf": null,
    "enseignantId": 2,
    "dateCreation": "2026-10-04T23:08:13.785707",
    "dateModification": "…"
  },
  "timestamp": "…"
}
```
- `niveau` vaut `DEBUTANT`, `INTERMEDIAIRE` ou `AVANCE`.
- Erreur : `404` si le cours n'existe pas.

Garanti stable : `id`, `titre`, `categorie`, `niveau`, `enseignantId`. Ce sont les champs lus par `quiz-service/client/CoursDto`.

## 2. `GET /api/cours/{id}/texte`

| | |
|---|---|
| Fournisseur | cours-service |
| Consommateurs | ai-service (résumé, fiche de révision), forum-service |
| Rôles | tout utilisateur authentifié |
| Statut | **stub** : renvoie le titre et la description préfixés de `[STUB]`, avec `stub: true` |

`200` :
```json
{ "success": true, "data": { "coursId": 1, "texte": "…texte brut extrait du PDF…", "stub": false } }
```
- Erreur : `404` si le cours n'existe pas.
- À faire (membre cours) : extraire le texte du PDF et passer `stub` à `false`. Le texte ne doit contenir aucune donnée personnelle.

## 3. `GET /api/quiz/notions-ratees?etudiantId={id}`

| | |
|---|---|
| Fournisseur | quiz-service |
| Consommateurs | user-service / ai-service (recommandations), cours-service, front (tableau de bord) |
| Rôles | l'étudiant lui-même (`X-User-Id == etudiantId`), `ENSEIGNANT`, `ADMIN` |
| Statut | **en place** : agrégé à partir des tentatives (une ligne par apprenant, cours et notion) |

`200` :
```json
{
  "success": true,
  "data": [
    { "notion": "Injection de dépendances", "coursId": 1, "nbErreurs": 4, "tauxReussite": 0.33 }
  ]
}
```
- `tauxReussite` est compris entre 0 et 1.
- Le tableau est trié par `nbErreurs` décroissant.
- Erreurs : `400` si `etudiantId` est absent ; `403` si un étudiant demande les notions d'un autre étudiant.

## 4. `GET /api/users/{id}`

| | |
|---|---|
| Fournisseur | user-service |
| Consommateurs | forum-service (auteur d'une discussion), cours-service (enseignant), quiz-service, front |
| Rôles | tout utilisateur authentifié |
| Statut | **implémenté** |

`200` (informations **publiques uniquement**) :
```json
{ "success": true, "data": { "id": 2, "nom": "Ben Ali", "prenom": "Sami", "role": "ENSEIGNANT", "formateurValide": true } }
```
- `formateurValide` : `true` seulement pour un `ENSEIGNANT` validé par l'admin (Admin → Demandes formateurs) ; `false` pour un formateur en attente ou refusé et pour les autres rôles. **cours-service doit refuser la publication d'un cours (`403`) si `formateurValide` est `false`.**
- Erreur : `404` si l'utilisateur n'existe pas.
- **Jamais** d'email, de hash, de statut `actif` ni de date dans ce contrat. Les données complètes ne sont visibles que par l'intéressé (`/api/users/me`) ou un ADMIN (`/api/users`).
- Ces données ne doivent **jamais** être transmises au LLM.

## 5. `POST /api/ai/{module}/...`

| | |
|---|---|
| Fournisseur | ai-service, joint via la gateway (`/api/ai/**`, URL statique `AI_SERVICE_URL`) |
| Consommateurs | front, services Java |
| Rôles | tout utilisateur authentifié (la gateway exige le JWT) |
| Statut | **stubs** : `stub: true`, `message: "stub"` |

| Endpoint | Corps de la requête | `data` de la réponse |
|---|---|---|
| `POST /api/ai/user/profil` | `{ "apprenants": [ <indicateurs>, … ] }` (1 à 500) : voir *Indicateurs* ci-dessous. | `{ "groupes": [{ "id": 0, "nom": "Décrocheur", "description": "…" }], "donneesSimulees": true }`, dans l'ordre des apprenants. Groupes K-Means : `Avancé`, `Régulier`, `Débutant en difficulté`, `Décrocheur`. |
| `POST /api/ai/user/risque` | `{ "apprenants": [ <indicateurs>, … ] }` (1 à 500). **Rôles `ENSEIGNANT` et `ADMIN` uniquement** (`403` sinon) : le risque n'est jamais montré à l'apprenant. | `{ "resultats": [{ "probabilite": 0.9, "niveau": "eleve", "facteurs": ["Note moyenne basse aux quiz (42%)", "…"] }], "seuil": 0.4, "donneesSimulees": true }`. `niveau` parmi `faible`, `moyen`, `eleve` ; au plus 3 `facteurs`, aucun quand le risque est faible. |
| `POST /api/ai/user/recommandations` | `{ "coursSuivis": [8], "domaines": ["Back-end"], "coursEnDifficulte": [8], "catalogue": [1, 2, …], "nombre": 3 }` : tout est facultatif ; `coursEnDifficulte` = `coursId` des notions ratées (contrat 3) ; `catalogue` = cours recommandables (par défaut tous ceux du modèle) ; `nombre` de 1 à 10. | `{ "recommandations": [{ "coursId": 7, "titre": "…", "score": 0.82, "raison": "Pour revoir les bases en Back-end" }], "donneesSimulees": true }`. Jamais un cours déjà suivi. |
| `GET /api/ai/user/metriques` | — (**`ADMIN`**) | Contenu de `metriques.json` : scores des trois modèles, courbes coude et silhouette, matrice de confusion, importances, date, source des données, versions. |
| `POST /api/ai/user/entrainer` | — (**`ADMIN`**) | Mêmes métriques après un nouvel entraînement (≈ 30 s). |
| `POST /api/ai/cours/resume` | `{ "coursId": 1, "texte": "optionnel" }` | `{ "coursId": 1, "resume": "…", "stub": true }` |
| `POST /api/ai/quiz/generer` | `{ "coursId": 1, "matiere": "Mathématiques", "theme": "…", "competences": ["…"], "niveauxBloom": ["APPLIQUER"], "difficulte": "INTERMEDIAIRE", "nbQuestions": 5 }` : `matiere` facultative (80 caractères au plus, la précision saisie pour « Autre »), `nbQuestions` entre 1 et 10, `competences` de 1 à 8, `niveauxBloom` parmi `MEMORISER`, `COMPRENDRE`, `APPLIQUER`, `ANALYSER`, `EVALUER`, `CREER`, et `difficulte` parmi `FACILE`, `INTERMEDIAIRE`, `AVANCE`, `DIFFICILE`, `MASTER` (`MOYEN` reste accepté et vaut `INTERMEDIAIRE`). **Aucun texte de cours.** | `{ "coursId": 1, "questions": [{ "enonce": "…", "niveauBloom": "APPLIQUER", "competence": "…", "choix": [{ "texte": "…", "estCorrecte": true }, { "texte": "…", "estCorrecte": false, "erreurAssociee": "…" }], "explication": "…", "statut": "VALIDEE" }], "nbValidees": 4, "nbRejetees": 1, "versionPrompt": "v2", "difficulte": "INTERMEDIAIRE", "fournisseur": "gemini", "alertes": [] }`. Chaque question a exactement 4 choix, dont 1 seul correct, et chaque mauvais choix porte une `erreurAssociee`. `statut` vaut `VALIDEE` ou `REJETEE` ; dans le second cas, `raisonRejet` est renseigné. |
| `POST /api/ai/quiz/remedier` | `{ "notion": "…", "niveauBloom": "APPLIQUER", "enonce": "…", "choixDonne": "…", "bonneReponse": "…", "erreurAssociee": "…", "explication": "…" }`. **Aucun identifiant ni aucune donnée de l'apprenant.** | `{ "notion": "…", "raisonnement": "…", "explication": "…", "conseil": "…", "questionRattrapage": { "enonce": "…", "choix": [ … ], "explication": "…" }, "versionPrompt": "v1", "fournisseur": "gemini" }`. `questionRattrapage` est optionnelle et suit le même format qu'une question générée. `raisonnement` est réservé au formateur et à l'admin : il est omis de la réponse quand `X-User-Role` vaut `ETUDIANT`. |
| `POST /api/ai/forum/questions-similaires` | `{ "question": "…", "coursId": 1, "limite": 5 }` | `{ "question": "…", "similaires": [{ "discussionId": 7, "titre": "…", "score": 0.82 }], "stub": true }` |

**Indicateurs d'un apprenant (module User)** : uniquement des nombres, calculés par user-service. `noteMoyenne`, `tauxReussite`, `progressionMoyenne` entre 0 et 1 ; `nbQuiz`, `nbNotionsRatees`, `nbErreurs`, `tempsPasseMinutes`, `nbCoursSuivis`, `nbCoursTermines`, `joursDepuisDerniereConnexion` entiers positifs ; `joursActifs30j` entre 0 et 30. Les 11 champs sont obligatoires. **Aucun identifiant, nom ni email : tout champ inattendu est refusé (`400`).** `donneesSimulees` vaut `true` tant que les modèles sont entraînés sur le jeu simulé (quiz-service et cours-service ne fournissent pas encore toutes les données).

Erreurs spécifiques à l'IA, levées par `app/core`, au format d'erreur commun :

| Statut | `error` | Cause |
|---|---|---|
| 400 | `BAD_REQUEST` | corps invalide (`details` par champ) |
| 401 | `UNAUTHORIZED` | en-têtes `X-User-*` absents (appel hors gateway) |
| 413 | `LLM_TOO_LONG_FOR_FALLBACK` | texte trop long pour la limite par minute du fournisseur de secours (Groq) ; inutile de réessayer tel quel |
| 429 | `LLM_QUOTA_EXCEEDED` | quota du fournisseur LLM atteint |
| 502 | `LLM_INVALID_RESPONSE` | le LLM a répondu un JSON invalide pour le schéma attendu |
| 503 | `LLM_MISSING_KEY` | `LLM_API_KEY` absente, ou provider `stub` sur un appel JSON |
| 504 | `LLM_TIMEOUT` | dépassement de `LLM_TIMEOUT_SECONDS` |

Le module Quiz ne lit jamais le PDF ni le texte d'un cours : `generer` part uniquement des objectifs pédagogiques. Ces deux routes sont appelées par quiz-service, qui génère en asynchrone.

Un nouvel endpoint IA s'ajoute dans `app/modules/<module>/router.py` (fichier du module), puis on documente sa ligne ici par PR.

---

## Endpoints internes à chaque module (hors contrat)

Ils peuvent évoluer librement par leur propriétaire :

- **user** : `POST /api/auth/register` (avec `expertise`, `presentation`, `lien` facultatifs pour un formateur) et `POST /api/auth/login` (publics) ; `GET` et `PUT /api/users/me`, `PUT /me/mot-de-passe`, `POST /me/desactivation`, `GET` et `PUT /me/parcours`, `POST` et `DELETE /me/avatar`, `GET /{id}/avatar`, `GET` et `POST /me/demande-formateur` ; ADMIN : `GET` et `POST /api/users`, `PUT /{id}`, `PATCH /{id}/statut`, `PUT /{id}/mot-de-passe`, `GET /demandes-formateur`, `POST /demandes-formateur/{id}/accepter` et `/refuser`.
- **cours** : `GET /api/cours?categorie=&niveau=`, `POST`, `PUT /{id}`, `DELETE /{id}` (ENSEIGNANT propriétaire ou ADMIN pour l'écriture).
- **quiz** : `GET /api/quiz?coursId=`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.
- **forum** : `GET /api/forum/discussions?coursId=`, `GET /{id}`, `POST` (tout utilisateur), `PUT` et `DELETE /{id}` (auteur ou ADMIN).
- **health** (publics) : `GET /api/{users|cours|quiz|forum|ai}/health`.

## Historique

| Date | Changement | PR |
|---|---|---|
| 2026-10-04 | Version initiale du socle (contrats 1 à 5) | commit initial |
| 2026-10-13 | Contrat 5 : génération de quiz par objectifs, remédiation, erreur 413. Contrat 2 : consommateurs. Contrat 3 : statut « prévu » (forme inchangée) | docs/contrat-quiz-ia |
| 2026-10-13 | Contrat 4 : ajout de `formateurValide` (validation des formateurs par l'admin), à utiliser par cours-service avant publication ; liste des endpoints internes du module user mise à jour | PR `feature/user` |
| 2026-10-15 | Contrat 5 : endpoints ML du module User (`profil`, `risque`, `recommandations`, `metriques`, `entrainer`) ; l'ancien stub `recommandations { etudiantId }` est remplacé par des indicateurs chiffrés, sans identifiant | PR `feature/user` |
