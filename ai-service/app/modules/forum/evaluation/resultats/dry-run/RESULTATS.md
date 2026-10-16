# Évaluation du module Forum (La Vigie)

- **Mode** : dry-run (faux LLM, vecteurs du stub)
- **Modèle d'embeddings** : stub-hachage-768
- **Date** : 2026-10-16 00:10

> **Attention : exécution `--dry-run`.** Vecteurs du stub (hachage de mots) et LLM factice : les mesures sémantiques et de génération vérifient la chaîne de calcul, elles NE mesurent PAS un vrai modèle. Les mesures lexicales, de récupération lexicale, de sécurité et des règles de modération sont réelles. Relancer sans `--dry-run` (clé dans le .env) pour fixer les seuils.

## 1. Questions similaires

Seuil « similaire » (meilleur F1 du cosinus) : **0.3** · seuil « doublon » (précision ≥ 0.95) : **–** · seuil lexical (meilleur F1) : 0.15 · reformulation : non

| Méthode | Groupe | Paires | Recall@1 | Recall@5 | MRR |
|---|---|---|---|---|---|
| lexical | ar | 15 | 0.333 | 0.8 | 0.517 |
| lexical | en | 20 | 0.25 | 0.65 | 0.435 |
| lexical | fr | 25 | 0.24 | 0.68 | 0.4 |
| lexical | tous | 60 | 0.267 | 0.7 | 0.441 |
| semantique | ar | 15 | 0.2 | 0.467 | 0.362 |
| semantique | en | 20 | 0.2 | 0.55 | 0.354 |
| semantique | fr | 25 | 0.16 | 0.68 | 0.368 |
| semantique | tous | 60 | 0.183 | 0.583 | 0.362 |
| hybride | ar | 15 | 0.2 | 0.867 | 0.454 |
| hybride | en | 20 | 0.25 | 0.7 | 0.443 |
| hybride | fr | 25 | 0.2 | 0.8 | 0.423 |
| hybride | tous | 60 | 0.217 | 0.783 | 0.438 |

| Seuil cosinus | Précision | Rappel | F1 |
|---|---|---|---|
| 0.3 | 0.714 | 0.5 | 0.588 |
| 0.35 | 0.667 | 0.3 | 0.414 |
| 0.4 | 0.647 | 0.183 | 0.286 |
| 0.45 | 0.7 | 0.117 | 0.2 |
| 0.5 | 0.571 | 0.067 | 0.119 |
| 0.55 | 0.8 | 0.067 | 0.123 |
| 0.6 | 0.667 | 0.033 | 0.063 |
| 0.65 | 0 | 0 | 0 |
| 0.7 | 0 | 0 | 0 |
| 0.75 | 0 | 0 | 0 |
| 0.8 | 0 | 0 | 0 |
| 0.85 | 0 | 0 | 0 |
| 0.9 | 0 | 0 | 0 |
| 0.95 | 0 | 0 | 0 |

| Seuil lexical (repli sans vecteurs) | Précision | Rappel | F1 |
|---|---|---|---|
| 0.1 | 0.73 | 0.767 | 0.748 |
| 0.2 | 0.737 | 0.7 | 0.718 |
| 0.3 | 0.81 | 0.567 | 0.667 |
| 0.4 | 0.9 | 0.45 | 0.6 |
| 0.5 | 0.92 | 0.383 | 0.541 |
| 0.6 | 0.778 | 0.117 | 0.203 |
| 0.7 | 1 | 0.067 | 0.125 |
| 0.8 | 1 | 0.017 | 0.033 |
| 0.9 | 1 | 0.017 | 0.033 |

## 2. RAG

Récupération (questions dans le cours) : Recall@1 **0.925**, Recall@4 **1**, MRR **0.956**. Seuil de pertinence retenu : **0.325** (exactitude « répondre / s'abstenir » : 0.764). Seuil sûr (aucune question hors cours ne passe) : **0.425**, qui sert encore 0.35 des questions du cours.

| Version | Questions | Réponses justes | Bonnes abstentions | Inventées hors cours | Fidélité des citations | Confiance (justes) | Confiance (fausses) | Erreurs |
|---|---|---|---|---|---|---|---|---|
| v1 | 55 | 0.625 | 0.333 | 0.667 | 0.673 | 69.2 | 65.3 | 0 |
| v2 | 55 | 0.9 | 0.667 | 0.333 | 1 | 58.7 | 54.6 | 0 |
| v3 | 55 | 0.9 | 0.667 | 0.333 | 1 | 69.3 | 64.6 | 0 |

## 3. Sécurité (sans LLM)

- Injections neutralisées (règles ou Sanitizer) : **20 / 20** (taux 1 ; règles seules : 1)
- Données personnelles masquées avant le LLM : **12 / 12** éléments (taux 1) dans 10 messages ; signalées à l'auteur par les règles : 10 / 10 messages

## 4. Modération

| Version | Exactitude | F1 macro |
|---|---|---|
| regles | 0.517 | 0.476 |
| v1 | 0.717 | 0.685 |
| v2 | 0.933 | 0.934 |
| v3 | 0.967 | 0.97 |

Matrice de confusion (v3, lignes = attendu, colonnes = prédit) :

| | OK | HORS_S | IRRESP | SPAM | FUITE_ | INJECT | DONNEE |
|---|---|---|---|---|---|---|---|
| OK | 14 | 0 | 0 | 0 | 1 | 0 | 0 |
| HORS_SUJET | 0 | 8 | 0 | 0 | 0 | 0 | 0 |
| IRRESPECTUEUX | 0 | 0 | 8 | 0 | 0 | 0 | 0 |
| SPAM | 0 | 0 | 0 | 8 | 0 | 0 | 0 |
| FUITE_EXAMEN | 0 | 0 | 0 | 0 | 7 | 0 | 0 |
| INJECTION | 1 | 0 | 0 | 0 | 0 | 6 | 0 |
| DONNEES_PERSONNELLES | 0 | 0 | 0 | 0 | 0 | 0 | 7 |

