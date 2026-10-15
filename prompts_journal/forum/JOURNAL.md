# Journal des prompts — module Forum

**Membre** : Ouji Malek
**Branche** : `feature/forum`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 05 — 2026-10-15 — Lot 5 : La Vigie, RAG, réponse sourcée, niveau 3, assistant privé

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code, mode Auto
- **Objectif** : `repondre` (v1 → v3), vérification des citations, confiance calculée, abstention, mode Guide-moi,
  niveau 3 `repondre-general` à la demande ; côté forum-service, génération asynchrone à la publication,
  validation / correction / rejet par le formateur, assistant privé et « Publier comme question » (plan §4.2, §4.3,
  §4.9).
- **Contexte fourni** : plan §4.2 à §4.9, §6.2, §8 ; code des lots 2 à 4 ; travail du lot 5 déjà écrit sur le disque
  (session interrompue), repris là où il s'était arrêté.
- **Prompt** :
  > Reprends le lot 5 du plan (prompts_journal/forum/PLAN_FORUM.md). Le travail non commité est sur le disque. Fais
  > dans l'ordre :
  > 1. Ajoute repondre et repondreGeneral dans FauxIa, puis mvnw clean test.
  > 2. Corrige les 3 attentes de EngagementApiTest (la notification « réponse prête » de La Vigie est voulue).
  > 3. Écris VigieTest (abstention sans LLM, sources et confiance, mode examen, niveau 3 refusé si désactivé ou en
  > examen, droits de validation, assistant privé et publication).
  > 4. Journal 05, captures, commit, git push origin feature/forum.
  > Puis enchaîne les lots 6 à 10 avec un résumé court à la fin de chacun.
- **Résultat** :
  - **ai-service** : `rag.py`. Le modèle ne fait que proposer : chaque citation est revérifiée sans LLM
    (rapidfuzz `partial_ratio` ≥ 90), une citation introuvable est retirée avec son `[n]`, et sans aucune citation
    vérifiée la réponse devient l'abstention. Confiance calculée (40 % score de récupération, 30 % phrases citées,
    30 % citations vérifiées), jamais recopiée du modèle. Prompts `repondre`, `guider` et `repondre_general` en
    v1 → v3 (rôle, contexte, tâche, format, contraintes ; passages et question entre balises de données).
  - **forum-service** : `RagService` (deux sources, cours et réponses validées du forum, cosinus + mots fusionnés
    par RRF, bonus de 20 % pour le cours, top 4) ; `VigieService` (abstention SANS appel au LLM sous le seuil de
    pertinence, notification au formateur, validation / correction avec l'original gardé / rejet, avis utile,
    niveau 3) ; `BaseConnaissanceService` (réponses validées vectorisées dans `passage_forum`) ;
    `AssistantService` (conversation privée, 6 derniers échanges pour condenser les questions de suivi,
    brouillon « Publier comme question », réponse jointe au fil sans nouvel appel) ; `VigieController`.
  - Tests : `FauxIa` répond en citant le premier passage (ou s'abstient sur demande) ; `VigieTest` (24 tests) ;
    3 attentes d'`EngagementApiTest` corrigées (notification « réponse de La Vigie prête », et « hors cours » pour
    la formatrice quand la question sort du texte de démonstration).
- **Vérification** : `mvnw clean test` 107 verts dont 24 nouveaux ; `pytest tests/forum` 47 verts, couverture de
  branches 98 % ; suite Python complète 159 verts. Contrôle de mutation : en désactivant le refus du niveau 3 en
  mode examen, `laReponseGeneraleEstRefuseeEnModeExamen` échoue bien (200 au lieu de 403).
