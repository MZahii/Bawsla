# Module Cours (ai-service)

Routes sous `/api/ai/cours` : fiche de révision, explication orale, audio, résumé automatique local. Ce fichier décrit le
**résumé automatique local** (`local/`), qui n'utilise ni clé API ni LLM distant.

## Résumé automatique local

`POST /api/ai/cours/resume-local` : `{coursId, pages[{numero, texte}], longueur: courte|moyenne|longue, mode: auto|abstractif|extractif}`.

| Mode | Ce qui se passe |
|---|---|
| `extractif` | Les phrases clés du cours (TextRank), recopiées sans modification. Aucune dépendance lourde. |
| `abstractif` | Résumé reformulé par un modèle Hugging Face local (CPU). 503 si torch/transformers sont absents. |
| `auto` | Reformulé, puis contrôle de fidélité : sous `RESUME_LOCAL_SEUIL` (50) il est remplacé par l'extractif avec l'alerte « Résumé reformulé peu fidèle : remplacé par les phrases clés du cours ». Sans torch : extractif et une alerte. |

La langue du cours (fr ou en) est détectée sans réseau (mots vides) ; une autre langue donne une erreur 400. Le modèle suit la langue,
et l'audio du résumé utilise la chaîne vocale existante avec cette même langue.

### Installation (optionnelle)

```bash
cd ai-service
.venv/Scripts/python -m pip install --extra-index-url https://download.pytorch.org/whl/cpu -r app/modules/cours/requirements-local.txt
```

Le premier lancement télécharge le modèle (environ 1,2 Go pour l'anglais, quelques minutes). Ensuite il est dans le cache Hugging Face.

### Variables (`.env`)

| Variable | Défaut | Rôle |
|---|---|---|
| `RESUME_LOCAL_ACTIF` | `false` | Interrupteur. `false` : la route répond 503 « Résumé local indisponible sur ce serveur ». |
| `RESUME_LOCAL_MODELE_FR` | `moussaKam/barthez-orangesum-abstract` | Modèle français (Apache-2.0). |
| `RESUME_LOCAL_MODELE_EN` | `sshleifer/distilbart-cnn-12-6` | Modèle anglais (Apache-2.0). Alternative plus lourde : `facebook/bart-large-cnn` (MIT). |
| `RESUME_LOCAL_SEUIL` | `50` | Score de fidélité (0-100) sous lequel le reformulé est remplacé par l'extractif. |
| `RESUME_LOCAL_SEUIL_PHRASE` | `55` | Similarité minimale pour qu'une phrase du résumé soit « justifiée ». |
| `RESUME_LOCAL_LIBERER_APRES_SECONDES` | `600` | Un modèle inactif depuis ce délai est déchargé de la mémoire (`0` : jamais). |
| `RESUME_LOCAL_MORCEAUX_MAX` | `12` | Morceaux de cours résumés au plus (au-delà : alerte « cours tronqué »). |

### Mémoire nécessaire (mesurée sur CPU, Windows, torch 2.14)

Un seul modèle est chargé à la fois : changer de langue décharge l'autre.

| | RAM du processus ai-service |
|---|---|
| Sans le résumé local (torch non importé) | quelques centaines de Mo |
| Après `import torch` et `transformers` | environ 0,3 Go |
| Modèle français (BARThez, 139 M de paramètres) | environ 1,0 Go au pic |
| Modèle anglais (distilbart, 306 M de paramètres) | environ 1,7 Go au pic |
| `facebook/bart-large-cnn` (406 M) | prévoir environ 2,5 Go |

Prévoir 2 Go de RAM libre pour le modèle anglais par défaut. Après déchargement, Python ne rend pas toute la mémoire au système
(il reste environ 0,2 à 1,3 Go selon le modèle) : redémarrer le service la libère entièrement. Aucun GPU requis.

### Tests

```bash
cd ai-service
.venv/Scripts/python -m pytest -q                                  # faux modèles, rapide
RUN_SLOW=1 .venv/Scripts/python -m pytest -q -s tests/cours/test_resume_local.py -k reel   # vrais modèles, PDF de diaporama
```

Le test lent lit `~/Downloads/Chapter3_2023_2024.pdf` (ou la variable `RESUME_LOCAL_PDF_TEST`).
