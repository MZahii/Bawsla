# Analyse de l'évaluation du forum et choix des seuils

Données : `../paires_questions.jsonl` (60 paires de même sens dont 20 avec l'anglais et 15 avec l'arabe, la derja ou
l'arabizi, plus 60 paires de sens différent souvent sur le même sujet), `../rag_questions.jsonl` (40 questions sur
3 cours de test et 15 questions hors cours), `../injections.jsonl` (20 injections, 10 messages avec données
personnelles), `../moderation.jsonl` (60 messages étiquetés). Chiffres détaillés : `RESULTATS.md` et les CSV.
L'ancienne exécution `--dry-run` (faux LLM, vecteurs du stub) est conservée dans `dry-run/`.

## Ce qui a été mesuré pour de vrai (2026-10-16)

Clé Gemini du poste, **offre gratuite** : `gemini-embedding-001` (768 dimensions) et `gemini-3.6-flash`, sans
fournisseur de secours. Le script attend 65 s et réessaie (5 fois au plus) sur un 429, avec une pause entre deux
appels ; un quota épuisé pour la journée interrompt la partie concernée, qui garde ce qui a tourné.

| Mesure | Valeur | Statut |
|---|---|---|
| Questions similaires : cosinus, recherche sémantique, lexicale et hybride | voir ci-dessous | **mesuré** (120 paires) |
| Injections neutralisées avant le LLM (règles ou Sanitizer) | 20 / 20 | **mesuré** (sans LLM) |
| Données personnelles masquées avant le LLM | 12 / 12 éléments dans 10 messages | **mesuré** (sans LLM) |
| Données personnelles signalées à l'auteur (règles) | 10 / 10 | **mesuré** (sans LLM) |
| Modération par les seules règles : exactitude | 0,517 (31 / 60) | **mesuré** |
| Modération par le LLM, prompt v1 | 34 / 34 justes (16 tranchés par les règles, 18 par le LLM : 15 OK, 3 hors sujet) | **partiel** : quota |
| Modération par le LLM, prompts v2 et v3 | 16 / 16 (règles seules, aucun appel LLM abouti) | **non mesuré** : quota |
| RAG : récupération (Recall@1/@4, MRR), seuil de pertinence | – | **non mesuré** : quota |
| RAG : génération v1 / v2 / v3, fidélité, abstentions, confiance | – | **non mesuré** : quota |

