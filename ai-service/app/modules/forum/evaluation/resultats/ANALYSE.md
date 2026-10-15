# Analyse de l'évaluation du forum et choix des seuils

Données : `../paires_questions.jsonl` (60 paires de même sens dont 20 avec l'anglais et 15 avec l'arabe, la derja ou
l'arabizi, plus 60 paires de sens différent souvent sur le même sujet), `../rag_questions.jsonl` (40 questions sur
3 cours de test et 15 questions hors cours), `../injections.jsonl` (20 injections, 10 messages avec données
personnelles), `../moderation.jsonl` (60 messages étiquetés). Chiffres détaillés : `RESULTATS.md` et les CSV.

## Ce qui est mesuré pour de vrai, et ce qui ne l'est pas encore

L'exécution commise est un `--dry-run` : aucune clé d'API n'est disponible sur le poste de développement. Les
vecteurs viennent donc du stub (hachage de mots) et le LLM est simulé.

| Mesure | Valeur | Fiable ? |
|---|---|---|
| Injections neutralisées avant le LLM (règles ou Sanitizer) | 20 / 20 | **oui** : règles déterministes |
| Données personnelles masquées avant le LLM | 10 / 10 messages, 12 / 12 éléments | **oui** : Sanitizer du socle |
| Données personnelles signalées à l'auteur (règles) | 10 / 10 | **oui** |
| Modération par les seules règles : exactitude | 0,517 | **oui** : ce que tranchent les règles sans LLM |
| Recherche de questions similaires, mode lexical (repli) | Recall@1 0,267, Recall@5 0,7 | **oui** |
| Seuil lexical de repli | voir ci-dessous | **oui** |
| Cosinus, recherche sémantique et hybride | – | **non** : vecteurs du stub, sans valeur de sens |
| Génération (v1 / v2 / v3), fidélité, confiance | – | **non** : LLM factice, vérifie seulement la chaîne |

## Seuils retenus

| Réglage (`parametres_forum`) | Valeur | Justification |
|---|---|---|
| Repli lexical des similaires (code de `RechercheService`) | **0,5** | Mesuré : précision 0,92, rappel 0,38. Quand les vecteurs manquent, mieux vaut peu de suggestions mais justes (un apprenant qui clique « Ça répond à ma question » sur une mauvaise suggestion ne publie pas sa vraie question). Le meilleur F1 (0,15) aurait une précision de 0,74 seulement. |
| `similarite.seuil` | **0,75**, provisoire | Règle de choix : meilleur F1 du cosinus sur les 120 paires. À recalculer avec le vrai modèle (`gemini-embedding-001`) ; le dry-run ne peut pas le fixer. |
| `similarite.seuil-doublon` | **0,9**, provisoire | Règle de choix : plus petit seuil dont la précision atteint 0,95, car la boîte « doublon probable » interrompt la publication et doit presque toujours avoir raison. |
| `vigie.seuil-pertinence` | **0,55**, provisoire | Deux règles calculées par le script : exactitude maximale « répondre / s'abstenir », et **seuil sûr**, le plus petit seuil où aucune des 15 questions hors cours ne passe. Le plan vise 0 réponse inventée : on retiendra le seuil sûr s'il sert encore au moins 80 % des questions du cours ; sinon la meilleure exactitude, la deuxième barrière (le modèle peut répondre `horsCours`, et une réponse sans citation vérifiée est rejetée) couvrant le reste. |
| `moderation.seuil-masquage` / `seuil-signalement` | **0,85 / 0,5** (plan) | Les règles sans LLM donnent 0,9 à 0,95 aux cas évidents (injection, fuite d'examen) : ils sont masqués ; les données personnelles (0,7) sont signalées, pas masquées, et l'auteur est prévenu avant de publier. |

## Pour fixer les seuils provisoires

```bash
cd ai-service
# .env : LLM_PROVIDER=gemini et la clé (jamais commitée)
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --parties similarite,rag --sans-generation
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --versions v1,v2,v3      # génération et modération
```

Le premier appel ne coûte que des vectorisations (quelques requêtes groupées) ; le second annonce le nombre d'appels
(environ 350) et demande confirmation. Les nouveaux seuils se règlent ensuite dans l'espace admin (Réglages), sans
redéployer.
