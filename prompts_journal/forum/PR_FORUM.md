# Pull requests du module Forum (brouillons, à ouvrir par Malek)

Le plan (§13.2) interdit à l'agent d'ouvrir une pull request sans accord : ces textes sont prêts à copier.
`develop` n'a pas bougé depuis la dernière synchronisation de `feature/forum` : aucune fusion n'est nécessaire.

---

## PR 1 — `chore(socle)` : modifications des fichiers communs pour le forum

**Base** : `develop` · **Commits** : `9b71dad`, `3e44fbc`, `cae3971` (à extraire par `git cherry-pick` sur une
branche `chore/socle-forum` si l'équipe veut relire le socle à part ; sinon, ils sont inclus dans la PR 2).

Ce que touche cette PR, en ajouts uniquement, sans réorganiser les fichiers :

| Fichier | Modification | Pourquoi |
|---|---|---|
| `ai-service/app/core/llm_client.py` (+ `tests/core/test_embeddings.py`) | `LLMClient.embed()` : Gemini `gemini-embedding-001` (768 dimensions, RETRIEVAL_DOCUMENT / RETRIEVAL_QUERY, normalisé), OpenAI `text-embedding-3-small`, stub déterministe, routage avec secours | embeddings du cahier des charges ; tout appel à un fournisseur passe par le cœur |
| `CONTRATS_API.md` | contrat 5 : 9 routes IA du forum ; ligne des endpoints internes du forum | règle de l'équipe |
| `frontend/src/app/app.routes.ts` | 1 ligne : `/admin/forum` | espace admin du forum |
| `core/layouts/admin/admin-shell.ts` | 1 entrée de menu « Forum » | accès à l'espace admin |
| `core/layouts/parts/notif-menu.ts` | cloche branchée sur les notifications du forum (compteur toutes les 30 s) au lieu des données de démo | notifications visibles partout |
| `pages/student-home`, `pages/teacher-home` | `<app-forum-apercu>` à la place du bloc forum de démo | accueils sur de vraies données |

**Vérification** : `pytest` (suite complète) vert, `ng build` sans avertissement, `mvnw test` du forum vert.
Aucun changement de comportement pour les autres modules, hormis la cloche (elle affichait des données de démo).

---

## PR 2 — `feature/forum` → `develop` : module Forum et La Vigie

**Résumé** : le forum complet du cahier des charges et La Vigie, l'IA du forum, dans les trois espaces.

- **Web** : discussions par cours (questions, discussions, annonces), réponses, commentaires, votes, réactions,
  meilleure réponse, approbation du formateur, anonymat, historique des versions, pièces jointes, étiquettes,
  suivis, favoris, lu / non lu, notifications, réputation (5 niveaux), 10 badges, classement, recherche.
- **IA (NLP)** : embeddings + similarité cosinus (questions similaires en direct, doublons, discussions liées),
  recherche hybride RRF, reformulation derja / arabizi / anglais.
- **IA (RAG)** : cours découpé et indexé, réponse **uniquement** depuis le cours (et les réponses validées du forum),
  passage cité et vérifié sans LLM, confiance calculée, abstention « le cours ne traite pas ce point » au lieu
  d'inventer ; réponse générale hors cours seulement sur demande, dans un encadré séparé ; mode examen ; assistant
  privé « Demander au cours ».
- **Sécurité** : Sanitizer et balises de données neutralisées, règles sans LLM puis modération par LLM, masquage
  automatique, file de modération, signalements, escalade, sanctions, journal non modifiable avec export CSV.
- **Pilotage** : studio formateur (6 onglets), espace admin (6 sections), tendances (K-Means + silhouette),
  supervision de La Vigie.
- **Prompts** : `repondre`, `guider`, `repondre_general`, `reformuler`, `moderer` en v1 → v3 ; `resumer`,
  `ameliorer`, `nommer_groupes` en v1 → v2.

**Tests** : `mvnw test` 141 verts (forum-service) ; `pytest` forum 101 verts, couverture de branches 97 % ;
`ng build` sans avertissement ; test boîte noire `ai-service/tests/forum/e2e_forum.py` via la gateway.
**Évaluation** : `ai-service/app/modules/forum/evaluation/` (jeux, script, résultats, analyse des seuils).
**Journal** : `prompts_journal/forum/JOURNAL.md` (entrées 01 à 10) et `captures/`.

**Points d'attention pour la relecture**
- Le forum lit `GET /api/cours/{id}`, `/texte`, `/chapitres`, `GET /api/users/{id}`, `GET /api/quiz/notions-ratees`
  et fonctionne si l'une ne répond pas (dégradation propre). Aucune route à implémenter chez un coéquipier.
- En développement, `FORUM_RAG_TEXTE_DEMO=true` (dans `.env`) indexe un texte de démonstration tant que le texte
  réel des cours n'est pas disponible.
- Seuils sémantiques provisoires (0,75 / 0,9 / 0,55) à confirmer avec la clé (voir `evaluation/resultats/ANALYSE.md`).