**Quotas rencontrés** (messages de l'API, clé masquée) :
- vectorisation : `EmbedContentRequestsPerDayPerUserPerProjectPerModel-FreeTier`, **1 000 textes par jour**. Un lot
  de 100 textes compte pour 100. Les premiers essais, par lots de 100 (taille du cœur), ont été refusés pour la
  limite par minute et ont tout de même consommé le quota du jour. Le script envoie maintenant des lots de 20 avec
  15 s de pause ; la partie « similarité » (240 textes) est passée ainsi, puis la partie RAG a buté sur le quota du jour.
- génération : `GenerateRequestsPerDayPerProjectPerModel-FreeTier`, **20 requêtes par jour**. L'évaluation
  complète de la génération et de la modération demande environ 345 appels : hors de portée de l'offre gratuite
  (plus de deux semaines de quota).

## Résultats réels : questions similaires

| Méthode | Recall@1 | Recall@5 | MRR |
|---|---|---|---|
| lexicale (FULLTEXT simulé, mots significatifs) | 0,267 | 0,70 | 0,441 |
| **sémantique** (cosinus, `gemini-embedding-001`) | **0,400** | **1,00** | **0,654** |
| hybride (RRF, k = 60) | 0,333 | 0,933 | 0,574 |

- Le sémantique trouve la bonne question dans les 5 premières **à chaque fois** (60 / 60), le lexical 42 / 60.
  Le gain est le plus net en arabe / derja / arabizi (Recall@1 0,667 contre 0,333) : ce sont les paires sans mot commun.
- L'hybride fait **moins bien** que le sémantique seul sur ce jeu : la RRF remonte des questions qui partagent des mots
  mais pas le sens (les paires pièges du jeu). C'est pourquoi les **questions similaires** (boîte « déjà posée », doublons)
  restent purement sémantiques, comme dans le code ; l'hybride ne sert qu'au classement de la recherche de la liste, où
  l'apprenant tape des mots-clés. Pas de changement de code.
- Distribution du cosinus : les paires de même sens montent jusqu'à 0,83 ; **aucune paire ne dépasse 0,85**.

## Seuils retenus

| Réglage (`parametres_forum`) | Avant | Après | Justification |
|---|---|---|---|
| `similarite.seuil` | 0,75 (provisoire) | **0,725** | Règle annoncée : meilleur F1 du cosinus sur les 120 paires. À 0,725 : précision 0,965, rappel 0,917, F1 0,940 (à 0,75 : précision 1, rappel 0,783, F1 0,879). |
| `similarite.seuil-doublon` | 0,9 (provisoire) | **0,75** | L'ancien 0,9 n'était **jamais atteint** (aucun cosinus au-dessus de 0,85) : la boîte « doublon probable » ne se serait jamais affichée. La règle « plus petit seuil de précision ≥ 0,95 » donne 0,725, la même valeur que le seuil « similaire » : toute suggestion interromprait alors la publication. On retient donc 0,75, le premier seuil **sans aucun faux doublon** (précision 1, rappel 0,783), car cette boîte bloque la publication et doit avoir raison. |
| Repli lexical des similaires (code de `RechercheService`) | 0,5 | **0,5** (confirmé) | Mesure lexicale identique au dry-run (elle ne dépend pas du modèle) : précision 0,92, rappel 0,38. Le meilleur F1 (0,1) n'aurait qu'une précision de 0,73. |
| `vigie.seuil-pertinence` | 0,55 (provisoire) | **0,55, toujours provisoire** | Non mesuré : la partie RAG n'a pas obtenu ses vecteurs (quota). Le cosinus question / passage (`RETRIEVAL_QUERY` contre `RETRIEVAL_DOCUMENT`) n'a pas la même distribution que le cosinus question / question : on ne transpose pas le résultat ci-dessus. |
| `moderation.seuil-masquage` / `seuil-signalement` | 0,85 / 0,5 (plan) | **inchangés** | Les règles sans LLM donnent 0,9 à 0,95 aux cas évidents (injection, fuite d'examen) et 0,85 au spam : ils sont masqués. Pas assez de mesures LLM pour bouger ces seuils. |

Les nouvelles valeurs sont les **défauts** de `ParametresService` ; une plateforme déjà lancée garde les valeurs
enregistrées dans l'espace admin (Réglages), qui se modifient sans redéployer.

## Défauts trouvés pendant cette évaluation, et corrigés

1. **Script d'évaluation, modération** : quand le LLM était indisponible, `moderer()` laisse passer le message
   (« OK », règle `repli`, comportement voulu en production) et le script le comptait comme une prédiction juste.
   Il est maintenant écarté des mesures et compté dans la colonne « erreurs ».
2. **Règle « texte très répétitif »** (trouvée par l'e2e) : la répétition était mesurée sur titre + contenu ensemble.
   Une question dont le contenu reformule le titre (« Pourquoi mon LEFT JOIN… ? » / « Mon LEFT JOIN renvoie… ») passait
   sous 50 % de mots distincts et était **masquée comme spam**. La règle mesure maintenant le titre et le contenu
   séparément. Les mesures des règles sur le jeu ne changent pas (0,517 ; 20 / 20 ; 12 / 12).
3. **Questions sans vecteur invisibles** (trouvé par l'e2e, quota épuisé) : une discussion dont la vectorisation a
   échoué à la publication n'apparaissait plus jamais dans les questions similaires dès que la requête, elle, était
   vectorisée. Elle passe maintenant par le repli lexical (seuil 0,5), sans pouvoir être déclarée « doublon ».

## Pour terminer la mesure (RAG, génération, modération v2 / v3)

Il faut un quota de génération supérieur à 20 requêtes par jour : activer la facturation du projet Google AI de la
clé (offre payante), ou renseigner une clé de secours (`LLM_SECOURS=openai` ou `groq`). Estimation de la commande
complète : environ 357 appels, environ 0,45 M jetons en entrée et 0,07 M en sortie, de l'ordre de 0,30 à 1 $ au
tarif payant. Puis, quota de vectorisation rétabli (le lendemain) :

```bash
cd ai-service
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --parties similarite,rag --sans-generation
.venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --versions v1,v2,v3      # génération et modération
```

La deuxième commande annonce le nombre d'appels et demande confirmation (`--oui` pour s'en passer), puis réécrit
`RESULTATS.md` et les CSV. Le seuil sûr de `vigie.seuil-pertinence` et le choix de la version de prompt se lisent
alors dans `RESULTATS.md` (section 2).
