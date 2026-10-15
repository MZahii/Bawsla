# Journal des prompts — module User (authentification, profils, rôles)

**Membre** : Belkhouja Hamza
**Branche** : `feature/user`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 14 — 2026-10-15 — user-service appelle l'IA, recommandations sur le vrai catalogue (étape F2b)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : relier user-service au ML (profil, risque, recommandations) pour un vrai apprenant, sans jamais prédire sur des données incomplètes
- **Contexte fourni** : indicateurs de l'entrée 13, contrat 5, client IA de cours-service (appel par la gateway avec le jeton de l'utilisateur)
- **Prompt** :
  > Yes
  (réponse à « Shall I start F2b? »)
- **Résultat** :
  - ai-service : champ facultatif `coursDisponibles` (vrai catalogue) ; un cours inconnu du modèle est classé sur les domaines, le niveau et les notions ratées ; raison « Nouveau dans le catalogue ».
  - user-service : `AiClient` (Feign, par la gateway, jeton relayé, délai de 180 s car le premier appel peut réentraîner les modèles), `IaApprenantService`, `GET /api/users/me/ia` (profil et recommandations, jamais le risque) et `GET /api/users/{id}/ia` (formateur, admin : risque et profil).
  - Règle : recommandations dès que le catalogue existe ; profil et risque seulement si toutes les sources ont répondu, sinon « Pas encore assez de données » ; IA ou catalogue arrêtés : message clair, jamais d'erreur 500.
  - `CONTRATS_API.md` (commit socle) : `coursDisponibles` et routes internes `indicateurs` / `ia`.
  - Tests : 7 dans user-service (79 au total), 7 dans ai-service.
- **Vérification** : `mvnw test` (79/79), `pytest tests/user` (75/75) ; via la gateway avec 3 vrais cours créés par un formateur : recommandations « Dans un domaine qui t'intéresse : Données » pour l'apprenant, profil et risque refusés faute de données (quiz et inscriptions pas encore disponibles), 403 entre apprenants ; premier appel 85 s (réentraînement dans le conteneur).
- **Gardé / modifié / rejeté** :
  - Bug trouvé au test réel : le vrai cours 3 (« Introduction à React », jamais suivi) héritait de la popularité du cours simulé 3, car les deux ont le même numéro. Corrigé : l'historique (popularité, voisins) ne s'applique qu'à un cours identique dans le modèle (même identifiant et même titre). Test de non-régression ajouté.
  - Modifié : pour un nouvel apprenant, la pénalité de niveau (0,15 par niveau) annulait exactement le bonus de domaine (0,30) : un cours avancé de son domaine passait après un cours débutant hors domaine. Pénalité ramenée à 0,10.
  - Gardé : erreurs de l'IA (y compris connexion refusée) attrapées partie par partie, pour qu'une page reste utilisable si l'IA est arrêtée.
- **Captures** : _(à ajouter : réponse de /api/users/me/ia)_
- **Leçon** : les identifiants d'un jeu simulé et ceux de la vraie base se recouvrent ; il faut vérifier l'identité d'un objet, pas seulement son numéro.

### 13 — 2026-10-15 — Indicateurs d'un apprenant rassemblés par user-service (étape F2a)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : calculer dans user-service les 11 indicateurs du ML d'un vrai apprenant, à partir de quiz-service, cours-service et user-service, en disant clairement quelles données manquent
- **Contexte fourni** : contrats 3, 6, 7 ; modèle Feign de référence (quiz-service : `CoursClient`, `FeignHeadersInterceptor`) ; client IA de cours-service
- **Prompt** :
  > yes
  (réponse à « Shall I start F2? »)
