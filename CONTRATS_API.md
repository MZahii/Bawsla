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
| Statut | **implémenté** : texte extrait du PDF (vision si scanné), emails et téléphones masqués, `stub: false` ; `pages` et `estScanne` facultatifs |

`200` :
```json
{ "success": true, "data": { "coursId": 1, "texte": "…texte brut extrait du PDF…", "stub": false } }
```
- Erreur : `404` si le cours n'existe pas.
- Le texte ne contient aucune donnée personnelle. Cours sans PDF : `texte` vide.

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
| Statut | **implémenté** pour les 4 modules ; seul `POST /api/ai/forum/questions-similaires` reste un stub (la recherche est faite par forum-service, `POST /api/forum/discussions/similaires`) |

| Endpoint | Corps de la requête | `data` de la réponse |
|---|---|---|
| `POST /api/ai/user/profil` | `{ "apprenants": [ <indicateurs>, … ] }` (1 à 500) : voir *Indicateurs* ci-dessous. | `{ "groupes": [{ "id": 0, "nom": "Décrocheur", "description": "…" }], "donneesSimulees": true }`, dans l'ordre des apprenants. Groupes K-Means : `Avancé`, `Régulier`, `Débutant en difficulté`, `Décrocheur`. |
| `POST /api/ai/user/risque` | `{ "apprenants": [ <indicateurs>, … ] }` (1 à 500). **Rôles `ENSEIGNANT` et `ADMIN` uniquement** (`403` sinon) : le risque n'est jamais montré à l'apprenant. | `{ "resultats": [{ "probabilite": 0.9, "niveau": "eleve", "facteurs": ["Note moyenne basse aux quiz (42%)", "…"] }], "seuil": 0.4, "donneesSimulees": true }`. `niveau` parmi `faible`, `moyen`, `eleve` ; au plus 3 `facteurs`, aucun quand le risque est faible. |
| `POST /api/ai/user/recommandations` | `{ "coursSuivis": [8], "domaines": ["Back-end"], "coursEnDifficulte": [8], "catalogue": [1, 2, …], "nombre": 3 }` : tout est facultatif ; `coursEnDifficulte` = `coursId` des notions ratées (contrat 3) ; `catalogue` = cours recommandables (par défaut tous ceux du modèle) ; `coursDisponibles` (facultatif) = vrai catalogue `[{ "coursId": 1, "titre": "…", "categorie": "…", "niveau": "DEBUTANT" }]` (cours publiés de cours-service) ; `nombre` de 1 à 10. | `{ "recommandations": [{ "coursId": 7, "titre": "…", "score": 0.82, "raison": "Pour revoir les bases en Back-end" }], "donneesSimulees": true }`. Jamais un cours déjà suivi. Un cours de `coursDisponibles` inconnu du modèle (pas le même identifiant et le même titre) est classé sur les domaines, le niveau et les notions ratées, sans historique (raison « Nouveau dans le catalogue »). |
| `GET /api/ai/user/metriques` | — (**`ADMIN`**) | Contenu de `metriques.json` : scores des trois modèles, courbes coude et silhouette, matrice de confusion, importances, date, source des données, versions. |
| `POST /api/ai/user/entrainer` | — (**`ADMIN`**) | Mêmes métriques après un nouvel entraînement (≈ 30 s). |
| `POST /api/ai/user/conseil` | **LLM.** `{ "indicateurs": <indicateurs>, "profil": "Décrocheur", "objectif": "…", "coursSuivis": ["titre"], "notionsARevoir": ["Jointures"], "coursRecommandes": ["titre"] }` : seul `indicateurs` est obligatoire ; au plus 10 cours suivis, 5 notions, 3 cours recommandés. | `{ "conseil": "…", "actions": ["…"], "fournisseur": "gemini", "versionPrompt": "v1" }` : 1 à 3 actions, en tutoyant ; un texte qui parle de risque, d'échec ou d'abandon est écarté (`502`). |
| `POST /api/ai/user/explication-risque` | **LLM, `ENSEIGNANT` et `ADMIN`.** `{ "niveau": "eleve", "probabilite": 0.72, "facteurs": ["…"], "indicateurs": <indicateurs>, "profil": "…", "notionsARevoir": ["…"] }` (résultat de `risque`). | `{ "resume": "…", "actions": ["…"], "messageSuggere": "…", "fournisseur": "gemini", "versionPrompt": "v1" }` : `messageSuggere` est destiné à l'apprenant et ne parle jamais de risque. |
| `POST /api/ai/user/plan` | **LLM.** `{ "tempsDisponible": "1 à 3 h", "objectif": "…", "profil": "…", "coursEnCours": [{ "titre": "…", "progression": 0.33 }], "notionsARevoir": ["…"], "coursRecommandes": ["…"] }` : tout est facultatif. | `{ "resume": "…", "seances": [{ "jour": "Lundi", "activite": "…", "dureeMinutes": 30 }], "totalMinutes": 90, "budgetMinutes": 180, "fournisseur": "gemini", "versionPrompt": "v1" }` : 1 à 6 séances triées, une par jour, de 10 à 120 min ; total dans le temps disponible (`Moins de 1 h` = 60, `1 à 3 h` = 180, `3 à 5 h` = 300, `Plus de 5 h` = 420, sinon 180). |
| `POST /api/ai/user/rapport-cohorte` | **LLM, `ENSEIGNANT` et `ADMIN`.** Statistiques agrégées : `{ "nbApprenants": 12, "nbCours": 2, "risques": { "faible": 5, "moyen": 4, "eleve": 2, "inconnu": 1 }, "progressionMoyenne": 0.6, "noteMoyenne": 0.7, "profils": [{ "nom": "Régulier", "nombre": 6 }], "notions": [{ "nom": "Jointures", "nombre": 4 }], "cours": [{ "titre": "…", "nbInscrits": 8, "progressionMoyenne": 0.5 }] }`. | `{ "synthese": "…", "pointsCles": ["…"], "recommandations": ["…"], "fournisseur": "gemini", "versionPrompt": "v1" }` (au plus 4 constats et 4 recommandations). |
| `POST /api/ai/user/domaines` | **LLM.** `{ "texte": "je veux devenir data analyst", "domainesDisponibles": ["Informatique", …], "objectifsDisponibles": ["Trouver un premier emploi", …] }` : `texte` de 3 à 500 caractères, nettoyé par le Sanitizer. | `{ "domaines": ["Informatique"], "objectif": "Trouver un premier emploi", "explication": "…", "fournisseur": "gemini", "versionPrompt": "v1" }` : seulement des valeurs exactes des listes (au plus 3 domaines) ; `objectif` omis si aucun ne convient. |
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

