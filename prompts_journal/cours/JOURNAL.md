# Journal des prompts — module Cours

**Membre** : _(nom prénom)_
**Branche** : `feature/cours`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 02 — 2026-10-12 — Résumé automatique LOCAL (sans clé API) : choix des modèles et garde-fou de fidélité

- **Outil / modèle** : Claude Code (Claude Sonnet 5.5) pour écrire le code. Aucun LLM distant dans la fonction elle-même : modèles Hugging Face sur CPU.
- **Objectif** : un résumé du cours sans clé API, en français OU en anglais selon la langue du cours, avec sa voix.
- **Contexte fourni** : essai préalable hors projet (BARThez fonctionne avec `transformers<5` ; sur un cours anglais de 65 pages il a halluciné), CLAUDE.md, DESIGN.md, `fidelite.py`.
- **Prompt (à Claude Code)** :
  > « Résumé automatique LOCAL (sans clé API, sans LLM), en français ou en anglais selon la langue du cours, avec sa voix. Un modèle par langue + un garde-fou de fidélité + un mode extractif sans invention. Si le score de fidélité est sous le seuil, bascule automatiquement sur l'extractif avec l'alerte « Résumé reformulé peu fidèle : remplacé par les phrases clés du cours ». Montre-moi d'abord ton plan. »
- **Résultat** : `ai-service/app/modules/cours/local/` (détection de langue, découpage, extractif TextRank, garde-fou, moteur transformers avec un seul modèle en mémoire), route `POST /api/ai/cours/resume-local`, entité `ResumeLocal` côté cours-service, studio et lecteur côté front.
- **Choix des modèles** (licences vérifiées sur Hugging Face) :
  - français : `moussaKam/barthez-orangesum-abstract` (Apache-2.0, 139 M de paramètres) ;
  - anglais : `sshleifer/distilbart-cnn-12-6` (Apache-2.0, 306 M), alternative `facebook/bart-large-cnn` (MIT, 406 M) via `RESUME_LOCAL_MODELE_EN`.
  - `transformers<5` obligatoire (tokenizer de BARThez cassé en 5.x, pipeline « summarization » supprimé en 5.x) : on appelle `generate()` directement.