- **Gardé / modifié / rejeté** :
  - **Défaut trouvé par les tests** : la toute première question d'un cours lançait en même temps l'indexation du
    cours et La Vigie ; La Vigie passait avant l'index et s'abstenait à tort (en production, avec le pool de
    threads, c'était une course). Corrigé par `IndexationService.assurerIndex()` (verrou par cours, indexation sur
    place si le cours ne l'a jamais été), appelé par La Vigie avant de chercher.
  - Avant de soupçonner le seuil de pertinence (0,55), j'ai mesuré les scores réels avec le faux vectoriseur par
    hachage : 0,45 pour la question d'origine, 0,71 pour une question proche du passage LEFT JOIN, 0,26 hors cours.
    Le seuil n'a pas été touché (il se règle sur le vrai modèle au lot 9) ; les tests utilisent la question mesurée.
  - Rejeté : la confiance auto-déclarée par le modèle (champ ignoré, la confiance est calculée).
  - Git Bash a encore interprété des `\n` dans un heredoc Python : fichier réécrit avec l'outil d'écriture.
- **Décisions non couvertes par le plan** :
  - Seuil de pertinence provisoire 0,55 (`vigie.seuil-pertinence`, réglable par l'admin), 0,5 en mots-clés seuls.
  - Mode examen : la réponse complète est générée mais cachée aux apprenants (indices seulement) ; le formateur la
    voit. Passé la date de fin, elle redevient visible sans régénération.
  - La réponse générale n'est proposée qu'après une abstention (409 sinon) et n'est générée qu'une fois par fil.
  - Une question publiée depuis l'assistant reçoit une copie de la dernière réponse sourcée (pas la réponse
    générale), seulement si la conversation appartient à l'auteur et porte sur le même cours.
- **Captures** : `captures/2026-10-15_06_lot5-tests-vigie.png`
- **Leçon** : une course entre deux tâches de fond ne se voit pas avec un exécuteur parallèle ; l'exécuteur
  synchrone des tests l'a rendue reproductible à chaque fois.

---

### 04 — 2026-10-15 — Lot 4 : NLP, embeddings, recherche sémantique, index des cours

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code, mode Auto
- **Objectif** : PR socle `embed()` ; routes `vectoriser`, `indexer` (découpage), `reformuler` ; côté forum-service,
  base vectorielle, indexation des cours avec hash, recherche hybride RRF, similaires en direct, discussions liées,
  doublons (plan §4.1, §4.2 étapes 1 à 6, §5.2).
- **Contexte fourni** : `app/core/llm_client.py` lu en entier ; documentation Gemini des embeddings
  (ai.google.dev) et signatures réelles des SDK installés (`google-genai`, `openai`), vérifiées avant d'écrire.
- **Prompt** : suite du prompt de l'entrée 02.
- **Résultat** :
  - **Socle (commit séparé `chore(socle)`, ajouts uniquement)** : `LLMClient.embed()` et `EmbeddingResult` ;
    Gemini `gemini-embedding-001` (un `Content` par texte, `task_type` RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY,
    768 dimensions, lots de 100, normalisation) ; OpenAI `text-embedding-3-small` ; Groq sans embeddings (503
    clair) ; stub déterministe par hachage ; routage avec secours ; 14 tests.
  - **ai-service / forum** : découpage (~800 caractères, 120 de chevauchement, paragraphes puis phrases, titres
    Markdown en sections) ; `securite.py` (Sanitizer, balises de données neutralisées, ouvrantes comme fermantes) ;
    prompts `reformuler` v1 → v3 (v3 : exemples derja / arabizi, questions de suivi rendues autonomes) ;
    repli sans LLM (la question nettoyée telle quelle) ; 33 tests, couverture de branches 98 %.
  - **forum-service** : `IaClient` via la gateway avec le jeton de l'utilisateur ; tâches de fond qui copient
    l'identité au moment de la soumission ; vectorisation des discussions après validation de la transaction ;
    `IndexationService` (contrat 2 et chapitres s'ils existent, hash SHA-256, texte de démonstration en profil dev,
    état consultable et réindexation) ; `RechercheService` (cosinus en Java sur les vecteurs du même modèle,
    FULLTEXT MySQL ou score de mots, RRF k = 60, similaires, liées, « Pour toi », doublon à la publication = 409).
- **Vérification** : `pytest` 145 verts ; `mvnw test` 83 verts dont 15 nouveaux (`RechercheApiTest`) avec un
  `FauxIa` déterministe. Le scénario de démo §12 étape 3 (« kifech nfiltri b LEFT JOIN… » retrouve la question
  résolue) est un test.
- **Gardé / modifié / rejeté** :
  - La doc Gemini a montré deux pièges : `gemini-embedding-001` ne normalise pas sous 3072 dimensions, et
    `gemini-embedding-2` fusionne plusieurs textes en un seul vecteur s'ils ne sont pas enveloppés un par un.
    Les deux sont gérés dans le cœur.
  - Premier découpage faux : le dernier passage d'une section disparaissait si son texte figurait déjà dans le
    précédent (comparaison de texte). Corrigé en comptant les phrases nouvelles depuis le chevauchement.
  - Tâches de fond : une transaction `REQUIRED` lancée en phase AFTER_COMMIT aurait rejoint la transaction déjà
    validée (écritures perdues). Toutes les tâches utilisent `REQUIRES_NEW`.
  - Deux tests de recherche échouaient à cause des données de test (même contenu pour toutes les discussions,
    cosinus 0,74 entre sujets sans rapport) : mesuré, puis contenus distincts dans ces tests. Le code est inchangé.
  - Git Bash interprète les `\n` dans les heredocs Python : modifications refaites avec l'outil d'édition.
- **Décisions non couvertes par le plan** :
  - Seuils de similarité provisoires (0,75 « similaire », 0,9 « doublon ») réglables par l'admin, fixés au lot 9.
  - Repli lexical des similaires : seuil fixe de 0,5 sur le score de mots (ce n'est pas un cosinus), et jamais de
    « doublon » sans vecteurs.
  - `IndexerRequest.texte` ajouté (texte libre sans pagination, forme actuelle du contrat 2).
  - Texte de démonstration activé par `forum.rag.texte-demo=true` (profil dev ou variable d'environnement).
  - L'ai-service renvoie les champs `null` au lieu de les omettre (comportement du socle, inchangé ici).
  - La mise à jour de `CONTRATS_API.md` (routes du forum) sera faite en une seule fois quand toutes les routes IA
    existeront (lot 7), dans un commit `chore(socle)`.
- **Captures** : `captures/2026-10-15_05_lot4-tests-nlp.png`
- **Leçon** : avant de corriger un seuil ou un algorithme parce qu'un test échoue, mesurer le score réel : ici,
  c'étaient les données de test.

---

### 03 — 2026-10-15 — Lot 3 : forum-service, engagement

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code, mode Auto
- **Objectif** : suivis, favoris, lu / non lu, vues, notifications, réputation, niveaux, badges, classement,
  profil, statistiques, avec leurs tests.
- **Contexte fourni** : plan §3.4, §3.6, §3.8, §4.7 (indicateurs) ; événements publiés au lot 2.
- **Prompt** : suite du prompt de l'entrée 02 (« continue les lots suivants sans attendre ma validation »).
- **Résultat** :
  - Tout passe par des écouteurs des événements du lot 2, dans la transaction de l'action : points, badges,
    abonnements et notifications sont validés ou annulés avec elle.
  - `ReputationService` : barème du plan ; annuler un vote, une acceptation, une approbation ou un masquage rejoue
    exactement l'inverse de ce qui avait été donné, d'après l'historique (`solde`), sans dérive possible.
  - `BadgeService` : les 10 badges (Gardien prêt pour la modération du lot 6), série de jours actifs pour Assidu,
    progression vers chaque badge.
  - `NotificationService` : réponse dans un fil suivi, commentaire, mention `@Prénom Nom` parmi les participants
    (remplace la notification de réponse pour la personne mentionnée), acceptation, approbation, masquage, badge.
  - `SuiviService` : suivi automatique de l'auteur et des répondants, favoris, « Tout marquer comme lu »,
    « Ça répond à ma question » (`POST /discussions/{id}/reponse-trouvee`, badge Éclaireur).
  - `ProfilService` (carte de survol, Mon activité, réputation détaillée, classement par cours sur 7 ou 30 jours) et
    `StatistiquesService` (questions, sans réponse, délai de première réponse, résolution, couverture et utilité de
    La Vigie, par chapitre et par semaine ; formateur du cours ou admin).
- **Vérification** : `mvnw test`, 65 tests verts dont 21 nouveaux (`EngagementApiTest`). Une horloge de test réglable
  simule le passage des jours (Secouriste après 24 h, Assidu après 7 jours, classement qui expire).
- **Gardé / modifié / rejeté** :
  - Deux tests échouaient au premier passage : ils oubliaient que les badges produisent aussi des notifications.
    Le code avait raison ; j'ai corrigé les attentes et rendu explicite le cas « marquer la notification d'un autre
    membre n'a aucun effet ».
  - Le profil public comptait d'abord les réponses anonymes : corrigé, un autre membre ne voit que les messages
    non anonymes (sinon l'anonymat fuirait par les compteurs).
  - Un heredoc Python imbriqué a été mal interprété par Git Bash : les modifications de dépôts ont été refaites
    avec l'outil d'édition.
- **Décisions non couvertes par le plan** :
  - On ne gagne ni points ni badge en acceptant sa propre réponse ; le +2 « pour celui qui accepte » ne va qu'à
    l'auteur de la question, une seule fois par fil.
  - Les badges obtenus restent acquis même si la cause disparaît (vote retiré).
  - Statistiques par chapitre libellées « Chapitre n° X » : les titres de chapitres ne sont pas lisibles sans la
    route `/chapitres` du module Cours. Sans `coursId`, les statistiques sont réservées à l'admin.
  - « Pour toi » (notions ratées) est reporté au lot 4 : il s'appuie sur la recherche sémantique.
- **Captures** : `captures/2026-10-15_04_lot3-tests-engagement.png`
- **Leçon** : quand un test échoue, vérifier d'abord laquelle des deux attentes est fausse, le code ou le test.

---

### 02 — 2026-10-15 — Lot 2 : forum-service, cœur web

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code, mode Auto
- **Objectif** : services, contrôleurs et règles du forum : discussions, réponses, commentaires, votes, réactions,
  étiquettes, meilleure réponse, approbation, anonymat, édition et versions, pièces jointes, droits, anti-flood,
  clients Feign avec cache, outils de modération d'un fil, tests Java.
- **Contexte fourni** : modèle validé au lot 1, plan §3.1 à §3.3, §3.7, §4.8, §5.4, §6.1, `quiz-service` comme
  référence (client Feign, tests H2 avec services simulés).
- **Prompt** :
  > OK, modèle validé. Enchaîne sur le lot 2, puis continue les lots suivants sans attendre ma validation, sauf si
  > une décision est irréversible ou touche un autre module. À la fin de chaque lot, fais-moi un résumé court.
- **Résultat** :
  - Clients Feign `CoursClient` (contrat 1, 2 et chapitres), `UserClient` (contrat 4), `QuizClient` (contrat 3),
    propagation des en-têtes `X-User-*`. `AnnuaireService` : cache mémoire de 5 min, et dégradation propre
    (« Cours n° X », « Membre n° X », modération réservée à l'admin si cours-service ne répond pas).
  - `AccesService` : rôle connu exigé, formateur DU cours (enseignantId) ou admin pour modérer, sanctions
    LECTURE_SEULE / SUSPENSION (« Ton accès en écriture au forum est suspendu jusqu'au … »), forum fermé,
    anti-flood 5 questions et 30 messages par heure (429), anonymat réservé aux apprenants et réglable par cours.
  - Services : discussions (liste filtrée et paginée, fil complet, vues uniques par jour, lu / non lu, versions),
    réponses, commentaires, votes (+1 / −1, annulables, pas sur son message, −1 à partir de 50 points),
    réactions, étiquettes normalisées avec autocomplétion, meilleure réponse, approbation, modération d'un fil
    (épingler, verrouiller, masquer / rétablir discussion, réponse ou commentaire, déplacer, fusionner, étiquettes),
    tracée dans le journal non modifiable ; pièces jointes vérifiées sur leurs octets magiques.
  - `PresentationService` : anonymat (auteurId jamais renvoyé aux autres apprenants), contenu masqué caché,
    droits d'affichage, chargement par lots (votes, réactions, lectures, approbations) pour éviter le N+1.
  - Événements Spring (`DiscussionCreee`, `ReponseCreee`, `VoteModifie`, `ReponseAcceptee`, `ReponseApprouvee`,
    `MessageMasque`…) publiés dès maintenant : les lots 3 à 6 s'y abonnent (points, notifications, IA).
  - `MigrationFulltext` : index FULLTEXT créés au démarrage sur MySQL s'ils manquent.
- **Vérification** : `mvnw test`, 44 tests verts (35 tests d'API MockMvc sur H2 avec cours-service, user-service et
  quiz-service simulés). Contrôle de mutation : en désactivant la règle « pas de vote sur son propre message »,
  le test correspondant échoue bien. `mvnw package` sur tout le backend : vert.
- **Gardé / modifié / rejeté** :
  - Une première version de la vérification des octets magiques contenait une expression inutile (flux sans effet) :
    réécrite en boucle simple.
  - La requête JPQL comparant un enum à un littéral (`d.type = 'QUESTION'`) a été retirée : fragile selon le
    dialecte, et inutile à ce lot.
  - La vérification de l'anonymat, d'abord dupliquée dans `DiscussionService`, a été déplacée dans `AccesService`
    pour servir aussi aux réponses.
- **Décisions non couvertes par le plan** :
  - 429 anti-flood : `TropDeRequetesException` dans le module (elle étend l'exception du socle sans la modifier).
  - Étiquettes facultatives (0 à 5) plutôt que « 1 à 5 » : moins de friction pour une première question ;
    normalisées en minuscules, lettres arabes acceptées.
  - Épingler / verrouiller : `POST` pour activer, `DELETE` pour désactiver (au lieu d'une bascule ambiguë).
    Ajout de `PUT /discussions/{id}/etiquettes` et du masquage des réponses et commentaires.
  - Une discussion masquée renvoie 404 aux autres apprenants (comme si elle n'existait pas) ; la liste exclut
    les fils masqués et supprimés pour tous (les modérateurs les verront dans la file de modération, lot 6).
  - Recherche `q` en `LIKE` en attendant la recherche hybride (lot 4) ; tri « pertinence » = activité d'ici là.
  - Pièces jointes stockées sur disque (`FORUM_PIECES_JOINTES_DOSSIER`, `data/` ignoré par git). En Docker,
    il faudra un volume (PR socle sur `docker-compose.yml`, à proposer au lot 10). Une balise `<img>` n'envoie pas
    le JWT : le front chargera les images via HttpClient (lot 8).
  - Docker n'étant pas démarré sur le poste, le démarrage réel sur MySQL (index FULLTEXT) sera vérifié au lot 10.
- **Captures** : `captures/2026-10-15_03_lot2-tests-api.png` (les 44 tests)
- **Leçon** : un test qui passe du premier coup se vérifie en cassant la règle qu'il protège.

---

### 01 — 2026-10-15 — Lot 1 : préparation, structure, modèle de données et DTO

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code, mode Auto
- **Objectif** : synchroniser `feature/forum` avec `develop`, poser le plan dans le dépôt et construire le lot 1
  du plan (§11) : structure des dossiers, modèle de données (entités JPA), DTO Java, schémas Pydantic de l'ai-service
  et modèles Angular. Les services viennent au lot 2, après validation du modèle.
- **Contexte fourni** : `CLAUDE.md`, `DESIGN.md`, `CONTRATS_API.md`, `prompts_journal/forum/PLAN_FORUM.md`,
  code existant de `forum-service`, référence `quiz-service` (client Feign, `FauxIa`, tests H2, prompts versionnés,
  lus en lecture seule depuis `origin/feature/quiz`).
- **Prompt** :
  > 1. Récupère le projet dans CE dossier (sans créer de sous-dossier) : git init, git remote add origin …,
  > git fetch --all, git checkout feature/forum, git merge origin/develop
  > 2. Déplace settings.local.json dans .claude/settings.local.json et ajoute la ligne .claude/settings.local.json
  > dans .git/info/exclude.
  > 3. Déplace PLAN_FORUM.md dans prompts_journal/forum/PLAN_FORUM.md.
  > 4. Règle absolue : tu travailles uniquement sur la branche feature/forum. […]
  > Ensuite, lis CLAUDE.md, DESIGN.md, CONTRATS_API.md et prompts_journal/forum/PLAN_FORUM.md en entier, et suis la
  > section 13.2 du plan : construis le module Forum lot par lot (section 11). À la fin de chaque lot : build et
  > tests verts, entrée dans prompts_journal/forum/JOURNAL.md, captures dans captures/, commit, puis
  > git push origin feature/forum. Commence par le lot 1 et montre-moi la structure des dossiers et le modèle de
  > données avant d'écrire les services.
- **Résultat** :
  - Branche synchronisée (`git merge origin/develop` en avance rapide : template, cœur IA Gemini / Groq, contrats).
  - `backend/forum-service` : 29 entités (`entity/`), 18 énumérations (`entity/enums/`), `VecteurConverter`
    (vecteurs float32 en BLOB), 67 DTO `record` (`dto/`), 5 vues locales des autres services (`client/` :
    `CoursDto`, `CoursTexteDto`, `ChapitreDto`, `UserDto`, `NotionRateeDto`), H2 pour les tests.
    L'ancien CRUD minimal (`DiscussionController/Service/Repository`, `DiscussionResponse`) est retiré : il est
    réécrit au lot 2 sur le nouveau modèle.
  - `ai-service/app/modules/forum` : `schemas.py` (toutes les routes du §6.2, entrées en `extra="forbid"`),
    `config.py` (`PROMPT_FORUM_VERSION`, défaut v3), `prompts/__init__.py` (registre `Prompt` versionné, repli sur v3).
  - `frontend/src/app/features/forum/models` : `forum`, `vigie`, `engagement`, `moderation` (miroir des DTO) ;
    `forum.service.ts` branché sur ces modèles.
- **Vérification** : `mvnw test` (9 tests : schéma créé sur H2 en mode MySQL, vote unique, vue unique par jour,
  journal non modifiable, valeurs par défaut, aller-retour d'un vecteur de 768 dimensions, niveaux de réputation) ;
  `pytest tests/forum` (15 tests) et suite complète (113) ; `ng build` sans avertissement.
- **Gardé / modifié / rejeté** :
  - Les énumérations étaient d'abord créées par Hibernate en type MySQL natif `ENUM(...)` (visible dans le DDL des
    tests) : avec `ddl-auto: update`, ajouter une valeur plus tard aurait cassé les insertions. Corrigé en forçant
    `VARCHAR(30)` (`@JdbcTypeCode(SqlTypes.VARCHAR)`).
  - La colonne `date` du journal de modération est devenue `creeLe` (mot réservé en H2, et cohérence avec les autres tables).
  - La méthode `AuteurResponse.anonyme()` entrait en conflit avec l'accesseur du record : renommée `masque()`.
- **Décisions non couvertes par le plan (option la plus simple qui respecte CLAUDE.md)** :
  - Clés étrangères : identifiants `Long` partout (comme le plan), sauf `Discussion.etiquettes` en `@ManyToMany`
    (table `discussion_etiquette`, même base) ; clés techniques + contraintes d'unicité au lieu de clés composées.
  - Ajouts au modèle : table `avis_vigie` (un seul avis Utile / Pas utile par personne), statut `EN_ATTENTE` de
    `reponse_ia` (génération asynchrone), `Discussion.faq`, `fusionneeDansId`, `dernierActeurId`,
    `Signalement.escalade`, `auteurCibleId` et `coursId` (file du formateur, récidive), `Reponse.anonyme`,
    `Reputation.joursActifsConsecutifs` (badge Assidu), `IndexCours.sourceDemo`, `ReponseIa.jetonsEstimes`.
  - `journal_moderation` est `@Immutable` et ses colonnes `updatable = false` (historique non modifiable).
  - « Publier comme question » renvoie un brouillon pré-rempli ; la publication finale passe par
    `POST /discussions` avec `conversationId`.
  - Le stub `POST /api/ai/forum/questions-similaires` (contrat 5) est conservé tant que `CONTRATS_API.md` n'est
    pas mis à jour (PR socle du lot 4).
  - Environnement : seul un JDK 17 était installé ; un Temurin 21 portable est placé dans `~/.jdks` (hors dépôt)
    et utilisé via `JAVA_HOME` pour Maven.
- **Captures** :
  - `captures/2026-10-15_01_lot1-builds-tests.png` (Maven, pytest, ng build)
  - `captures/2026-10-15_02_lot1-modele-donnees.png` (les 29 tables, groupées par domaine)
- **Leçon** : lire le DDL généré par les tests dès le premier lot ; c'est là que le problème des `ENUM` natifs est apparu.
