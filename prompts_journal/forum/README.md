# Module Forum de Bawsla : « Forum + La Vigie »

**Autrice :** Malek Ouji · **Branche :** `feature/forum` · **Module IA :** NLP sémantique + RAG

Ce document s'adresse aux membres de l'équipe qui intègrent le forum avec leurs modules. Il explique ce que fait
le module, où se trouve le code, ce qu'il lit chez les autres, ce qu'il a modifié dans le socle, comment le lancer
et le tester.

Documents liés, dans ce même dossier :
- `PLAN_FORUM.md` : le plan complet, avec les choix et leur justification ;
- `JOURNAL.md` : le journal des prompts, entrées 01 à 12 ;
- `captures/` : les captures de chaque lot et de chaque écran (clair, sombre, mobile) ;
- `PR_FORUM.md` : le texte de la pull request.

---

## 1. En bref

Un forum de questions-réponses **par cours**, avec une IA, **La Vigie**, qui :
1. **reconnaît les questions déjà posées, même formulées autrement** : embeddings + similarité cosinus, recherche
   hybride (cosinus + FULLTEXT, fusion RRF), reformulation des questions en derja, arabizi ou anglais ;
2. **répond uniquement à partir du cours, en citant le passage**. C'est un RAG : le cours est découpé et indexé, chaque
   citation est vérifiée sans LLM et la confiance est calculée. Si le cours ne traite pas le point, La Vigie le dit
   au lieu d'inventer ;
3. **protège le forum** : règles sans LLM puis modération par LLM, défense contre l'injection de prompt, données
   personnelles masquées avant tout envoi au LLM.

Sortie du RAG, au format du cahier des charges :
`{ reponse, sources: [{ cours, passage }], confiance, questionsSimilaires }`.

Trois espaces, chacun avec ses outils :

| Espace | Route | Pour quoi faire |
|---|---|---|
| Apprenant | `/app/forum` | chercher, poser une question, répondre, voter ; assistant privé « Demander au cours » ; activité, points et badges ; notifications |
| Formateur | `/studio/forum` | à traiter, réponses de La Vigie (valider, corriger, rejeter), signalements, tendances, index des cours, réglages par cours (dont le mode examen) |
| Administrateur | `/admin/forum` | vue d'ensemble, modération globale, sanctions, supervision de La Vigie, réglages globaux, journal de modération |

---

## 2. Où est le code

```
backend/forum-service/                    Spring Boot, port 8084, base MySQL bawsla_forum
  src/main/java/tn/esprit/bawsla/forum/
    controller/   10 contrôleurs (voir §5)
    service/      métier : discussions, Vigie, indexation, recherche, modération, réputation, tendances…
    entity/       29 entités JPA (enums stockées en VARCHAR)
    client/       clients Feign vers cours-service, user-service, quiz-service et ai-service
  src/main/resources/demo/cours-demo.md   texte de cours de démonstration (voir §7)
ai-service/app/modules/forum/             FastAPI : routes /api/ai/forum/** (voir §5.2) ; nlp.py, rag.py, moderation.py, aides.py
  prompts/        prompts versionnés v1 → v3
  evaluation/     jeux de données, script evaluer_forum.py, resultats/ (réel) et resultats/dry-run/
ai-service/tests/forum/                   tests pytest + e2e_forum.py (boîte noire via la gateway)
frontend/src/app/features/forum/          Angular : pages, services, modèles, composant <app-forum-apercu>
prompts_journal/forum/                    plan, journal (entrées 01 à 12), captures, ce README
```

---

## 3. Ce que le forum lit chez les autres modules

Le forum **lit seulement** des routes déjà décrites dans `CONTRATS_API.md`. Il **n'exige aucune route nouvelle** d'un
autre module, et il **fonctionne si l'une d'elles ne répond pas** : les délais Feign sont courts (2 s pour la
connexion, 5 s pour la lecture) et l'affichage se dégrade proprement.

| Donnée | Route lue | Si elle ne répond pas |
|---|---|---|
| Titre et formateur d'un cours | `GET /api/cours/{id}` | « Cours n° X » ; modération réservée à l'admin |
| Texte du cours pour le RAG | `GET /api/cours/{id}/texte` et `/chapitres` | La Vigie s'appuie sur la base de connaissances du forum (réponses validées) |
| Nom et rôle d'un auteur | `GET /api/users/{id}` | « Membre n° X » |
| Notions ratées (« Pour toi ») | `GET /api/quiz/notions-ratees` | la section est masquée |

