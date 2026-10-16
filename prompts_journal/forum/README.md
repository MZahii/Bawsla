# Module Forum et La Vigie

Forum par cours de Bawsla et **La Vigie**, l'IA du forum : questions similaires, réponse sourcée depuis le cours
(RAG), modération, résumé, aide à la rédaction, tendances. Propriétaire : Ouji Malek (`feature/forum`).

## Où est le code

| Couche | Dossier | Contenu |
|---|---|---|
| Back | `backend/forum-service/` (port 8084, base `bawsla_forum`) | 10 contrôleurs sous `/api/forum/**`, clients Feign `CoursClient`, `UserClient`, `QuizClient`, `IaClient` |
| IA | `ai-service/app/modules/forum/` | `router.py` (`/api/ai/forum/**`, 9 routes, contrat 5 de `CONTRATS_API.md`), `nlp.py`, `rag.py`, `moderation.py`, `aides.py`, `prompts/` (versions v1 → v3) |
| Front | `frontend/src/app/features/forum/` | 8 pages : liste, nouvelle question, fil, assistant, activité, notifications, studio formateur, admin |
| Évaluation | `ai-service/app/modules/forum/evaluation/` | jeux de données, `evaluer_forum.py`, `resultats/` (réel) et `resultats/dry-run/` |
| Journal | `prompts_journal/forum/` | `JOURNAL.md` (entrées 01 à 11), `PLAN_FORUM.md`, `PR_FORUM.md`, `captures/` |

## Lancer

```bash
docker compose up -d --build        # toute la pile → http://localhost:4200, forum dans chaque cours
```

Réglages propres au forum (dans `.env`, voir `.env.example`) :

| Variable | Défaut | Rôle |
|---|---|---|
| `FORUM_RAG_TEXTE_DEMO` | `false` | `true` indexe un texte de cours de démonstration pour La Vigie tant que le texte réel des cours manque |
| `FORUM_PIECES_JOINTES_DOSSIER` | `./data/pieces-jointes` | dossier des pièces jointes ; en Docker, `/home/bawsla/pieces-jointes` sur le volume `forum_pieces_jointes` (conservé quand le conteneur est recréé) |

Sans clé LLM (`LLM_PROVIDER=stub`), le forum fonctionne : les similaires passent en mots-clés, La Vigie s'abstient,
la modération s'appuie sur les règles sans LLM.

Les seuils se règlent dans l'espace admin (Admin → Forum → Réglages), sans redéployer. Défauts mesurés
(`evaluation/resultats/ANALYSE.md`) : `similarite.seuil` 0,725, `similarite.seuil-doublon` 0,75,
`vigie.seuil-pertinence` 0,55 (encore provisoire : partie RAG non mesurée, quota).

## Tester

```bash
cd backend/forum-service && ../mvnw test                     # API du forum (H2, IA simulée)
cd ai-service && .venv/Scripts/python -m pytest              # dont tests/forum/
cd frontend && npx ng build
cd ai-service && .venv/Scripts/python tests/forum/e2e_forum.py  # boîte noire via la gateway (pile lancée)
```

## Évaluer La Vigie

```bash
cd ai-service
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --dry-run            # sans clé, chaîne de calcul
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --parties similarite,rag --sans-generation
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --versions v1,v2,v3
```

Sans `--dry-run`, le script annonce le nombre d'appels et demande confirmation (`--oui` pour s'en passer). Il
espace les appels (`--pause`, 4 s), envoie les vecteurs par lots de 20 et attend 65 s sur un 429 (5 essais) ; une
partie dont le quota est épuisé est notée « interrompue » dans `RESULTATS.md`, les autres continuent.

Résultat réel du 2026-10-16 (offre gratuite Gemini) : similarité mesurée (sémantique Recall@5 = 1, MRR 0,654
contre 0,441 en lexical), sécurité 20 / 20 et 12 / 12 ; RAG et modération v2 / v3 non mesurés faute de quota
(20 générations par jour). Détail et marche à suivre : `ai-service/app/modules/forum/evaluation/resultats/ANALYSE.md`.

## Sécurité

Tout texte passe par le `Sanitizer` du cœur avant le LLM ; aucun nom, email ni identifiant n'entre dans un prompt.
Les règles sans LLM (injection, fuite d'examen, données personnelles, spam) tranchent avant la modération par LLM.
L'autorisation est vérifiée dans forum-service (`CurrentUser.requireAnyRole`, `requireOwnerOrAdmin`).