- **Résultat** :
  - `ActiviteJour` + `ActiviteService` : un jour d'activité enregistré à chaque connexion (et à chaque lecture de ses indicateurs) → `joursActifs30j`, qui n'existait pas (seule la dernière connexion était gardée).
  - Clients Feign `QuizClient` (contrats 3 et 6) et `CoursClient` (contrat 7, catalogue), `FeignHeadersInterceptor` : chaque service applique ses propres droits (un apprenant ne lit que ses données).
  - `IndicateursService` : chaque source appelée séparément, statut `DISPONIBLE`, `STUB`, `PAS_ENCORE_DISPONIBLE` ou `INDISPONIBLE` ; un indicateur dont la source manque reste null (omis du JSON), jamais remplacé par un faux zéro ; `complet` seulement si tout a répondu.
  - `GET /api/users/me/indicateurs` (apprenant) et `GET /api/users/{id}/indicateurs` (formateur, admin ; 404 si ce n'est pas un apprenant).
  - 12 tests (`IndicateursServiceTest`, `ActiviteServiceTest`), 72 tests au total dans user-service.
- **Vérification** : `mvnw test` (72/72) ; via la gateway : tentatives et inscriptions `PAS_ENCORE_DISPONIBLE`, notions ratées `STUB` (version de quiz-service encore sans contrat 3 dans ce Docker), activité `DISPONIBLE` ; droits : 403 entre apprenants, 200 pour l'admin, 404 pour un non-apprenant.
- **Gardé / modifié / rejeté** :
  - Corrigé au test réel : une route absente renvoyait 400 et non 404 (« tentatives » tombe sur `/api/quiz/{id}`, qui attend un nombre), donc classée à tort `INDISPONIBLE`. Comme nos requêtes sont toujours bien formées, 400, 404 et 405 comptent pour « route pas encore implémentée ».
  - Corrigé : les indicateurs absents apparaissaient en `null` dans le JSON ; omis comme l'exige la règle commune du projet.
  - Corrigé (tests) : créer un mock pendant un autre stubbing troublait Mockito ; exceptions Feign réelles (sous-classe anonyme).
- **Captures** : _(à ajouter : réponse de /api/users/me/indicateurs)_
- **Leçon** : un service qui dépend d'autres doit dégrader proprement source par source, et le dire, plutôt que d'échouer ou d'inventer des valeurs.

### 12 — 2026-10-15 — Contrats 6 à 8 proposés à Quiz et Cours (étape F1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : obtenir les vraies données du ML (notes, temps passé, cours suivis), absentes aujourd'hui, avant de brancher les pages Progrès et Apprenants
- **Contexte fourni** : branches distantes relues : quiz-service a désormais `Tentative` (score, réussite, date) et `NotionRatee`, mais aucune route pour lire les tentatives d'un autre apprenant ; cours-service n'a ni inscription, ni progression, ni temps passé
- **Prompt** :
  > yes
  (réponse à « Shall I start with F1, the contract proposals? »)
- **Résultat** :
  - Branche `docs/contrat-user-ia` partant de `develop` (petite PR à part, comme le module Quiz), faite dans un worktree séparé pour ne pas toucher à `feature/user`.
  - Contrat 6 : `GET /api/quiz/tentatives?etudiantId=` (quiz-service, à partir de l'entité `Tentative` existante) et la façon dont user-service en tire `nbQuiz`, `noteMoyenne`, `tauxReussite`.
  - Contrat 7 : `GET /api/cours/inscriptions?etudiantId=` et `GET /api/cours/{id}/inscriptions` (nouvelle entité `Inscription` dans cours-service : progression, temps passé, terminé).
  - Contrat 8 : `GET /api/cours/inscriptions/toutes` (ADMIN) pour entraîner le filtrage collaboratif ; `etudiantId` remplacé par un pseudonyme avant l'envoi à ai-service.
- **Vérification** : relecture de la structure de `CONTRATS_API.md` (sections 1 à 8, historique) ; simulation de fusion avec `feature/user` : seul conflit attendu, la ligne d'historique.
- **Gardé / modifié / rejeté** :
  - Rejeté : brancher tout de suite les pages Progrès et Apprenants avec des indicateurs presque vides : les prédictions auraient été trompeuses en démonstration.
  - Gardé : le principe « user-service calcule les indicateurs, ai-service ne reçoit que des nombres », déjà appliqué au contrat 5.
- **Captures** : _(aucune : document)_
- **Leçon** : quand un module dépend des données d'un autre, proposer le contrat tôt, sur une petite branche à part, pour laisser au coéquipier le temps de l'implémenter.

### 11 — 2026-10-15 — IA : page Admin → Supervision IA (étape E1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : point démo du sujet : « afficher la précision du modèle sur le tableau de bord admin », avec les vraies métriques
- **Contexte fourni** : `metriques.json` (entrée 09), endpoints `metriques` et `entrainer` (entrée 10), page `admin-ia` existante (données de démo), composants `bw-kpi`, `bw-chart`, `bw-empty-state`, `bw-skeleton`
- **Prompt** :
  > yes
  (réponse à « Shall I start E1? »)
- **Résultat** :
  - `models/ia.model.ts` (miroir de `metriques.json`) et `services/ia-user.service.ts` (`metriques()`, `entrainer()`).
  - Page `admin-ia` refaite : bandeau « Données simulées » (source, date, durée, versions), 4 KPI (accuracy 82,5 %, rappel 69 %, AUC 0,81, recommandation top 3 64 %), comparaison du Random Forest à la régression logistique et à la classe majoritaire, matrice de confusion, importances des variables, courbes du coude et de la silhouette, tableau des groupes K-Means, évaluation des recommandations, bouton « Réentraîner ».
- **Vérification** : build Docker du front ; navigateur headless connecté en admin : bandeau, 4 KPI, matrice 22 / 2 / 5 / 11, 5 graphiques, 3 groupes, réentraînement « Modèles réentraînés en 108,8 s », aucune erreur dans la console ; capture.
- **Gardé / modifié / rejeté** :
  - Retiré : les données de démo de la page (requêtes par module, quota, coût, erreurs, interrupteurs) : aucune source réelle, elles demanderaient un endpoint dans `app/core` (socle). Mieux vaut aucune donnée que de faux chiffres à côté des vrais.
  - Corrigé après capture : le tableau des groupes n'avait pas de marges (les styles globaux de `.bw-table` visent les tableaux Material) ; styles propres à la page.
  - Problème d'environnement : le build Docker du front restait bloqué au téléchargement des polices Google (réseau du build BuildKit). Contourné sans modifier de fichier : `docker build --network=host -t bawsla/frontend frontend`, puis `docker compose up -d --no-build frontend`.
- **Captures** : `captures/2026-10-15_11_supervision-ia.png`
- **Leçon** : sur un tableau de bord, distinguer clairement données réelles et simulées, et retirer les chiffres de démo dès qu'une partie de la page devient réelle.

### 10 — 2026-10-15 — IA : endpoints du module User et contrat (phase D)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : rendre les trois modèles utilisables via la gateway, sans jamais recevoir de donnée personnelle
- **Contexte fourni** : modèles et `entrainement.py` (entrées 06 à 09) ; conventions du module Quiz (`extra="forbid"`, contrôle de rôle) ; branches distantes relues (contrat 3 `notions-ratees` désormais « en place », `Tentative` et `NotionRatee` dans quiz-service)
- **Prompt** :
  > okay start Phase D but first what is this ? (IMAGE)
  (capture de l'activation automatique de `.venv` par l'extension Python de VS Code : expliquée, sans effet sur le projet)
- **Résultat** :
  - `schemas.py` : `Indicateurs` (11 nombres bornés), lots de 1 à 500 apprenants, `extra="forbid"` : `etudiantId`, `email`… refusés en 400.
  - `router.py` : `POST /profil` (K-Means), `POST /risque` (Random Forest, ENSEIGNANT et ADMIN seulement), `POST /recommandations` (cours suivis, domaines, notions ratées, catalogue), `GET /metriques` et `POST /entrainer` (ADMIN) ; calculs hors boucle asynchrone (`run_in_threadpool`) ; champs nuls omis.
  - `CONTRATS_API.md` (commit socle séparé) : contrat 5 du module User, l'ancien stub `recommandations { etudiantId }` remplacé.
  - Nouvelle synchronisation avec `develop` (PR #3 du module Quiz) : conflit dans l'historique de `CONTRATS_API.md`, résolu en gardant les deux lignes.
  - 12 tests de routes + 3 tests du dossier des modèles (166 tests au total dans ai-service).
- **Vérification** : `pytest` ; puis via la gateway avec de vrais jetons (apprenant, formateur, admin) : profil 200 (34 s au premier appel, entraînement dans le conteneur, puis 0,16 s), risque 403 pour l'apprenant et 200 pour le formateur, recommandations 200, données personnelles 400, en-têtes falsifiés sans jeton 401, métriques 403 pour le formateur, réentraînement admin 200 (51 s).
- **Gardé / modifié / rejeté** :
  - Bug trouvé au test Docker : 500 (`PermissionError`) car l'application tourne sous l'utilisateur `bawsla`, sans droit d'écriture sur `/app`. Corrigé dans le module, sans toucher au Dockerfile : dossier `USER_ML_DOSSIER` s'il est défini, sinon `ml/modeles/`, sinon le dossier temporaire ; un échec d'enregistrement n'empêche plus de servir les modèles.
  - Bug trouvé avant les tests : l'alias automatique de `jours_actifs30j` donnait `joursActifs30J` ; alias explicite `joursActifs30j`.
  - Corrigé : `"message": null` apparaissait dans les réponses ; `response_model_exclude_none=True` comme le module Quiz.
  - Faux problème : conteneur « unhealthy » juste après le redémarrage de Docker Desktop ; démarrage lent, import mesuré à 3 s, puis « healthy ».
  - Limite connue : dans Docker, les modèles vivent dans `/tmp` et sont réentraînés après chaque redémarrage du conteneur (≈ 30 à 50 s au premier appel).
- **Captures** : _(à ajouter : réponses de /risque et /recommandations via la gateway)_
- **Leçon** : tester dans le vrai conteneur, pas seulement en local : les droits du fichier et la version de Python y sont différents.

### 09 — 2026-10-13 — IA : enregistrement des modèles et métriques (étape C4)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : entraîner les trois modèles en une commande, les enregistrer, et les recharger rapidement pour les endpoints ; regrouper toutes les métriques pour le tableau de bord admin
- **Contexte fourni** : modules `clustering`, `risque`, `recommandation` (entrées 06 à 08)
- **Prompt** :
  > YES
  (réponse à « Shall I start C4? »)
- **Résultat** :
  - `ml/entrainement.py` : `entrainer_tout()` (≈ 28 s) écrit `profils.joblib`, `risque.joblib`, `recommandation.joblib` (≈ 165 Ko en tout) et `metriques.json` (scores des trois modèles, courbes du coude et de la silhouette, matrice de confusion, importances, évaluation des recommandations, date, durée, source « données simulées », versions de Python et des bibliothèques).
  - `obtenir()` : modèles gardés en mémoire, sinon rechargés du disque, sinon entraînés (premier démarrage) ; `reentrainer()` pour le bouton admin ; verrou pour les appels simultanés.
  - `ml/modeles/.gitignore` : les binaires ne sont pas versionnés.
  - 9 tests pytest (`tests/user/test_entrainement.py`).
- **Vérification** : `pytest` (151/151) ; rechargement : mêmes prédictions qu'avant l'enregistrement ; `git status` : seul le `.gitignore` est suivi.
- **Gardé / modifié / rejeté** :
  - Ajouté : refus de recharger des modèles produits avec une autre version de Python (mineure), de scikit-learn ou de numpy (le PC est en Python 3.13, l'image Docker en 3.12) : on réentraîne plutôt que de risquer un modèle mal rechargé.
  - Gardé comme règle de sécurité : joblib repose sur pickle, on ne charge que des fichiers produits par ce module, jamais un fichier venu de l'extérieur.
- **Captures** : _(à ajouter : sortie de la commande d'entraînement)_
- **Leçon** : un modèle enregistré dépend des versions qui l'ont produit ; il faut les enregistrer avec lui et les vérifier au chargement.

### 08 — 2026-10-13 — IA : filtrage collaboratif, recommandation de cours (étape C3)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : recommandation du sujet : « les étudiants au profil proche du tien ont suivi tel cours », avec en entrée supplémentaire les notions ratées (module Quiz)
- **Contexte fourni** : jeu de données simulé (630 inscriptions), contrat 3 `notions-ratees` (champ `coursId`), parcours de l'apprenant (domaines, user-service)
- **Prompt** :
  > yes
  (réponse à « Shall I start C3? »)
- **Résultat** :
  - Expériences : on cache un cours par apprenant (175 apprenants, 3 tirages) et on mesure s'il est retrouvé dans les n recommandations. Filtrage collaboratif seul (utilisateur ou cours, valeurs ou binaire, k voisins de 5 à 40) ≈ popularité (≈ 0,31 à 3, ≈ 0,55 à 5) : matrice très creuse (3 cours sur 15 par apprenant).
  - Ajout des domaines déclarés (colonne `domaines` exportée par le générateur, comme `parcours.domaines` de user-service) : hybride collaboratif 0,5 + popularité 0,2 + domaines 0,3 : réussite@3 0,644, réussite@5 0,752, couverture du catalogue 100 % (popularité : 0,314 / 0,568 / 44 %).
  - `ml/recommandation.py` : similarité cosinus, 20 voisins, jamais un cours déjà suivi, raison pour chaque cours (voisins, domaine, popularité), bonus « pour revoir les bases » pour un cours plus facile de la même catégorie qu'un cours où des notions ont été ratées, démarrage à froid par domaines et niveaux accessibles, filtre par catalogue (cours publiés).
  - 11 tests pytest (`tests/user/test_recommandation.py`) + vérification de la colonne `domaines`.
- **Vérification** : `pytest` (142/142) ; exemples : apprenant connu, nouvel apprenant, apprenant en difficulté en Spring Boot.
- **Gardé / modifié / rejeté** :
  - Rejeté : le filtrage collaboratif seul, pas meilleur que la popularité sur ces données ; la popularité seule, qui propose les mêmes cours à tous (44 % du catalogue).
  - Gardé : un hybride où le collaboratif garde le plus gros poids, pour rester un filtrage collaboratif comme le demande le sujet.
  - Modifié : un nouvel apprenant intéressé par « Data » se voyait proposer « Machine Learning appliqué » (avancé) dès le premier jour ; pénalité de niveau au démarrage à froid.
  - Limite à dire dans le rapport : le gain des domaines est en partie dû à la simulation, où les cours sont choisis selon les domaines ; sur de vraies données, il faudra le mesurer de nouveau.
- **Captures** : _(à ajouter : tableau de comparaison et exemples de recommandations)_
- **Leçon** : sur une matrice très creuse, le filtrage collaboratif seul ne bat pas une référence simple ; il faut toujours comparer à la popularité avant de conclure.

### 07 — 2026-10-13 — IA : Random Forest, risque d'échec (étape C2)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : apprentissage supervisé du sujet : prédire le risque d'échec d'un apprenant à partir de ses résultats aux quiz, et expliquer pourquoi
- **Contexte fourni** : jeu de données simulé (entrée 05), K-Means (entrée 06)
- **Prompt** :
  > yes
  (réponse à « Shall I start C2? »)
- **Résultat** :
  - `ml/risque.py` : 11 variables (quiz, cours, activité) ; découpage 80 / 20 stratifié ; `GridSearchCV` à 5 plis (profondeur, feuilles) ; `class_weight="balanced"` ; comparaison avec la classe majoritaire et une régression logistique ; seuil de décision choisi sur des prédictions hors pli en maximisant le F2 ; niveaux faible / moyen / élevé ; jusqu'à 3 raisons lisibles par apprenant, sans doublon, aucune quand le risque est faible.
  - Validation croisée : Random Forest accuracy 0,794, F1 0,750, AUC 0,810 ; régression logistique 0,750 / 0,719 / 0,833 ; classe majoritaire 0,600 / 0 / 0,5.
  - Test (40 apprenants jamais vus), seuil 0,4 : accuracy 0,825, précision 0,846, rappel 0,688, F1 0,759, AUC 0,805 ; 11 échecs repérés sur 16, 2 fausses alertes.
  - 14 tests pytest (`tests/user/test_risque.py`).
- **Vérification** : `pytest` (131/131) ; exemples d'apprenants avec probabilité, niveau et raisons.
- **Gardé / modifié / rejeté** :
  - Modifié : au seuil 0,5, le modèle ne repérait que 7 échecs sur 16 (rappel 0,44). Seuil choisi par F2 (un échec raté compte double) sur les prédictions hors pli, jamais sur le test : 0,4, rappel 0,69.
  - Modifié : importance par permutation calculée d'abord en F1 sur 40 apprenants, toutes ≤ 0 : trop instable. Passée en AUC, elle reste proche de 0 car des variables vont par paires (note / bonnes réponses, temps / cours suivis). Gardée dans les métriques ; l'ordre des raisons suit l'importance propre à la forêt, stable.
  - Rejeté : la variable « ancienneté », sans lien avec l'échec (importance par permutation négative).
  - Modifié : raisons seulement si l'apprenant fait moins bien que 75 % de ceux qui réussissent, une seule par groupe de variables redondantes, et aucune quand le risque est faible.
  - Limite gardée : sur ces données, la régression logistique est presque aussi bonne (normal : l'échec simulé suit une loi logistique). À dire dans le rapport ; le Random Forest garde l'avantage en validation croisée (F1) et capte des seuils non linéaires.
  - Accéléré : recherche des hyperparamètres en parallèle (`n_jobs=-1`), tests de 73 s à 39 s.
- **Captures** : _(à ajouter : tableau de comparaison et matrice de confusion)_
- **Leçon** : pour un outil d'alerte, l'accuracy ne suffit pas : il faut regarder le rappel de la classe à risque et choisir le seuil en conséquence, sans jamais toucher au jeu de test.

### 06 — 2026-10-13 — IA : K-Means, regroupement des apprenants (étape C1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : apprentissage non supervisé du sujet : regrouper les apprenants selon leurs notes, leur temps passé et les cours suivis
- **Contexte fourni** : jeu de données simulé (entrée 05), capture du sujet
- **Prompt** :
  > yes
  (réponse à « Shall I start C1? »)
- **Résultat** :
  - Expérience d'abord : k de 2 à 8, avec et sans transformation log, trois jeux de variables, silhouette et accord avec les profils cachés (ARI).
  - `ml/clustering.py` : `StandardScaler` + `KMeans` (graine 42) sur 7 variables (note, progression, temps, cours suivis, notions ratées, jours actifs, jours depuis la dernière connexion) ; inertie et silhouette pour k = 2 à 8 (données des graphiques coude et silhouette) ; k retenu = meilleure silhouette entre 3 et 6 ; groupes nommés d'après leur centre par des règles lisibles ; `predire()` pour un nouvel apprenant.
  - Résultat : k = 3, silhouette 0,289, ARI 0,419. « Régulier » (100 apprenants, 17 % d'échec : tous les avancés et la plupart des réguliers), « Débutant en difficulté » (59, 58 %), « Décrocheur » (41, 71 %).
  - 12 tests pytest (`tests/user/test_clustering.py`).
- **Vérification** : `pytest tests/user` (19/19) ; affichage des centres et de la répartition des profils cachés par groupe.
- **Gardé / modifié / rejeté** :
  - Rejeté : k = 2, qui a la meilleure silhouette (0,363) mais ne sépare que « ça va / ça ne va pas ». Règle retenue et justifiée : au moins 3 groupes, comme le sujet.
  - Rejeté : la transformation log du temps et des jours (pas de gain net d'ARI ni de silhouette).
  - Limite gardée et à expliquer dans le rapport : le K-Means sépare surtout par l'engagement ; il fusionne « avancé » et « régulier » (leurs comportements sont proches) mais ne classe aucun avancé en difficulté ni en décrocheur.
- **Captures** : _(à ajouter : tableau k / inertie / silhouette)_
- **Leçon** : la meilleure valeur d'une métrique (ici la silhouette à k = 2) n'est pas forcément le meilleur choix pour le produit ; il faut une règle de choix explicite et la justifier.

### 05 — 2026-10-13 — IA : préparation et jeu de données simulé (étapes A1, A2, B1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : préparer le ML du module User et produire les données d'entraînement simulées demandées par le sujet (« environ 200 étudiants fictifs, à dire clairement dans le rapport »)
- **Contexte fourni** : capture du sujet (partie IA du module User), lecture de toutes les branches du dépôt (quiz-service n'a pas encore de tentatives ni de notions ratées ; cours-service n'a ni inscriptions, ni progression, ni temps passé ; contrat 3 `notions-ratees` garanti stable par le membre Quiz)
- **Prompts** :
  > okay lets start with simulated data and start working ! but before that i need a summuray on what we gonna do from begging to the end in the ia working !

  > okay lets start working on it check the image !
- **Résultat** :
  - A1 : `develop` fusionnée dans `feature/user` (client LLM du socle), sans conflit.
  - A2 (commit socle) : `scikit-learn 1.9.1`, `pandas 3.0.6`, `numpy 2.5.4`, `joblib 1.6.0` dans `requirements.txt`.
  - B1 : `ml/donnees_simulees.py` : 200 apprenants tirés de 4 profils cachés (débutant en difficulté, régulier, avancé, décrocheur) avec du bruit, 15 cours (catégories et niveaux de cours-service), 630 inscriptions ; échec à l'examen selon une loi logistique des chiffres plus du hasard ; graine fixe 42 ; colonnes nommées comme les contrats (`nbErreurs`, `tauxReussite`, `note`, `progression`, `tempsPasseMinutes`…) pour brancher les vraies données sans changer les modèles ; identifiants `A001`… sans aucune donnée personnelle. CSV générés dans `ml/donnees/`.
  - 7 tests pytest (`tests/user/test_donnees_simulees.py`).
- **Vérification** : `pytest` (105/105, dont 7 nouveaux) ; statistiques par profil ; image Docker reconstruite (les 4 bibliothèques s'installent sous Python 3.12).
- **Gardé / modifié / rejeté** :
  - Modifié : la première version donnait 60 % d'échecs (30 % même chez les avancés), peu réaliste. Constante de la loi logistique recalée : 40 % d'échecs au total, 11 % chez les avancés, 20 % chez les réguliers, environ 70 % chez les débutants en difficulté et les décrocheurs.
  - Gardé : la colonne `profilCache` (vérité terrain) sert uniquement à évaluer le K-Means, jamais d'entrée à un modèle.
- **Captures** : _(à ajouter : statistiques par profil)_
- **Leçon** : vérifier les ordres de grandeur des données simulées avant d'entraîner : un taux d'échec irréaliste aurait faussé toutes les métriques.

### 04 — 2026-10-13 — Fin de la partie web : suppression, parcours, photo, demandes formateur (checklist 1.3 à 1.6)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : terminer toute la partie web du module User avant l'IA, pour que les autres membres puissent s'appuyer dessus
- **Contexte fourni** : CHECKLIST.md ; découpage proposé par l'IA en 8 petites étapes (supprimer mon compte, parcours côté serveur, photo, puis demandes formateur en 4 étapes, puis push)
- **Prompts** :
  > i got confused too much ! lets make it with little steps so i can understand !

  > okay do them all and then run a test !
- **Résultat** :
  - Suppression du compte : `POST /api/users/me/desactivation` (mot de passe exigé ; compte désactivé et non effacé pour ne pas casser les données des autres modules ; interdit à l'admin).
  - Parcours : `GET`/`PUT /api/users/me/parcours` (`@Embeddable Parcours`, domaines en JSON via `ListeTexteConverter`) ; enregistré à la fin du questionnaire d'accueil et modifiable dans Mon compte.
  - Photo : table `avatars` séparée (MEDIUMBLOB) ; `POST`/`DELETE /me/avatar`, `GET /{id}/avatar` ; type déduit des premiers octets (PNG, JPEG, WebP), jamais du nom ni du Content-Type ; 2 Mo ; le front charge l'image en blob car la gateway exige le JWT (`AvatarService`, cache par version).
  - Demandes formateur : entité `DemandeFormateur` (EN_ATTENTE, ACCEPTEE, REFUSEE) ; créée à l'inscription si l'expertise est donnée, ou depuis Mon compte ; admin : liste, accepter (rôle ENSEIGNANT + `formateurValide`), refuser avec motif obligatoire ; nouvelle demande possible après un refus.
  - `formateurValide` dans `UserDto` et dans le contrat 4 (`GET /api/users/{id}`), pour que cours-service bloque la publication d'un formateur non validé ; un formateur créé ou promu par l'admin est validé d'office.
  - Pages : Mon compte (photo, parcours, onglet « Devenir formateur » / « Profil de formateur », zone de suppression), Admin → Demandes formateurs (KPI réels, en attente / traitées), Admin → Utilisateurs (photos, « non validé »).
  - Socle (commit séparé) : `RegisterRequest` (front) et page d'inscription qui envoient expertise, présentation, lien ; questionnaire d'accueil qui enregistre aussi côté serveur ; `CONTRATS_API.md`.
  - 60 tests unitaires (23 nouveaux : `DemandeFormateurServiceTest`, `AvatarServiceTest`, ajouts dans `UserServiceTest` et `AuthServiceTest`).
- **Vérification** : `mvnw test` (60/60) ; curl via la gateway pour chaque étape (demande à l'inscription, doublon 409, contrat avant et après validation, refus sans motif 400, double traitement 409, nouvelle demande après refus, apprenant devenu formateur, parcours, photo relue octet par octet par un autre utilisateur, faux PNG, plus de 2 Mo, sans fichier, sans token, suppression) ; scénario complet en navigateur headless (étapes 1 à 7) + 2 captures.
- **Gardé / modifié / rejeté** :
  - Faux échecs des tests curl : les JSON avec accents envoyés depuis le terminal Windows arrivaient mal encodés (« Requête mal formée »). Rejoués depuis des fichiers UTF-8 : tout passe. Ce n'était pas un bug de l'application.
  - Corrigé après le test navigateur : espace manquant dans « Motif du refus : » (Angular supprime les espaces entre balises) ; la zone de dépôt gardait le dernier fichier refusé (elle est maintenant recréée après chaque envoi).
  - Choix : l'inscription d'un formateur sans expertise reste acceptée (exemples du README) ; la demande pourra être faite depuis Mon compte.
  - Non fait : la photo dans l'en-tête et le badge « 3 » du menu admin (socle, `core/layouts`) ; le bandeau « en attente » de l'accueil formateur lit encore le stockage local (socle).
- **Captures** : `captures/2026-10-13_04_admin-formateurs.png`, `captures/2026-10-13_04_mon-compte-photo.png` (navigateur headless, comptes de test supprimés ensuite)
- **Leçon** : quand un test échoue seulement sur certaines données (ici les accents), vérifier l'outil de test avant de chercher un bug dans le code.

### 03 — 2026-10-13 — Mon compte : profil et mot de passe (checklist 1.2)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : que chaque utilisateur modifie ses informations et son mot de passe, sur une page du module User
- **Contexte fourni** : la page `pages/profile` (socle, données de démo), `app.routes.ts`, CHECKLIST.md
- **Prompt** :
  > A
  (réponse au choix proposé par l'IA : A = nouvelle page dans `features/user` + petite PR sur `app.routes.ts`, B = modifier directement `pages/profile` par PR)
- **Résultat** :
  - Backend : champ `bio` (500 car.) ; `PUT /api/users/me` (nom, prénom, présentation ; email et rôle ignorés) ; `PUT /api/users/me/mot-de-passe` (ancien exigé, nouveau différent et conforme aux règles).
  - Mauvais mot de passe actuel → 400 et non 401, car le front déconnecte sur tout 401 hors `/auth`.
  - Front : page `features/user/pages/mon-compte` (chargement, erreur, informations, parcours, sécurité, thème), en-tête mis à jour après enregistrement (`refreshMe`).
  - Socle : `app.routes.ts`, 2 lignes (`/app/profil` et `/studio/profil` pointent vers la nouvelle page), dans un commit séparé à faire relire dans la PR.
  - 5 tests de plus dans `UserServiceTest` (36 au total).
- **Vérification** : `mvnw test` (36/36) ; 11 appels curl (modification, email/rôle ignorés, champs vides, bio trop longue, mauvais ancien mot de passe, même mot de passe, mot de passe faible, succès, ancien refusé et nouveau accepté à la connexion, sans token) ; navigateur headless (apprenant : modification + mot de passe, reste sur la page après une erreur ; formateur : onglet dédié) + capture.
- **Gardé / modifié / rejeté** :
  - Modifié : « Membre depuis » affichait le mois en anglais (pas de `LOCALE_ID` français) → `Intl.DateTimeFormat('fr-FR')`.
  - Retiré de la page (données inventées ou non enregistrées) : statistiques fictives de la carte de gauche, sessions ouvertes, onglet Notifications, langue et réglages non sauvegardés, dépôt de photo (arrive en 1.3), faux profil de formateur (arrive en 1.5).
  - Gardé : le parcours de l'apprenant, enregistré sur l'appareil comme avant (ajouté à la checklist en 1.6 pour le stocker côté serveur).
  - `pages/profile` n'est pas supprimée (socle) : à retirer dans la PR si l'équipe valide.
- **Captures** : `captures/2026-10-13_03_mon-compte.png` (navigateur headless, compte de test supprimé ensuite)
- **Leçon** : vérifier comment le front réagit aux codes d'erreur (ici le 401 qui déconnecte) avant de choisir le code renvoyé par le backend.

### 02 — 2026-10-13 — Gestion des utilisateurs par l'admin (checklist 1.1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : remplacer les données de démo de la page Admin → Utilisateurs par la vraie gestion des comptes
- **Contexte fourni** : CHECKLIST.md, la page `admin-users` existante (démo), CONTRATS_API.md (« endpoints internes à chaque module : évoluent librement »)
- **Prompt** :
  > okay make a check list for all what we still have to do and kets do it one by one !
- **Résultat** :
  - `prompts_journal/user/CHECKLIST.md` : tout le travail restant, en 5 parties.
  - Backend (ADMIN uniquement) : `POST /api/users`, `PUT /api/users/{id}`, `PATCH /api/users/{id}/statut`, `PUT /api/users/{id}/mot-de-passe` (débloque aussi le compte) ; liste triée du plus récent au plus ancien.
  - Garde-fous : un admin ne peut ni se désactiver ni se retirer le rôle ADMIN ; le dernier admin actif ne peut pas perdre son accès.
  - Règles de mot de passe regroupées dans `MotDePasse` (une seule source pour l'inscription, la création et la réinitialisation).
  - Front : `UserService` (5 appels), modèles dans `features/user/models`, page branchée (chargement, erreur avec « Réessayer », création, modification, activation en lot, réinitialisation du mot de passe).
  - 12 tests `UserServiceTest` (31 tests au total dans user-service).
- **Vérification** : `mvnw test` (31/31) ; 13 appels curl via la gateway (création, doublon, modification, désactivation + connexion refusée, verrouillage puis réinitialisation, garde-fous admin, 403, 404) ; parcours navigateur headless (liste réelle, création par la boîte de dialogue, désactivation/réactivation, pas de « Désactiver » sur son propre compte) + capture.
- **Gardé / modifié / rejeté** :
  - Bug trouvé au test navigateur : après « Désactiver », la ligne restait « Actif » alors que la base était à jour. Cause : en zoneless, la table n'était alimentée que par un `effect`, exécuté trop tard. Corrigé avec `majTous()` qui met aussi la table à jour tout de suite.
  - Retiré : le delta du KPI « Comptes » (`bw-kpi` affiche toujours « % », ce qui donnait « +3 % » pour 3 comptes) ; `bw-kpi` est dans le socle, donc non modifié.
  - Retirés car non implémentés : « Importer (CSV) », « Envoyer un email », « Voir l'activité », colonne « Profil ou expertise » (reviendra avec les demandes formateur, 1.5).
  - Choix : l'admin saisit le mot de passe initial lui-même ; l'API ne génère ni ne renvoie aucun mot de passe.
- **Captures** : `captures/2026-10-13_02_admin-users.png` (navigateur headless, données de test supprimées ensuite)
- **Leçon** : vérifier l'écran après chaque action, pas seulement l'API : la base était juste mais l'affichage ne l'était pas.

### 01 — 2026-10-13 — Inscription et connexion : durcissement et tests

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : vérifier l'inscription et la connexion existantes, puis combler leurs manques
- **Contexte fourni** : CLAUDE.md, README.md, CONTRATS_API.md, le sujet `Bawsla_modules_IA.pdf`, le code de `user-service` ; stack lancée avec `docker compose up -d --build`
- **Prompt** :
  > OKAY ! now i need u to work on the user module ! ( the basic create account and login
- **Résultat** :
  - L'IA a d'abord testé l'existant par curl via la gateway (inscription, doublon 409, validation 400, rôle ADMIN refusé, mauvais mot de passe 401, `/me`, sans token 401, rôle 403, en-têtes `X-User-*` falsifiés rejetés) : tout fonctionnait.
  - Manques trouvés et corrigés dans `user-service` :
    - mot de passe : au moins une lettre et un chiffre (`@Pattern` dans `RegisterRequest`) ;
    - protection brute force : 5 échecs consécutifs → compte verrouillé 15 min (`User.enregistrerEchec`, `@Transactional(noRollbackFor = UnauthorizedException.class)` pour que l'échec reste compté malgré le 401) ;
    - `derniereConnexion` enregistrée à chaque connexion et renvoyée par `/me` (servira au ML : jours depuis la dernière activité) ;
    - `ClockConfig` : horloge injectée pour figer le temps dans les tests.
  - 19 tests unitaires : `AuthServiceTest` (11, JUnit 5 + Mockito, sans base) et `RegisterRequestValidationTest` (8).
- **Vérification** : `./mvnw -q -pl user-service test` (19/19 verts) ; reconstruction du conteneur puis curl via la gateway : mot de passe faible refusé, `derniereConnexion` renseignée, 6ᵉ tentative bloquée avec « Réessaie dans 15 minutes » ; colonnes vérifiées dans phpMyAdmin.
- **Gardé / modifié / rejeté** :
  - Gardé : tout le code produit.
  - Modifié : le message de verrouillage affichait « 14 minutes » (troncature) → arrondi au supérieur avec `Math.ceilDiv`.
  - Non fait volontairement : les pages login/register (dossier `pages/`, socle) ne sont pas modifiées ; elles affichent déjà les messages du backend.
- **Captures** : _(à ajouter : `captures/2026-10-13_01_auth-tests.png`)_
- **Leçon** : tester l'existant avant d'écrire du code ; le test a révélé les vrais manques (mot de passe faible accepté, aucune protection brute force).