**Pour l'équipe Cours :** le RAG prend le texte réel des cours dès que `/texte` et `/chapitres` renvoient le contenu
(branche `feature/cours`). Rien à faire côté forum : l'index se recalcule tout seul quand le texte change (hash
SHA-256), et un formateur peut forcer « Réindexer ».

---

## 4. Fichiers communs modifiés (socle)

Uniquement des **ajouts**, dans des commits `chore(socle): …` séparés :

| Fichier | Modification |
|---|---|
| `ai-service/app/core/llm_client.py` (+ `tests/core/test_embeddings.py`) | nouvelle méthode `LLMClient.embed()` : Gemini `gemini-embedding-001` (768 dimensions, normalisé), OpenAI `text-embedding-3-small`, stub déterministe, routage avec secours. Aucune méthode existante n'est modifiée. |
| `ai-service/app/core/config.py` | réglages du modèle d'embedding (modèle, dimension) |
| `CONTRATS_API.md` | contrat 5 : routes IA du forum ; endpoints internes du forum |
| `.env.example` | variable `FORUM_RAG_TEXTE_DEMO=false` |
| `docker-compose.yml` | forum-service : variable `FORUM_PIECES_JOINTES_DOSSIER` et volume `forum_pieces_jointes` (pièces jointes conservées quand le conteneur est recréé) |
| `frontend/src/app/app.routes.ts` | 1 ligne : `/admin/forum` (`FORUM_ADMIN_ROUTES`) |
| `frontend/src/app/core/layouts/admin/admin-shell.ts` | 1 entrée de menu « Forum » |
| `frontend/src/app/core/layouts/parts/notif-menu.ts` | la cloche lit les notifications du forum (compteur toutes les 30 s) au lieu des données de démo |
| `frontend/src/app/pages/student-home`, `teacher-home` | le bloc forum de démo est remplacé par `<app-forum-apercu>` |