**IA générative du module User** : le Machine Learning décide, le LLM explique. Les routes marquées **LLM** passent par le client LLM du socle (prompts versionnés dans `app/modules/user/llm/prompts.py`) ; elles ne reçoivent que des nombres, des libellés, des noms de notions et des titres de cours (`extra="forbid"`), et tous les textes passent par le Sanitizer. Elles renvoient les erreurs LLM ci-dessous ; user-service les traduit en `503` avec un message clair, sans bloquer les résultats du Machine Learning.

Le module Quiz ne lit jamais le PDF ni le texte d'un cours : `generer` part uniquement des objectifs pédagogiques. Ces deux routes sont appelées par quiz-service, qui génère en asynchrone.

Les routes `/api/ai/forum/*` (hors `questions-similaires`, stub d'origine conservé) sont appelées par forum-service avec le jeton de l'utilisateur, via la gateway. Tous les corps refusent un champ inconnu (`extra="forbid"`) : aucun nom, email ni identifiant d'utilisateur n'y entre. Version des prompts : `PROMPT_FORUM_VERSION` (v3 par défaut).

Un nouvel endpoint IA s'ajoute dans `app/modules/<module>/router.py` (fichier du module), puis on documente sa ligne ici par PR.

---

> **Contrats 6 à 8 : proposés par le module User** pour son Machine Learning (sujet : « regroupe les étudiants selon leurs notes, leur temps passé et les cours suivis », « prédit le risque d'échec à partir de ses résultats aux quiz », « les étudiants au profil proche du tien ont suivi tel cours »). user-service les appelle par Feign, calcule des **indicateurs chiffrés** et n'envoie à ai-service **que ces nombres** (contrat 5, module User) : jamais de nom, d'email ni d'identifiant. En attendant leur mise en place, les modèles sont entraînés sur un jeu de données simulé.

## 6. `GET /api/quiz/tentatives?etudiantId={id}`

| | |
|---|---|
| Fournisseur | quiz-service |
| Consommateurs | user-service (indicateurs du ML : note moyenne, quiz passés, bonnes réponses), front (tableau de bord formateur) |
| Rôles | l'étudiant lui-même (`X-User-Id == etudiantId`), `ENSEIGNANT`, `ADMIN` |
| Statut | **implémenté** (entité `Tentative`) |

`200` (une ligne par tentative corrigée, la plus récente d'abord) :
```json
{
  "success": true,
  "data": [
    { "quizId": 4, "coursId": 1, "nbCorrectes": 7, "nbQuestions": 10, "pourcentage": 70, "reussie": true, "dateTentative": "2026-10-14T18:02:11" }
  ]
}
```
- `pourcentage` est un entier de 0 à 100 ; `reussie` suit la règle de réussite du quiz.
- Tableau vide si l'apprenant n'a passé aucun quiz.
- Erreurs : `400` si `etudiantId` est absent ; `403` si un étudiant demande les tentatives d'un autre étudiant.
- Utilisation par user-service : `nbQuiz` = nombre de tentatives, `noteMoyenne` = moyenne de `pourcentage` / 100, `tauxReussite` = somme de `nbCorrectes` / somme de `nbQuestions`.

## 7. `GET /api/cours/inscriptions?etudiantId={id}` et `GET /api/cours/{id}/inscriptions`

| | |
|---|---|
| Fournisseur | cours-service |
| Consommateurs | user-service (indicateurs du ML : cours suivis, progression, temps passé, cours terminés ; page Apprenants du formateur), front |
| Rôles | par `etudiantId` : l'étudiant lui-même, `ENSEIGNANT`, `ADMIN` ; par cours : l'`ENSEIGNANT` propriétaire du cours, `ADMIN` |
| Statut | **implémenté** : entité `Inscription` de cours-service (`etudiantId`, `coursId`, `dateInscription`, progression, temps passé), alimentée par le lecteur de cours |

`200` (une ligne par inscription) :
```json
{
  "success": true,
  "data": [
    { "etudiantId": 12, "coursId": 1, "dateInscription": "2026-10-01T09:00:00", "progression": 0.65, "tempsPasseMinutes": 240, "termine": false, "dateDerniereActivite": "2026-10-14T20:15:00" }
  ]
}
```
- `progression` de 0 à 1 (part des chapitres terminés) ; `termine` vaut `true` quand tous les chapitres le sont ; `tempsPasseMinutes` entier positif ; `dateDerniereActivite` facultative.
- Par cours (`/api/cours/{id}/inscriptions`) : les inscrits d'un cours, pour la page Apprenants du formateur.
- Erreurs : `400` si `etudiantId` est absent ; `403` si un étudiant demande les inscriptions d'un autre, ou un formateur celles d'un cours qui n'est pas le sien ; `404` si le cours n'existe pas.

## 8. `GET /api/cours/inscriptions/toutes`

| | |
|---|---|
| Fournisseur | cours-service |
| Consommateurs | user-service (entraînement du filtrage collaboratif : matrice apprenants × cours suivis) |
| Rôles | `ADMIN` uniquement |
| Statut | **implémenté** (même entité `Inscription` que le contrat 7) |

`200` :
```json
{ "success": true, "data": [ { "etudiantId": 12, "coursId": 1, "progression": 0.65, "termine": false } ] }
```
- Toutes les inscriptions de la plateforme, sans autre donnée sur l'apprenant.
- user-service remplace `etudiantId` par un pseudonyme avant l'envoi à ai-service : le modèle ne voit jamais d'identifiant réel.
- Erreur : `403` pour tout autre rôle que `ADMIN`.

---

## Endpoints internes à chaque module (hors contrat)

Ils peuvent évoluer librement par leur propriétaire :

- **user** : `POST /api/auth/register` (avec `expertise`, `presentation`, `lien` facultatifs pour un formateur) et `POST /api/auth/login` (publics) ; `GET` et `PUT /api/users/me`, `PUT /me/mot-de-passe`, `POST /me/desactivation`, `GET` et `PUT /me/parcours`, `POST` et `DELETE /me/avatar`, `GET /{id}/avatar`, `GET` et `POST /me/demande-formateur`, `GET /me/indicateurs` et `GET /me/ia` (profil et recommandations ; jamais le risque) ; ENSEIGNANT ou ADMIN : `GET /{id}/indicateurs`, `GET /{id}/ia` (risque et profil d'un apprenant) et `GET /formateur/apprenants` (inscrits à ses cours et leur risque, via le contrat 7) ; IA générative (LLM), `GET` = dernier texte gardé, `POST` = générer (texte réutilisé tant que les données n'ont pas changé) : `/me/ia/conseil` et `/me/ia/plan` (ETUDIANT), `POST /me/parcours/suggestion` (ETUDIANT, `{ "texte": "…" }`), `/{id}/ia/explication` et `/formateur/apprenants/rapport` (ENSEIGNANT ou ADMIN) ; ADMIN : `GET` et `POST /api/users`, `PUT /{id}`, `PATCH /{id}/statut`, `PUT /{id}/mot-de-passe`, `GET /demandes-formateur`, `POST /demandes-formateur/{id}/accepter` et `/refuser`.
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
| 2026-10-13 | Contrat 4 : ajout de `formateurValide` (validation des formateurs par l'admin), à utiliser par cours-service avant publication ; liste des endpoints internes du module user mise à jour | PR `feature/user` |
| 2026-10-15 | Contrat 5 : endpoints ML du module User (`profil`, `risque`, `recommandations`, `metriques`, `entrainer`) ; l'ancien stub `recommandations { etudiantId }` est remplacé par des indicateurs chiffrés, sans identifiant | PR `feature/user` |
| 2026-10-15 | Contrat 5 : champ facultatif `coursDisponibles` (vrai catalogue) pour `recommandations` ; endpoints internes du module user (indicateurs, ia) | PR `feature/user` |
| 2026-10-15 | Contrats 6 (tentatives d'un apprenant, quiz-service), 7 et 8 (inscriptions, cours-service) proposés par le module User pour son Machine Learning | docs/contrat-user-ia |
| 2026-10-16 | Contrat 5 : IA générative du module User (`conseil`, `explication-risque`, `plan`, `rapport-cohorte`, `domaines`) ; endpoints internes du module user correspondants | PR `feature/user-ia` |
| 2026-10-18 | Statuts mis à jour à l'intégration des 4 modules : contrats 2, 5, 6, 7 et 8 implémentés (seul `forum/questions-similaires` reste un stub) | integration/finale |
