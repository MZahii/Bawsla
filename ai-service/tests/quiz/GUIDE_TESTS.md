# Guide : refaire les tests du module Quiz (ai-service)

Ce guide permet de relancer soi-même les tests **boîte blanche** (pytest + couverture de branches) et
**boîte noire** (requêtes HTTP sur le vrai serveur), puis de tester à la main dans Swagger.
Aucune clé API n'est nécessaire : le LLM est soit simulé (tests), soit absent (mode `stub`).

Commandes à taper dans Git Bash. Commence par `cd ai-service` (depuis la racine du dépôt) : toutes les
commandes suivantes se lancent depuis ce dossier.

## 0. Préparer l'environnement (une seule fois)

```bash
cd ai-service
python -m venv .venv                         # si .venv n'existe pas encore
.venv/Scripts/python -m pip install -r requirements.txt
.venv/Scripts/python -m pip install -r requirements-dev.txt pytest-cov==7.0.0
```

`requirements-dev.txt` apporte pytest et pytest-asyncio (indispensable : sans lui, les tests `async`
échouent), `pytest-cov` mesure la couverture.

> Sous PowerShell, remplace `.venv/Scripts/python` par `.venv\Scripts\python`.
> Les accents s'affichent mieux si tu ajoutes `PYTHONIOENCODING=utf-8` devant la commande (Git Bash).

## 1. Tests boîte blanche (structure du code)

### 1.1 Tous les tests du module

```bash
.venv/Scripts/python -m pytest tests/quiz -q
```

Attendu : `130 passed` (dont `test_quiz_difficulte.py` : les 5 niveaux de difficulté). Pour tout ai-service : `.venv/Scripts/python -m pytest tests -q` (228 passed).

### 1.2 Couverture des instructions ET des branches

```bash
.venv/Scripts/python -m pytest tests/quiz -q \
  --cov=app/modules/quiz --cov-branch --cov-report=term-missing \
  --cov-report=html:tests/quiz/boite_noire/resultats/couverture
```

Attendu dans le terminal : `TOTAL 475 0 120 0 100%`.

Lecture des colonnes :

| Colonne | Sens |
|---|---|
| `Stmts` / `Miss` | instructions / instructions jamais exécutées |
| `Branch` / `BrPart` | branches (chaque `if` a 2 issues) / branches dont une issue n'a jamais été prise |
| `Missing` | lignes manquantes ; `62->64` = « de la ligne 62, le saut vers 64 n'a jamais eu lieu » |

Puis ouvre `tests/quiz/boite_noire/resultats/couverture/index.html` dans le navigateur : clique sur un
fichier pour voir chaque ligne en vert (exécutée), rouge (jamais exécutée) ou jaune (branche partielle).

### 1.3 Les tests boîte blanche ciblés

Fichier : `tests/quiz/test_quiz_boite_blanche.py`. Chaque test vise **une branche** que les tests
fonctionnels ne parcouraient pas (avant : 98 %, après : 100 %). La docstring de chaque test rappelle la
fonction et la condition visées. Pour ne lancer que ceux-là :

```bash
.venv/Scripts/python -m pytest tests/quiz/test_quiz_boite_blanche.py -v
```

**Méthode pour en écrire un nouveau** : lance la couverture (1.2), repère une ligne `Missing`, lis la
condition dans le code, construis l'entrée qui force l'issue jamais prise, et vérifie que la ligne passe
au vert.

**Défaut réel trouvé ainsi** : dans `coherence._divergence`, une réponse de vérification vide (`""`)
était acceptée comme la lettre « A », parce qu'en Python `"" in "ABCD"` vaut `True`. Le test
`test_divergence_lettre[-illisible]` échouait. Corrigé par `if not lettre or lettre not in LETTRES`.

## 2. Tests boîte noire (comportement vu de l'extérieur)

On ne regarde pas le code : on envoie des requêtes HTTP au **vrai serveur** (`app.main:app`, mêmes
routes, validations et Sanitizer) et on compare le statut et le contenu à l'attendu.

### 2.1 Tout lancer en une commande

```bash
PYTHONIOENCODING=utf-8 .venv/Scripts/python -m tests.quiz.boite_noire.scenarios
```

Le script démarre lui-même deux serveurs (LLM simulé sur le port 8765, sans LLM sur 8766), joue les
**38 cas**, puis les arrête. Attendu : `38/38 cas réussis.` (code de sortie 1 si un cas échoue).

Résultats dans `tests/quiz/boite_noire/resultats/` (non versionnés) :

| Fichier | Contenu |
|---|---|
| `rapport.html` | tableau des 38 cas : technique, requête, statut attendu / obtenu, durée, verdict |
| `generation.html` | le quiz de BN-02 : 2 questions VALIDÉES, 2 REJETÉES avec la raison |
| `remediation.html` | BN-26 : ce que voit l'apprenant, et le raisonnement réservé au formateur (BN-25 vérifie qu'un apprenant ne le reçoit pas) |
| `resultats.json` | réponses brutes de chaque cas |