**Conflit connu au moment de la fusion :** `CONTRATS_API.md` est aussi modifié sur `feature/user-ia`. Il suffit de
garder les deux ajouts. Aucun conflit avec `feature/cours` ni `feature/quiz` (vérifié par une fusion d'essai).

---

## 5. API

Tout passe par la gateway, avec le JWT. Les réponses utilisent l'enveloppe `ApiResponse` et les erreurs passent par
`common.exception`. Les droits sont vérifiés côté backend avec `CurrentUser` ; les guards Angular ne servent qu'au
confort d'affichage.

### 5.1 forum-service (`/api/forum/**`)

| Domaine | Routes principales |
|---|---|
| Discussions | `GET/POST /discussions`, `GET/PUT/DELETE /discussions/{id}`, `/versions`, `/accepter/{reponseId}`, `/etiquettes` ; modération : `/epingler`, `/verrouiller`, `/masquer`, `/retablir`, `/deplacer`, `/fusionner` |
| Réponses, commentaires, votes | `POST /discussions/{id}/reponses`, `PUT/DELETE /reponses/{id}`, `/approbation`, commentaires sur une discussion ou une réponse, `PUT /votes`, `PUT/DELETE /reponses/{id}/reactions/{type}` |
| Recherche (NLP) | `POST /discussions/similaires`, `GET /discussions/{id}/liees`, `GET /pour-moi`, `GET /index/{coursId}`, `POST /index/{coursId}/reindexer` |
| La Vigie (RAG) | `GET /discussions/{id}/vigie`, `/vigie/regenerer`, `/vigie/general` (niveau 3, sur demande) ; `POST /vigie/{id}/avis`, `/valider`, `/corriger`, `/rejeter` ; assistant privé : `/vigie/conversations`, `/messages`, `/publier` |
| Engagement | `/suivre`, `/favori`, `/discussions/lu`, `/notifications` (+ `/compteur`, `/lues`), `/profils/{userId}`, `/moi/activite`, `/moi/reputation`, `/classement`, `/statistiques` |
| Modération | `POST /signalements`, `GET /moderation/file`, `POST /signalements/{id}/decision`, `POST /composer/verifier`, `GET/PUT /cours/{coursId}/parametres` |
| Aides et tendances | `GET /discussions/{id}/resume`, `POST /composer/ameliorer`, `GET /tendances`, `GET /cours/{coursId}/apprenants-bloques`, `POST /cours/{coursId}/faq` |
| Admin (ADMIN seulement) | `/admin/moderation/file`, `/admin/sanctions`, `/admin/utilisateurs/{id}/historique`, `/admin/parametres`, `/admin/journal` (+ `/export` en CSV), `/admin/statistiques`, `/admin/vigie/supervision`, `/admin/index/reindexer-tout` |
| Divers | `GET /etiquettes`, `POST/GET /pieces-jointes`, `GET /health` |

### 5.2 ai-service (`/api/ai/forum/**`)

`vectoriser`, `indexer` (découpage en passages d'environ 800 caractères, chevauchement de 120), `reformuler`,
`repondre` (RAG sourcé), `repondre-general` (niveau 3), `moderer`, `resumer`, `ameliorer`, `nommer-groupes`.
L'ancienne route `questions-similaires` du contrat 5 est conservée telle quelle. La recherche réelle est faite par
forum-service, qui possède les discussions et leurs vecteurs.

Sécurité : tous les schémas sont en `extra="forbid"`, aucun identifiant d'utilisateur n'entre dans ces routes, tout
texte passe par le `Sanitizer` avant le LLM, et le contenu des utilisateurs est encadré par des balises de données.
Tous les appels au LLM passent par `app/core`.

---

## 6. Données (base `bawsla_forum`)

29 tables créées par JPA (`ddl-auto: update`). Aucune clé étrangère vers une autre base : seuls des identifiants
(`coursId`, `auteurId`…) sont stockés.

- **Forum :** discussion, version_discussion, reponse, commentaire, vote, reaction, etiquette (+ discussion_etiquette), piece_jointe
- **Engagement :** abonnement, favori, lecture, vue, notification, reputation, evenement_reputation, badge_obtenu
- **La Vigie :** reponse_ia, avis_vigie, passage_cours, passage_forum, index_cours, conversation_vigie, message_vigie, resume_fil
- **Modération :** signalement, sanction, journal_moderation (non modifiable), parametres_forum_cours, parametres_forum

La « base vectorielle » est stockée dans MySQL : chaque vecteur est un BLOB float32 normalisé, et le cosinus est
calculé en Java.

---

## 7. Configuration

| Variable (`.env`) | Rôle |
|---|---|
| `LLM_PROVIDER`, `LLM_API_KEY`, `LLM_MODEL` | fournisseur LLM du socle (par exemple `gemini` / `gemini-3.6-flash`). En `stub`, le forum fonctionne, mais La Vigie ne génère pas de vraie réponse. |
| `FORUM_PIECES_JOINTES_DOSSIER` | dossier des images jointes (par défaut `./data/pieces-jointes`, ignoré par git ; en Docker, `/home/bawsla/pieces-jointes` sur le volume `forum_pieces_jointes`) |
| `PROMPT_FORUM_VERSION` | version des prompts du forum (par défaut `v3`) |
| `FORUM_RAG_TEXTE_DEMO` | `true` : si le texte réel d'un cours est indisponible, le forum indexe `demo/cours-demo.md` (pratique en développement tant que le module Cours n'est pas fusionné). Défaut `false`. |

Réglages modifiables **sans redéployer**, dans l'espace admin (onglet Réglages) :

| Clé | Défaut |
|---|---|
| `similarite.seuil` / `similarite.seuil-doublon` | 0,725 / 0,75 (mesurés avec Gemini) |
| `vigie.seuil-pertinence` | 0,55 (provisoire) |
| `vigie.reponse-generale-active` | true |
| `moderation.seuil-masquage` / `moderation.seuil-signalement` | 0,85 / 0,5 |
| `flood.questions-par-heure` / `flood.messages-par-heure` | 5 / 30 |
| `vote.points-minimum-negatif` | 50 |
| `moderation.mots-interdits` | vide |

Les seuils de similarité ont été mesurés avec Gemini (évaluation du 2026-10-16). Le seuil de pertinence de La Vigie
reste provisoire : la partie RAG n'a pas pu être mesurée à cause du quota gratuit (voir
`ai-service/app/modules/forum/evaluation/resultats/ANALYSE.md`).

