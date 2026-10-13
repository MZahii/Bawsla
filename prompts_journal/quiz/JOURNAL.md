# Journal des prompts — module Quiz

**Membre** : Zahi Mohamed
**Branche** : `feature/quiz`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 07 — 2026-10-13 — Notebook d'analyse de l'IA et captures de l'IA en marche

- **Outil / modèle** : Claude Code (Claude Opus 5.5), nbformat + nbclient pour le notebook, Playwright (Chrome) pour les captures
- **Objectif** : documenter et démontrer le choix du fournisseur, la conception des prompts, les garde-fous et l'appel par API, avec des résultats réels et non recopiés.
- **Contexte fourni** : le code de `app/core` (llm_client, config, sanitizer) et de `app/modules/quiz`, le serveur de démo des tests boîte noire, la stack Docker en marche.
- **Prompt** :
  > prends des screenshots du ai quizz en marche et produit un ipynb qui fait toute une analyse par apport au choix de l ai et de son developpement sa creation ou son appel depuis un api
- **Résultat** : `prompts_journal/quiz/analyse_ia_quiz.ipynb`, exécuté, sorties conservées. Neuf sections :
  - choix du fournisseur : Gemini, secours Groq, OpenAI en option ; modèle local et fine-tuning écartés ;
  - table de bascule calculée par la vraie fonction `_declenche_secours` ;
  - réglages et taille de lot (graphe de `taille_lot` face au budget Groq) ;
  - prompts réellement envoyés ;
  - garde-fous : Sanitizer, validation stricte, Self-Consistency ;
  - appel de la vraie application FastAPI avec LLM simulé : 2 questions validées, 2 rejetées, remédiation vue apprenant / vue formateur, matrice d'erreurs 8/8 ;
  - chemin de production via la gateway (401, 403 même avec un faux `X-User-Role: ADMIN`, 503 sans clé) ;
  - tests : 113 réussis, couverture de branches 100 %.
- **Vérification** : notebook exécuté de bout en bout sans erreur. Aucune clé, aucun jeton JWT, aucun email dans le fichier (vérifié par recherche). Les identifiants de test sont lus dans des variables d'environnement.
- **Gardé / modifié / rejeté** : modifié. Le health du serveur de démo affichait `stub` (il lit `.env`) : une note l'explique. Une ligne de code mal échappée a cassé une ré-exécution et laissé deux serveurs de démo actifs : corrigée, serveurs arrêtés.
- **Captures** : `captures/2026-10-13_07_swagger-{generer,remedier}-{requete,reponse}.png` et `captures/2026-10-13_07_nb-*.png` (11 sections du notebook).
- **Leçon** : aucune clé réelle n'est configurée, donc l'IA tourne sur un simulateur clairement étiqueté (`fournisseur: "simulateur"`). Avec une clé Gemini dans `.env`, la section 7 montre un vrai quiz sans changer le notebook.

---

### 06 — 2026-10-13 — Tests boîte noire et boîte blanche de l'IA (génération et remédiation)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), Chrome headless pour les captures
- **Objectif** : tester les deux routes IA du module des deux points de vue (boîte noire, puis boîte blanche), produire des captures et un guide pour refaire les tests soi-même.
- **Prompt** :
  > fais les test en boite noir et boite blanche et donne moi en retour des screenshots des fonctionnalite et puis guide moi pour refaire les tests moi meme
- **Résultat** (tout sous `ai-service/tests/quiz/`) :
  - `boite_noire/serveur_demo.py` : lance la vraie application (`app.main:app`) avec un **LLM simulé déterministe**, sans clé et sans réseau. À chaque génération, il produit volontairement une question mal formée (2 bonnes réponses) et une question « piège » sur laquelle le vérificateur diverge. Mettre `[panne-429]` dans un texte simule un quota épuisé. L'option `--llm stub` lance le serveur sans aucun LLM ;
  - `boite_noire/scenarios.py` : **38 cas** HTTP. Techniques : nominal, table de décision (authentification × rôle), valeurs limites, partitions invalides, confidentialité, sécurité, erreurs 429 et 503. Le script génère les rapports HTML `rapport.html`, `generation.html` et `remediation.html` ;
  - `test_quiz_boite_blanche.py` : un test par branche non couverte, repérée avec `pytest --cov-branch`. Couverture : 98 % → **100 %** des instructions et des branches (452 instructions, 118 branches) ;
  - `boite_noire/exemples/*.json` et `GUIDE_TESTS.md` : la marche à suivre pas à pas.