### 2.2 Les techniques utilisées

| Technique | Exemples de cas |
|---|---|
| Nominal | BN-02 génération à 4 questions, BN-25 remédiation complète |
| Table de décision (authentification × rôle) | formateur et admin acceptés, apprenant refusé (403), sans en-têtes (401) |
| Valeurs limites | `nbQuestions` 0 / 1 / 10 / 11, 0 / 8 / 9 compétences, thème de 1 et 201 caractères, notion de 150 / 151 |
| Partitions invalides | Bloom ou difficulté inconnus, JSON illisible, `X-User-Id` non numérique, mauvaise réponse = bonne réponse |
| Confidentialité | email et téléphone masqués, champs `etudiantId` / `email` refusés (400) |
| Sécurité | injection de consigne, balise `</reponse_apprenant>` glissée dans une réponse |
| Erreurs | quota épuisé → 429 propre, aucun fournisseur → 503 propre |

### 2.3 Viser un serveur déjà lancé

```bash
# terminal 1
.venv/Scripts/python -m tests.quiz.boite_noire.serveur_demo
# terminal 2
PYTHONIOENCODING=utf-8 .venv/Scripts/python -m tests.quiz.boite_noire.scenarios --url http://127.0.0.1:8000
```

Avec `--url`, les deux cas « sans LLM » (BN-24, BN-38) sont sautés.

## 3. Tests manuels dans Swagger

1. Lance le serveur de démo (LLM simulé, aucune clé) :
   ```bash
   .venv/Scripts/python -m tests.quiz.boite_noire.serveur_demo
   ```
2. Ouvre <http://127.0.0.1:8000/docs> pour voir les routes et les schémas (`GenererQuizRequest`,
   `RemedierRequest`…), avec **Try it out** pour envoyer une requête.
3. Dans **Try it out**, Swagger affiche deux champs d'en-tête, `x-user-id` et `x-user-role` (en
   production, c'est la gateway qui les remplit à partir du JWT). Mets par exemple `7` et `ENSEIGNANT`
   pour `/generer`, `12` et `ETUDIANT` pour `/remedier`, colle le contenu d'un fichier d'exemple dans
   le corps, puis **Execute**. Laisse les deux champs vides : tu obtiens **401**, c'est déjà un test.

Même chose en ligne de commande avec `curl`. Les corps de requête sont dans
`tests/quiz/boite_noire/exemples/` (UTF-8). Passe-les avec
`--data-binary @fichier` : sous Windows, des accents tapés directement dans `-d '...'` arrivent mal
encodés et le serveur répond 400 « There was an error parsing the body ».

```bash
curl -s http://127.0.0.1:8000/api/ai/quiz/generer \
  -H "Content-Type: application/json" -H "X-User-Id: 1" -H "X-User-Role: ENSEIGNANT" \
  --data-binary @tests/quiz/boite_noire/exemples/generer.json
```

Attendu : 200, 4 questions, dont 2 `VALIDEE` et 2 `REJETEE` avec `raisonRejet`, `fournisseur: "simulateur"`.

```bash
curl -s http://127.0.0.1:8000/api/ai/quiz/remedier \
  -H "Content-Type: application/json" -H "X-User-Id: 2" -H "X-User-Role: ETUDIANT" \
  --data-binary @tests/quiz/boite_noire/exemples/remedier.json
```

Attendu : 200 avec `explication`, `conseil` et `questionRattrapage` (statut `VALIDEE`), **sans** `raisonnement` : le Chain-of-Thought est retiré pour un apprenant. Avec `X-User-Role: ENSEIGNANT`, `raisonnement` est présent.

À essayer ensuite, en copiant le fichier et en faisant une modification à la fois :

| Modification | Résultat attendu |
|---|---|
| `X-User-Role: ETUDIANT` sur `/generer` | 403 |
| retirer les deux en-têtes | 401 |
| `"nbQuestions": 11` | 400 avec `details` |
| ajouter `"email": "x@y.tn"` dans `/remedier` | 400 (champ inattendu) |
| `"choixDonne": "left join"` | 400 (la « mauvaise » réponse est la bonne) |
| mettre `[panne-429]` dans le thème ou la notion | 429 `LLM_QUOTA_EXCEEDED` (panne simulée) |
| mettre `moi@mail.com` dans le thème | 200, email masqué, alerte dans `alertes` |

4. Mode sans LLM : `.venv/Scripts/python -m tests.quiz.boite_noire.serveur_demo --llm stub`, puis la même
   requête → **503** `LLM_MISSING_KEY`, message propre, pas de 500.

## 4. Règles à respecter pendant les tests

- Ne jamais mettre de vraie clé API, de token JWT ni de donnée personnelle réelle dans une requête, une
  capture ou le journal. Les en-têtes `X-User-*` ci-dessus sont des valeurs factices.
- Le serveur de démo écoute seulement sur `127.0.0.1` ; ne l'expose pas : en production, seule la gateway
  pose les en-têtes `X-User-*`.