- **Hallucination observée** : BARThez (modèle français) sur le diaporama anglais de 65 pages (13 678 caractères) écrit du français inventé (« projet de loi », « distanciation sociale »). Ces deux modèles sont entraînés sur des articles de presse (OrangeSum, CNN/DailyMail) : sur un cours, ils inventent ou copient.
- **Garde-fou** : chaque phrase du résumé est rapprochée de la phrase du cours la plus proche (rapidfuzz `token_set_ratio`) et du vocabulaire du cours ; pénalités pour chiffres absents du cours et pour une langue de sortie différente de celle du cours ; score 0-100 ; sous `RESUME_LOCAL_SEUIL` (50) le mode auto remplace le reformulé par les phrases clés.
- **Vérification** : 33 tests pytest avec faux modèles (détection fr/en/autre, découpage, bascule vers l'extractif, 503 sans dépendances, langue transmise à chaque fournisseur vocal) ; 14 tests JUnit ; test lent `RUN_SLOW=1` avec les vrais modèles sur le diaporama. Scores mesurés sur ce PDF : reformulé distilbart = 99, reformulé BARThez sur ce cours anglais = 26 (donc remplacé), extractif = 100. Mémoire mesurée : BARThez ≈ 1,0 Go, distilbart ≈ 1,7 Go au pic.
- **Gardé / modifié / rejeté** :
  - Gardé : un seul modèle en mémoire, déchargé après inactivité (`RESUME_LOCAL_LIBERER_APRES_SECONDES`) ; extractif disponible même sans torch.
  - Modifié : la détection de langue par mots vides confondait l'espagnol avec le français (« de », « la », « que ») : ajout d'une liste de mots vides d'autres langues ; le code (SQL) est retiré avant la mesure. L'extractif retenait un bloc aplati de tableau : pénalité pour un long bloc sans ponctuation.
  - Rejeté : `langdetect` (probabiliste, dépendance de plus pour deux langues) ; générer les résumés avec un LLM distant (hors du sujet : sans clé API).
- **Captures** : _(à ajouter après les essais dans le navigateur)_
- **Leçon** : un modèle de résumé local ne se juge pas sur la fluidité de sa sortie mais sur sa fidélité au cours ; mesurer d'abord (scores, RAM) puis seulement choisir les seuils.

---

### 01 — 2026-10-12 — Explication orale par l'IA (phase 2) : prompt `explication_v1`

- **Outil / modèle** : Claude Code (Claude Sonnet 5.5) pour écrire le code ; Gemini (`gemini-3.8-flash`) pour le script, via `app/core/llm_client.py`.
- **Objectif** : l'IA doit RÉEXPLIQUER le cours à l'oral comme un enseignant en vidéo (et non lire ou paraphraser la fiche de révision). Entrée : le texte complet du cours par page ; la fiche validée ne sert que de plan (titres des notions).
- **Contexte fourni** : CLAUDE.md, DESIGN.md, CONTRATS_API.md, phase 1 (chaîne de voix), `fidelite.py` (vérification des extraits sans LLM).
- **Prompt (à Claude Code)** :
  > « La Phase 1 LIT la fiche. Ce n'est PAS ce que je veux. L'IA doit réexpliquer le cours comme un enseignant : accroche, puis pour chaque notion explication → analogie → exemple → démonstration, une question de vérification avec sa réponse, un récapitulatif. 450 à 750 mots (3 à 5 minutes), monologue ou dialogue avec questions d'un étudiant. JSON validé par Pydantic, extraitSource vérifié par fidelite.py, analogies libres mais signalées, score visible du formateur seul. Le français est généré depuis le cours ; l'anglais est la TRADUCTION du français VALIDÉ. Montre-moi ton plan avant de coder. »
- **Prompt `explication_v1` (structure, dans `prompts/explication_v1.py`)** : RÔLE (enseignant qui explique à l'oral) / CONTEXTE (niveau, mode, durée, plan, texte du cours par page) / TÂCHE / FORMAT (JSON : segments `{ordre, type, locuteur, texte, notion, extraitSource, page}`) / CONTRAINTES (extraits recopiés mot pour mot, aucun chiffre ni terme technique inventé hors analogie, phrases courtes à dire à voix haute, pas de markdown). Aucune donnée personnelle n'est envoyée : le texte passe par `Sanitizer`.
- **Résultat** : module `ai-service/app/modules/cours/explication/` (schémas, sélection des pages, contrôle de structure, génération avec une régénération guidée, traduction, routes `/explication`, `/traduire`, `/fidelite`, `/audio`) ; côté cours-service : entités, traitement asynchrone, aperçu audio, validation ; côté front : studio du formateur et section « Explication par l'IA » du lecteur.
- **Vérification** : 175 tests pytest (faux LLM : JSON invalide relancé une fois, segment infidèle signalé, structure, traduction, audio) ; 89 tests JUnit (droits, visibilité de l'apprenant, aperçu obligatoire avant validation, audio refait si le texte change, anglais dépendant du français validé, délais par appel, audio de 8 Mo et de plus de 20 millions de caractères en base64) ; `ng build` sans erreur. Essai réel de la chaîne vocale (3 segments, deux voix) : Gemini a refusé le dialogue (400), repli automatique sur gTTS en 4 s, minutage exact. L'essai réel du script Gemini n'a pas pu avoir lieu : quota du jour atteint (429).
- **Gardé / modifié / rejeté** :
  - Gardé : la structure imposée au prompt, vérifiée en code (`verifier_structure`) plutôt que crue sur parole.
  - Modifié : règle de structure trop stricte (une réponse juste après une question n'a pas à être une « explication » séparée) ; seuils de fidélité (score 60 % extraits / 20 % chiffres / 20 % termes).
  - Rejeté : s'appuyer sur la fiche comme texte source (elle résume déjà : on ré-expliquerait un résumé) ; générer l'anglais depuis le cours (deux scripts différents, extraits impossibles à comparer).
- **Captures** : _(à ajouter après les essais dans le navigateur)_
- **Leçon** : écrire d'abord le contrat de validation (structure, fidélité, durée) et les tests avec un faux LLM, puis le prompt ; tester tôt les services externes un par un : le dialogue Gemini exige `speech_metadata.speaker` sur chaque partie (correction à faire dans `core/llm_client.py`, par PR).
