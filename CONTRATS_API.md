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
{ "success": true, "data": { "id": 2, "nom": "Ben Ali", "prenom": "Sami", "role": "ENSEIGNANT" } }
```
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
| `POST /api/ai/user/recommandations` | `{ "etudiantId": 3 }` | `{ "etudiantId": 3, "profil": "…", "coursRecommandes": [1, 4], "stub": true }` |
| `POST /api/ai/cours/resume` | `{ "coursId": 1, "texte": "optionnel" }` | `{ "coursId": 1, "resume": "…", "stub": true }` |
| `POST /api/ai/quiz/generer` | `{ "coursId": 1, "matiere": "Mathématiques", "theme": "…", "competences": ["…"], "niveauxBloom": ["APPLIQUER"], "difficulte": "INTERMEDIAIRE", "nbQuestions": 5 }` : `matiere` facultative (80 caractères au plus, la précision saisie pour « Autre »), `nbQuestions` entre 1 et 10, `competences` de 1 à 8, `niveauxBloom` parmi `MEMORISER`, `COMPRENDRE`, `APPLIQUER`, `ANALYSER`, `EVALUER`, `CREER`, et `difficulte` parmi `FACILE`, `INTERMEDIAIRE`, `AVANCE`, `DIFFICILE`, `MASTER` (`MOYEN` reste accepté et vaut `INTERMEDIAIRE`). **Aucun texte de cours.** | `{ "coursId": 1, "questions": [{ "enonce": "…", "niveauBloom": "APPLIQUER", "competence": "…", "choix": [{ "texte": "…", "estCorrecte": true }, { "texte": "…", "estCorrecte": false, "erreurAssociee": "…" }], "explication": "…", "statut": "VALIDEE" }], "nbValidees": 4, "nbRejetees": 1, "versionPrompt": "v2", "difficulte": "INTERMEDIAIRE", "fournisseur": "gemini", "alertes": [] }`. Chaque question a exactement 4 choix, dont 1 seul correct, et chaque mauvais choix porte une `erreurAssociee`. `statut` vaut `VALIDEE` ou `REJETEE` ; dans le second cas, `raisonRejet` est renseigné. |
| `POST /api/ai/quiz/remedier` | `{ "notion": "…", "niveauBloom": "APPLIQUER", "enonce": "…", "choixDonne": "…", "bonneReponse": "…", "erreurAssociee": "…", "explication": "…" }`. **Aucun identifiant ni aucune donnée de l'apprenant.** | `{ "notion": "…", "raisonnement": "…", "explication": "…", "conseil": "…", "questionRattrapage": { "enonce": "…", "choix": [ … ], "explication": "…" }, "versionPrompt": "v1", "fournisseur": "gemini" }`. `questionRattrapage` est optionnelle et suit le même format qu'une question générée. `raisonnement` est réservé au formateur et à l'admin : il est omis de la réponse quand `X-User-Role` vaut `ETUDIANT`. |
| `POST /api/ai/forum/questions-similaires` | `{ "question": "…", "coursId": 1, "limite": 5 }` | `{ "question": "…", "similaires": [{ "discussionId": 7, "titre": "…", "score": 0.82 }], "stub": true }` |
| `POST /api/ai/forum/vectoriser` | `{ "textes": ["…"], "tache": "DOCUMENT" }` (`DOCUMENT` ou `REQUETE`, 1 à 100 textes) | `{ "vecteurs": [[0.01, …]], "modele": "gemini-embedding-001", "dimension": 768, "fournisseur": "gemini" }` (vecteurs normalisés) |
| `POST /api/ai/forum/indexer` | `{ "coursId": 1, "texte": "…", "pages": [{ "numero": 1, "texte": "…" }], "chapitres": [{ "id": 3, "titre": "…", "contenu": "…" }] }` | `{ "passages": [{ "chapitreId": 3, "page": 12, "ordre": 0, "titreSection": "…", "texte": "…", "vecteur": [ … ] }], "modele": "…", "dimension": 768 }` (~800 caractères, 120 de chevauchement) |
| `POST /api/ai/forum/reformuler` | `{ "question": "…", "historique": [{ "role": "APPRENANT", "texte": "…" }] }` | `{ "requete": "…", "langueDetectee": "fr", "versionPrompt": "v3", "repli": false }` (repli : question nettoyée telle quelle, sans LLM) |
| `POST /api/ai/forum/repondre` | `{ "question": "…", "mode": "DIRECTE", "passages": [{ "id": 11, "origine": "COURS", "cours": "…", "chapitre": "…", "page": 12, "texte": "…", "score": 0.82 }] }` (`mode` : `DIRECTE` ou `GUIDEE`) | `{ "reponse": "… [1].", "sources": [{ "numero": 1, "id": 11, "origine": "COURS", "cours": "…", "passage": "…", "chapitre": "…", "page": 12, "citation": "…" }], "confiance": 82, "horsCours": false, "indices": [], "questionsSimilaires": [], "versionPrompt": "v3", "fournisseur": "gemini", "alertes": [] }`. Citations revérifiées sans LLM, confiance calculée, abstention si rien n'est vérifié. |
| `POST /api/ai/forum/repondre-general` | `{ "question": "…" }` (niveau 3, sur demande de l'apprenant seulement) | `{ "reponse": "…", "incertitudes": ["…"], "aVerifier": ["…"], "versionPrompt": "v3", "fournisseur": "gemini" }` : ni source ni confiance |
| `POST /api/ai/forum/moderer` | `{ "titre": "…", "contenu": "…" }` | `{ "categorie": "OK", "score": 0.0, "raison": "…", "regle": null, "versionPrompt": "v3", "fournisseur": "gemini" }` ; `categorie` parmi `OK`, `HORS_SUJET`, `IRRESPECTUEUX`, `SPAM`, `FUITE_EXAMEN`, `INJECTION`, `DONNEES_PERSONNELLES` ; `regle` = règle sans LLM qui a tranché (`repli` si le LLM est absent : message accepté) |
| `POST /api/ai/forum/resumer` | `{ "titre": "…", "messages": [{ "role": "REPONSE", "texte": "…" }] }` (rôles `QUESTION`, `REPONSE`, `VIGIE`, `COMMENTAIRE`, jamais de nom) | `{ "puces": ["…"], "versionPrompt": "v2", "fournisseur": "gemini" }` (1 à 5 puces) |
| `POST /api/ai/forum/ameliorer` | `{ "titre": "…", "contenu": "…", "etiquettesExistantes": ["sql"] }` | `{ "titre": "…", "manques": ["…"], "etiquettes": ["sql"], "repli": false, "versionPrompt": "v2", "fournisseur": "gemini" }` (étiquettes prises dans la liste fournie) |
| `POST /api/ai/forum/nommer-groupes` | `{ "groupes": [{ "titres": ["…"] }] }` (groupes calculés par forum-service) | `{ "noms": ["…"], "repli": false, "versionPrompt": "v2", "fournisseur": "gemini" }` (un nom par groupe, dans l'ordre) |

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

Les routes `/api/ai/forum/*` (hors `questions-similaires`, stub d'origine conservé) sont appelées par forum-service avec le jeton de l'utilisateur, via la gateway. Tous les corps refusent un champ inconnu (`extra="forbid"`) : aucun nom, email ni identifiant d'utilisateur n'y entre. Version des prompts : `PROMPT_FORUM_VERSION` (v3 par défaut).

Un nouvel endpoint IA s'ajoute dans `app/modules/<module>/router.py` (fichier du module), puis on documente sa ligne ici par PR.

---

## Endpoints internes à chaque module (hors contrat)

Ils peuvent évoluer librement par leur propriétaire :

- **user** : `POST /api/auth/register` et `POST /api/auth/login` (publics), `GET /api/users/me`, `GET /api/users` (ADMIN).
- **cours** : `GET /api/cours?categorie=&niveau=`, `POST`, `PUT /{id}`, `DELETE /{id}` (ENSEIGNANT propriétaire ou ADMIN pour l'écriture).
- **quiz** : `GET /api/quiz?coursId=`, `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.
- **forum** : toutes les routes `/api/forum/**` (discussions, réponses, votes, La Vigie, assistant privé, modération, studio formateur, espace admin `/api/forum/admin/**`) sont décrites dans `prompts_journal/forum/PLAN_FORUM.md` §6.1.
- **health** (publics) : `GET /api/{users|cours|quiz|forum|ai}/health`.

## Historique

| Date | Changement | PR |
|---|---|---|
| 2026-10-04 | Version initiale du socle (contrats 1 à 5) | commit initial |
| 2026-10-13 | Contrat 5 : génération de quiz par objectifs, remédiation, erreur 413. Contrat 2 : consommateurs. Contrat 3 : statut « prévu » (forme inchangée) | docs/contrat-quiz-ia |
| 2026-10-15 | Contrat 5 : routes IA du forum (vectoriser, indexer, reformuler, repondre, repondre-general, moderer, resumer, ameliorer, nommer-groupes). Endpoints internes du forum | feature/forum |
