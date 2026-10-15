# Journal des prompts — module Forum

**Membre** : Ouji Malek
**Branche** : `feature/forum`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

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