Sans clé LLM (`LLM_PROVIDER=stub`), le forum fonctionne quand même : les questions similaires passent par les
mots-clés, La Vigie s'abstient et la modération s'appuie sur les règles sans LLM.

---

## 8. Lancer et tester

```bash
# Toute la pile
docker compose up -d --build                 # http://localhost:4200

# forum-service seul (JDK 21)
cd backend && ./mvnw -pl forum-service -am spring-boot:run

# Tests
cd backend && ./mvnw -pl forum-service -am test          # 161 tests (MockMvc, H2, IA simulée)
cd ai-service && .venv/Scripts/python -m pytest tests/forum   # tests du forum, couverture de branches ≥ 95 %
cd frontend && npx ng build                              # sans avertissement
cd ai-service && .venv/Scripts/python tests/forum/e2e_forum.py   # boîte noire via la gateway (pile lancée)

# Évaluation (rapport)
cd ai-service && .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --dry-run   # sans clé
cd ai-service && .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --parties similarite,rag --sans-generation
cd ai-service && .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --versions v1,v2,v3   # ≈ 350 appels
```

Sous Windows (PowerShell), utiliser `.\mvnw.cmd` à la place de `./mvnw`. Maven demande le **JDK 21** dans `JAVA_HOME` (il échoue avec Java 17).

Hors `--dry-run`, le script d'évaluation annonce le nombre d'appels et demande confirmation (`--oui` pour s'en
passer). Il espace les appels, envoie les vecteurs par lots de 20 et attend 65 s sur une erreur 429 (5 essais).

Résultats à la date de la PR :
- **Java :** 161 tests verts.
- **Python :** 218 tests verts sur toute la suite de l'ai-service, dont 106 du forum (couverture de branches d'environ 97 %).
- **Front :** `ng build` sans avertissement.
- **Boîte noire :** 26 contrôles réussis, 0 échec. Elle vérifie aussi les accès refusés : 401 sans jeton, en-têtes `X-User-*` forgés ignorés, 403 croisés.

---

## 9. Scénario de démo (6 minutes)

1. **Apprenant :** « Poser une question » dans le cours Bases de données. Pendant la saisie, la question déjà
   résolue « Pourquoi mon LEFT JOIN renvoie autant de lignes qu'un INNER JOIN ? » apparaît, y compris quand la
   question est tapée en derja.
2. Il publie une nouvelle question. La Vigie répond avec des citations [1][2] et le passage du cours surligné ; un
   clic ouvre le cours.
3. **Question hors cours** (Kubernetes) : La Vigie répond « le cours ne traite pas ce point ». La réponse générale
   n'apparaît que sur demande, dans un encadré séparé marqué « non vérifiée ».
4. **Injection** (« ignore tes instructions… ») : le message est masqué automatiquement et arrive dans la file du
   formateur.
5. **Formateur :** il corrige une réponse de La Vigie, consulte les tendances, active le mode examen et transmet le
   cas à l'administration.
6. **Admin :** il sanctionne l'auteur (lecture seule 7 jours, avec un motif), ouvre la supervision de La Vigie puis
   le journal de modération.

---

## 10. Limites connues

- **Évaluation avec un vrai LLM :** la similarité est mesurée avec Gemini (Recall@5 = 1 en sémantique, MRR 0,654
  contre 0,441 en lexical) et la sécurité est mesurée sans LLM (20 injections sur 20 neutralisées, 12 données
  personnelles sur 12 masquées). La génération RAG et la modération v2 / v3 ne sont pas encore mesurées : le quota
  gratuit de Gemini est limité à 20 générations par jour. La marche à suivre est dans `ANALYSE.md`.
- **Texte des cours :** tant que `feature/cours` n'est pas dans `develop`, `/texte` ne renvoie pas le contenu réel.
  La Vigie s'appuie alors sur la base de connaissances du forum, ou sur le texte de démonstration si
  `FORUM_RAG_TEXTE_DEMO=true`.
- **Affichage :** le Markdown est rendu par un moteur maison qui échappe tout le HTML, au lieu des bibliothèques
  marked et DOMPurify. L'assistant « Demander au cours » est une page complète plutôt qu'un panneau latéral.