- **Défaut trouvé par la boîte blanche** : dans `coherence._divergence`, une réponse de vérification vide (`""`) comptait comme la lettre « A », car en Python `"" in "ABCD"` vaut `True`. Conséquence : une question pouvait être VALIDÉE alors que le vérificateur n'avait rien répondu. **Corrigé** par `if not lettre or lettre not in LETTRES`, et un test couvre ce cas.
- **Vérification** : `pytest tests -q` donne 211 passed ; `pytest tests/quiz` donne 113 passed avec 100 % des branches couvertes ; les scénarios boîte noire donnent 38/38. Une mutation des contrôles (mauvais nombre de questions, rattrapage absent) fait bien échouer les cas, donc ils ne passent pas à vide.
- **Gardé / modifié / rejeté** :
  - Gardé : le LLM simulé plutôt qu'un vrai fournisseur pour la boîte noire, pour des résultats reproductibles et aucun quota consommé. Les vrais appels Gemini ou Groq restent à montrer à la démo ;
  - Gardé : le détail de validation de `extra="forbid"` est en anglais (« Extra inputs are not permitted »). Il vient du socle et n'a pas été modifié ;
  - Modifié : sous Windows, des accents passés à `curl -d '...'` donnent un 400 « error parsing the body ». Le guide utilise donc des fichiers JSON (`--data-binary @fichier`) ;
  - Modifié après relecture par l'agent `code-reviewer` (verdict COMMENT, aucun défaut grave) :
    - le serveur `--llm stub` lisait `.env` et pouvait appeler un vrai fournisseur. Les deux modes remplacent maintenant le LLM ;
    - beaucoup de cas en 400 ne vérifiaient que le statut. Chacun vérifie désormais le champ cité dans `details`, et les 401 vérifient le code `UNAUTHORIZED` ;
    - le contrôle « aucun identifiant renvoyé » ne pouvait jamais échouer. Il est remplacé par « l'email ou le téléphone envoyé ne revient pas en clair » ;
    - les serveurs de test sont arrêtés proprement, même en cas d'erreur, et un serveur qui plante au démarrage est signalé tout de suite ;
    - le guide installait les tests sans pytest-asyncio et affirmait à tort que Swagger n'a pas de champ pour les en-têtes.
- **Captures** :
  - `captures/2026-10-13_06_bn-generation.png` : quiz généré, avec 2 questions validées et 2 rejetées, chacune avec sa raison ;
  - `captures/2026-10-13_06_bn-remediation.png` : remédiation, avec la vue de l'apprenant et le raisonnement réservé au formateur ;
  - `captures/2026-10-13_06_bn-rapport.png` : les 38 cas boîte noire ;
  - `captures/2026-10-13_06_bb-pytest.png`, `_06_bb-couverture.png`, `_06_bb-couverture-coherence.png` : pytest et couverture à 100 % ;
  - `captures/2026-10-13_06_swagger.png` : les routes exposées.
- **Leçon** : viser 100 % des branches n'est pas une fin en soi, mais chercher l'entrée qui force la branche manquante a révélé un vrai défaut que 100 tests fonctionnels n'avaient pas vu. Il faut toujours tester la chaîne vide.

---

### 05 — 2026-10-13 — IA 2 : remédiation adaptative (Chain-of-Thought, question de rattrapage) et relecture

