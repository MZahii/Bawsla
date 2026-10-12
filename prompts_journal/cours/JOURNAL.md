# Journal des prompts — module Cours

**Membre** : _(nom prénom)_
**Branche** : `feature/cours`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

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
