# Évaluation du module Forum (La Vigie)

- **Mode** : réel (gemini : `gemini-3.6-flash`, offre gratuite)
- **Modèle d'embeddings** : gemini-embedding-001 (768 dimensions)
- **Date** : 2026-10-16 (deux exécutions : 04:09 et 04:20)
- **Commandes** : `--oui --parties similarite,rag --sans-generation`, puis `--oui --versions v1,v2,v3 --parties securite,moderation`
- **Relances après un quota atteint (429)** : 12 (5 puis 7)

> **Parties interrompues par les quotas de l'offre gratuite** (détail dans `ANALYSE.md`) :
> - **rag** (récupération et génération) : quota journalier de vectorisation épuisé (1 000 textes par jour).
>   Aucune mesure réelle ; les chiffres du dry-run restent dans `dry-run/`.
> - **moderation** : quota journalier de génération épuisé (20 requêtes par jour). Partiel : v1 sur 34 messages
>   (16 tranchés par les règles, 18 par le LLM), v2 et v3 sur les 16 messages tranchés par les règles seulement.

## 1. Questions similaires

Seuil « similaire » (meilleur F1 du cosinus) : **0.725** · seuil « doublon » (précision ≥ 0.95) : **0.725** · seuil lexical (meilleur F1) : 0.15 · reformulation : non

| Méthode | Groupe | Paires | Recall@1 | Recall@5 | MRR |
|---|---|---|---|---|---|
| lexical | ar | 15 | 0.333 | 0.8 | 0.517 |
| lexical | en | 20 | 0.25 | 0.65 | 0.435 |
| lexical | fr | 25 | 0.24 | 0.68 | 0.4 |
| lexical | tous | 60 | 0.267 | 0.7 | 0.441 |
| semantique | ar | 15 | 0.667 | 1 | 0.817 |
| semantique | en | 20 | 0.35 | 1 | 0.625 |
| semantique | fr | 25 | 0.28 | 1 | 0.58 |
| semantique | tous | 60 | 0.4 | 1 | 0.654 |
| hybride | ar | 15 | 0.4 | 1 | 0.613 |
| hybride | en | 20 | 0.25 | 0.95 | 0.538 |
| hybride | fr | 25 | 0.36 | 0.88 | 0.578 |
| hybride | tous | 60 | 0.333 | 0.933 | 0.574 |

| Seuil cosinus | Précision | Rappel | F1 |
|---|---|---|---|
| 0.3 | 0.5 | 1 | 0.667 |
| 0.35 | 0.5 | 1 | 0.667 |
| 0.4 | 0.5 | 1 | 0.667 |
| 0.45 | 0.5 | 1 | 0.667 |
| 0.5 | 0.5 | 1 | 0.667 |
| 0.55 | 0.522 | 1 | 0.686 |
| 0.6 | 0.606 | 1 | 0.755 |
| 0.65 | 0.723 | 1 | 0.839 |
| 0.7 | 0.879 | 0.967 | 0.921 |
| 0.75 | 1 | 0.783 | 0.879 |
| 0.8 | 1 | 0.4 | 0.571 |
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

## 3. Sécurité (sans LLM)

- Injections neutralisées (règles ou Sanitizer) : **20 / 20** (taux 1 ; règles seules : 1)
- Données personnelles masquées avant le LLM : **12 / 12** éléments (taux 1) dans 10 messages ; signalées à l'auteur par les règles : 10 / 10 messages

## 4. Modération

| Version | Messages | Erreurs | Exactitude | F1 macro |
|---|---|---|---|---|
| regles | 60 | 0 | 0.517 | 0.476 |
| v1 | 34 | 26 | 1 | 0.857 |
| v2 | 16 | 44 | 1 | 0.571 |
| v3 | 16 | 44 | 1 | 0.571 |

Matrice de confusion (v3, lignes = attendu, colonnes = prédit) :

| | OK | HORS_S | IRRESP | SPAM | FUITE_ | INJECT | DONNEE |
|---|---|---|---|---|---|---|---|
| OK | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| HORS_SUJET | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| IRRESPECTUEUX | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| SPAM | 0 | 0 | 0 | 4 | 0 | 0 | 0 |
| FUITE_EXAMEN | 0 | 0 | 0 | 0 | 3 | 0 | 0 |
| INJECTION | 0 | 0 | 0 | 0 | 0 | 4 | 0 |
| DONNEES_PERSONNELLES | 0 | 0 | 0 | 0 | 0 | 0 | 5 |