- **Outil / modèle** : Claude Code (Claude Opus 5.5), agent `code-reviewer` d'oh-my-claudecode pour la relecture (deux passes)
- **Objectif** : `POST /api/ai/quiz/remedier` : pour UNE mauvaise réponse, diagnostiquer la confusion, l'expliquer à l'apprenant et proposer une question de rattrapage vérifiée (étape 4 du guide).
- **Contexte fourni** : le contrat 5 de CONTRATS_API.md (PR #3, pas encore fusionnée), le code de l'IA 1 (validation, Self-Consistency), le socle `app/core`.
- **Prompt** : la spécification est le prompt de l'étape 4 de `GUIDE_MODULE_QUIZ.md`. Le lancement s'est fait avec la consigne :
  > IA 2, la remédiation (/remedier). N'attends pas la PR #3 : le contrat est déjà écrit, tu peux coder en parallèle.
- **Résultat** (fichiers de `ai-service/app/modules/quiz/`) :
  - `schemas.py` : `RemedierRequest` (notion, niveau de Bloom, énoncé, choix donné, bonne réponse, erreur associée et explication facultatives) avec `extra="forbid"` : tout champ inattendu (`etudiantId`, `nom`, `email`…) est refusé en 400, donc **aucun identifiant d'apprenant ne peut atteindre le LLM**. Une « mauvaise réponse » égale à la bonne est refusée ;
  - `prompts/remediation_v1.py` : prompt de tuteur au tutoiement, en **Chain-of-Thought** : le champ `raisonnement` vient en premier (réservé au formateur, jamais montré à l'apprenant), puis `explication`, `conseil` et `questionRattrapage`. Les données sont encadrées par `<reponse_apprenant>` et traitées comme une simple donnée ;
  - `remediation.py` : Sanitizer sur tous les textes, pré-contrôle du budget de jetons (413), un appel à 0.3, validation stricte du rattrapage (mêmes règles que l'IA 1, niveau de Bloom égal ou plus bas, différent de la question d'origine), puis 1 vérification Self-Consistency à 0.7. Un rattrapage rejeté n'annule pas le diagnostic : il est écarté avec la raison dans `alertes`.
- **Vérification** : `pytest` donne 190 tests au vert avant relecture, 198 après corrections.
- **Gardé / modifié / rejeté** : la première relecture a conclu « REQUEST CHANGES ». **Corrections appliquées** :
  1. **[HIGH]** Des choix de rattrapage mal typés (`["A", "B"…]`, `[null, {}]`) faisaient échouer tout le schéma : 502 et diagnostic perdu. Les éléments qui ne sont pas des objets sont maintenant écartés, et seule la question est rejetée (cela protège aussi l'IA 1).
  2. **[MEDIUM]** Un raisonnement « étape par étape » renvoyé en liste devenait vide, donc 502. La liste est maintenant jointe, et le prompt précise « une chaîne, jamais une liste ».
  3. **[MEDIUM]** Plafond de sortie trop juste : 900 → 1 050 jetons (même estimation qu'une question générée, plus le diagnostic).
  4. **[LOW]** Une balise `</reponse_apprenant>` glissée dans un texte pouvait sortir du bloc de données : elle est retirée et signalée comme consigne suspecte.
  5. **[LOW]** `null` envoyé par quiz-service pour l'erreur associée ou l'explication : accepté. Conseil vide : alerte. La compétence du rattrapage est exactement la notion d'origine. Fonctions de l'IA 1 réutilisées plutôt que copiées.
  - Seconde relecture : **APPROVE**.
  - Écarts au guide, gardés : la forme du contrat de la PR #3 (textes des choix plutôt qu'index d'options) ; la `notion` renvoyée est celle d'origine, pour que quiz-service agrège les notions ratées par compétence ; 1 seule vérification du rattrapage (économie de quota, réglable par `QUIZ_NB_VERIFICATIONS_REMEDIATION`).
  - Gardé comme limite connue : la route est ouverte à tout utilisateur authentifié, donc un apprenant pourrait l'appeler directement et lire le `raisonnement`. Il n'apprend rien de nouveau (il doit fournir la bonne réponse), mais c'est à trancher dans le contrat quand quiz-service sera branché.
  - Gardé comme limite connue : environ 2 000 jetons par remédiation ; avec Groq gratuit (8 000 par minute), quiz-service devra étaler les appels et relancer après un 429.
- **Captures** : à ajouter, au format `captures/2026-10-13_05_ia2-remediation.png` et `captures/2026-10-13_05_relecture.png`.
- **Leçon** : un LLM ne respecte pas toujours le type demandé (liste au lieu de chaîne, chaîne au lieu d'objet). Il faut que la partie précieuse de la réponse (le diagnostic) survive à un défaut sur la partie accessoire (le rattrapage).

---

### 04 — 2026-10-13 — IA 1 : Self-Consistency renforcée (2 vérifications à 0.7)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), avec oh-my-claudecode 5.7.1
- **Objectif** : rendre la Self-Consistency capable de rejeter réellement des questions, pour en montrer en démo.
- **Contexte fourni** : le code de l'IA 1 (entrée 03) et l'avis d'un relecteur extérieur.
- **Prompt** :
  > La Self-Consistency est un peu faible. Une seule vérification à température 0, avec le même modèle, sera presque toujours d'accord avec lui-même : tu risques de n'avoir aucun rejet à montrer en démo. Demande-lui : « Self-Consistency : 2 vérifications à température 0.7 (réglable), rejet si une seule diverge. »
- **Résultat** :
  - nouveaux réglages `QUIZ_NB_VERIFICATIONS` (2 par défaut) et `QUIZ_TEMPERATURE_VERIFICATION` (0.7 par défaut) dans `config.py` ;
  - `coherence.verifier` fait N passes indépendantes. À chaque passe, les choix sont mélangés dans un ordre différent (graine fixe par énoncé et par passe), pour que le modèle ne retienne pas une position ;
  - une seule divergence suffit pour REJETEE. La raison indique la passe concernée, par exemple « Désaccord à la vérification : « X » choisi au lieu de « Y » — justification (vérification 2/2) ».
- **Vérification** : `pytest` : 163 tests passent, dont 5 nouveaux (réglages par défaut, deux passes d'accord, une seule passe divergente qui rejette, ordre des choix par passe, graine par passe).
- **Gardé / modifié / rejeté** :
  - gardé : **un appel par passe pour tout le lot**, et non un appel par question en parallèle comme le proposait le guide. Avec la limite gratuite de Groq (environ 8 000 jetons par minute), 10 questions × 2 vérifications feraient 20 appels au lieu de 2 ;
  - corrigé : ma première version (entrée 03) s'écartait du guide, qui demandait déjà `nb_verifications=2` et une température de vérification plus haute. Elle est maintenant alignée.
- **Leçon** : relire la spécification point par point avant d'optimiser. Le gain de quota de la première version (1 appel à 0) se payait en efficacité du contrôle.

---

### 03 — 2026-10-13 — IA 1 : génération de QCM (Few-Shot, JSON strict, Self-Consistency) et relecture

- **Outil / modèle** : Claude Code (Claude Opus 5.5), agent `code-reviewer` d'oh-my-claudecode pour la relecture
- **Objectif** : remplacer le stub `POST /api/ai/quiz/generer` par la vraie génération, sans jamais lire le cours (étape 2 du guide).
- **Contexte fourni** : CLAUDE.md, CONTRATS_API.md (PR #3), le socle `app/core` (LLMClient, `fournisseur_courant()`, `estimer_tokens`, Sanitizer), et les conventions du module Cours lues sur `origin/feature/cours` sans les modifier.
- **Prompt** : la spécification est le prompt de l'étape 2 de `GUIDE_MODULE_QUIZ.md`. Le lancement s'est fait avec la consigne :
  > Pour l'IA 1, vérifie d'abord que develop (avec le client Gemini de djappa) est fusionné dans feature/quiz ; sinon, travaille uniquement avec un faux LLM dans les tests.
- **Résultat** (fichiers de `ai-service/app/modules/quiz/`) :
  - `schemas.py` : `GenererQuizRequest` (thème, 1 à 8 compétences, niveaux de Bloom, difficulté, 1 à 10 questions ; **aucun champ de texte de cours**) et `GenererQuizResponse` (questions VALIDEE ou REJETEE avec `raisonRejet`, `versionPrompt`, `fournisseur`, `alertes`) ;
  - `prompts/generation_v1.py`, `verification_v1.py` et un registre versionné. `exemples_bloom.py` contient 2 exemples rédigés à la main par niveau de Bloom (12 sujets variés). Seuls les exemples des niveaux demandés sont envoyés ;
  - `generation.py` : objectifs nettoyés par le Sanitizer et insérés comme **donnée** entre `<objectifs>…</objectifs>`. Les questions sont demandées par lots (`QUIZ_LOT_MAX` = 5), réduits si le budget de jetons de Groq l'exige. Chaque question est validée strictement : 4 choix distincts, 1 seul correct, une `erreurAssociee` par distracteur, une explication, un niveau et une compétence demandés. Une question mal formée est rejetée **seule**, sans perdre le lot ;
  - `coherence.py` : vérification sans la bonne réponse, sans les erreurs et sans l'explication, avec des choix mélangés ;
  - tests `tests/quiz/` avec un FauxLLM (aucun appel réseau) : routes, génération, cohérence, confidentialité, budget.
- **Vérification** : `pytest` donne 151 tests au vert. Mesure de la taille réelle des prompts : environ 1 350 jetons pour 2 niveaux, environ 3 200 pour 6.
- **Gardé / modifié / rejeté** : la relecture par l'agent `code-reviewer` a conclu « REQUEST CHANGES ». **7 corrections appliquées** :
  1. **[HIGH] Contrôle de rôle absent** : un apprenant pouvait générer des quiz et épuiser le quota. Il reçoit maintenant un 403, et seuls ENSEIGNANT et ADMIN passent.
  2. **[HIGH] Un champ `null` renvoyé par le LLM** (compétence, `estCorrecte`, choix…) faisait échouer tout le schéma (502, lot perdu). Les schémas internes transforment maintenant `null` en valeur vide, et seule la question concernée est rejetée.
  3. **[HIGH] Le FauxLLM ne validait jamais de JSON brut** : les tests ne pouvaient pas voir le défaut 2. Il valide maintenant les dictionnaires avec le schéma, comme le vrai `complete_json`, et des tests avec des champs `null` ont été ajoutés.
  4. **[LOW] Estimation de sortie trop basse** : elle passe de 350 à 450 jetons par question, avec des tests de budget recalibrés.
  5. **[LOW] Faux doublons** : une question rejetée bloquait sa version correcte. Seules les questions VALIDEE comptent maintenant.
  6. **[LOW] Niveau de Bloom** : « Analyser » ou « appliquer » étaient refusés. Le niveau est maintenant normalisé (`strip().upper()`).
  7. **[LOW] `fournisseur` vide sans secours** : il reprend maintenant `LLM_PROVIDER`.
  - Rejeté (déjà traité) : une remarque sur CONTRATS_API.md, réglée par la PR #3.
  - Gardé comme limite connue : le budget **cumulé par minute** de Groq n'est pas garanti sur une grosse génération. quiz-service relancera après un 429.
  - Gardé comme limite connue : le Sanitizer du socle ne masque pas les noms de personnes. Le risque est faible, car les objectifs sont écrits par le formateur et ne contiennent aucune donnée d'apprenant.
  - Résultat après corrections : 158 tests au vert.
- **Captures** : à ajouter, au format `captures/2026-10-13_03_ia1-generation.png` et `captures/2026-10-13_03_relecture.png`.
- **Leçon** : un faux LLM trop « gentil » (qui renvoie des objets déjà valides) cache les vrais défauts. Il faut tester avec le JSON brut, tel que le fournisseur le renvoie.

---

### 02 — 2026-10-13 — PR #3 (socle) : contrat de l'IA du Quiz

- **Outil / modèle** : Claude Code (Claude Opus 5.5)
- **Objectif** : mettre CONTRATS_API.md en accord avec la spécification avant de coder (conflits C1 à C4 du plan).
- **Contexte fourni** : le plan de l'étape 0 et CONTRATS_API.md.
- **Prompt** :
  > Approuvé, étape 1 d'abord. Avant de modifier CONTRATS_API.md, montre-moi les conflits C1 à C4 et ta proposition pour chacun. C'est un fichier du socle : fais-le sur une petite branche à part avec sa propre PR, et ne change pas la forme existante de GET /api/quiz/notions-ratees (le module User en dépend).

  Puis, après relecture des propositions avec une grille (forme de notions-ratees inchangée, champ `texte` supprimé, `nbQuestions` ≤ 10, aucune donnée personnelle dans les routes IA) :
  > Pousser et ouvrir la PR (Recommandé)
- **Résultat** : branche `docs/contrat-quiz-ia`, commit `31bb7ab`, PR #3 vers `develop` (relecteur : djappa001). Seul CONTRATS_API.md change :
  - contrat 5 : `generer` part des objectifs (theme, competences, niveauxBloom, difficulte, 1 à 10 questions), sans texte de cours ;
  - contrat 5 : nouvelle route `remedier`, sans identifiant ni nom d'apprenant ;
  - erreur 413 `LLM_TOO_LONG_FOR_FALLBACK` ajoutée ;
  - contrat 2 : Quiz retiré des consommateurs de `/cours/{id}/texte` ;
  - contrat 3 : statut « prévu », **forme de notions-ratees inchangée**.
- **Vérification** : diff relu avant le push. Aucun autre fichier touché.
- **Gardé / modifié / rejeté** : gardé tel quel. La règle du socle est respectée : PR séparée et relue, au lieu d'un commit sur `feature/quiz`.
- **Captures** : à ajouter, au format `captures/2026-10-13_02_pr3-diff.png`.
- **Leçon** : écrire le contrat d'abord évite de coder contre une interface que l'équipe n'a pas validée.

---

### 01 — 2026-10-13 — Étape 0 : analyse et plan (aucun code)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), skill `/oh-my-claudecode:plan`
- **Objectif** : obtenir un plan complet du module avant d'écrire du code, et repérer les conflits avec le socle et le module Cours.
- **Contexte fourni** : CLAUDE.md, DESIGN.md, CONTRATS_API.md, le code existant du Quiz, et `origin/feature/cours` (lu sans le modifier).
- **Prompt** (étape 0 de `GUIDE_MODULE_QUIZ.md`, début) :
  > /oh-my-claudecode:plan
  > Tu travailles sur le module Quiz de Bawsla, branche feature/quiz (qui contient maintenant le client Gemini de feature/socle-llm-gemini). Lis d'abord CLAUDE.md, DESIGN.md et CONTRATS_API.md, puis tout le code de backend/quiz-service, ai-service (core + modules/quiz) et frontend/src/app/features/quiz. […] Produis un plan en étapes : modèle de données, endpoints Java, endpoints IA, intégration quiz-service -> ai-service, branchement du front (en listant précisément les données de démo à remplacer dans chaque page), tests. Signale tout conflit avec le socle, les contrats existants ou le travail du module Cours. N'écris aucun code.
- **Résultat** : `.omc/plans/quiz-etape0.md` (exigences R1 à R10, conflits C1 à C11, étapes, risques). Conflits importants relevés :
  - C1 : l'ancien contrat de `generer` demandait le texte du cours ;
  - C2 : le contrat 2 citait le Quiz comme lecteur du texte des cours ;
  - C5 : un apprenant aurait vu les brouillons ;
  - C7 : le générateur du front faisait choisir un chapitre, ce qui revenait à lire le contenu du cours.
- **Vérification** : plan relu, puis complété à ma demande.
- **Gardé / modifié / rejeté** : modifié. J'ai sorti l'examen final et les certificats du périmètre, avec cette consigne :
  > Hors périmètre, mais vérifie que quiz-list et le lecteur de quiz ne cassent pas les liens vers examen-final et certificats : ces pages doivent rester accessibles avec leurs données de démo.

  Le plan a ensuite reçu les contraintes L1 à L4 et le critère 4bis.
- **Captures** : à ajouter, au format `captures/2026-10-13_01_plan-etape0.png`.
- **Leçon** : le mode plan a trouvé des conflits de contrat (C1, C2) que je n'aurais vus qu'au moment de l'intégration.
