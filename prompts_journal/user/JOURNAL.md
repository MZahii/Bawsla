# Journal des prompts — module User (authentification, profils, rôles)

**Membre** : Belkhouja Hamza
**Branche** : `feature/user`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 02 — 2026-10-13 — Gestion des utilisateurs par l'admin (checklist 1.1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : remplacer les données de démo de la page Admin → Utilisateurs par la vraie gestion des comptes
- **Contexte fourni** : CHECKLIST.md, la page `admin-users` existante (démo), CONTRATS_API.md (« endpoints internes à chaque module : évoluent librement »)
- **Prompt** :
  > okay make a check list for all what we still have to do and kets do it one by one !
- **Résultat** :
  - `prompts_journal/user/CHECKLIST.md` : tout le travail restant, en 5 parties.
  - Backend (ADMIN uniquement) : `POST /api/users`, `PUT /api/users/{id}`, `PATCH /api/users/{id}/statut`, `PUT /api/users/{id}/mot-de-passe` (débloque aussi le compte) ; liste triée du plus récent au plus ancien.
  - Garde-fous : un admin ne peut ni se désactiver ni se retirer le rôle ADMIN ; le dernier admin actif ne peut pas perdre son accès.
  - Règles de mot de passe regroupées dans `MotDePasse` (une seule source pour l'inscription, la création et la réinitialisation).
  - Front : `UserService` (5 appels), modèles dans `features/user/models`, page branchée (chargement, erreur avec « Réessayer », création, modification, activation en lot, réinitialisation du mot de passe).
  - 12 tests `UserServiceTest` (31 tests au total dans user-service).
- **Vérification** : `mvnw test` (31/31) ; 13 appels curl via la gateway (création, doublon, modification, désactivation + connexion refusée, verrouillage puis réinitialisation, garde-fous admin, 403, 404) ; parcours navigateur headless (liste réelle, création par la boîte de dialogue, désactivation/réactivation, pas de « Désactiver » sur son propre compte) + capture.
- **Gardé / modifié / rejeté** :
  - Bug trouvé au test navigateur : après « Désactiver », la ligne restait « Actif » alors que la base était à jour. Cause : en zoneless, la table n'était alimentée que par un `effect`, exécuté trop tard. Corrigé avec `majTous()` qui met aussi la table à jour tout de suite.
  - Retiré : le delta du KPI « Comptes » (`bw-kpi` affiche toujours « % », ce qui donnait « +3 % » pour 3 comptes) ; `bw-kpi` est dans le socle, donc non modifié.
  - Retirés car non implémentés : « Importer (CSV) », « Envoyer un email », « Voir l'activité », colonne « Profil ou expertise » (reviendra avec les demandes formateur, 1.5).
  - Choix : l'admin saisit le mot de passe initial lui-même ; l'API ne génère ni ne renvoie aucun mot de passe.
- **Captures** : `captures/2026-10-13_02_admin-users.png` (navigateur headless, données de test supprimées ensuite)
- **Leçon** : vérifier l'écran après chaque action, pas seulement l'API : la base était juste mais l'affichage ne l'était pas.

### 01 — 2026-10-13 — Inscription et connexion : durcissement et tests

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : vérifier l'inscription et la connexion existantes, puis combler leurs manques
- **Contexte fourni** : CLAUDE.md, README.md, CONTRATS_API.md, le sujet `Bawsla_modules_IA.pdf`, le code de `user-service` ; stack lancée avec `docker compose up -d --build`
- **Prompt** :
  > OKAY ! now i need u to work on the user module ! ( the basic create account and login
- **Résultat** :
  - L'IA a d'abord testé l'existant par curl via la gateway (inscription, doublon 409, validation 400, rôle ADMIN refusé, mauvais mot de passe 401, `/me`, sans token 401, rôle 403, en-têtes `X-User-*` falsifiés rejetés) : tout fonctionnait.
  - Manques trouvés et corrigés dans `user-service` :
    - mot de passe : au moins une lettre et un chiffre (`@Pattern` dans `RegisterRequest`) ;
    - protection brute force : 5 échecs consécutifs → compte verrouillé 15 min (`User.enregistrerEchec`, `@Transactional(noRollbackFor = UnauthorizedException.class)` pour que l'échec reste compté malgré le 401) ;
    - `derniereConnexion` enregistrée à chaque connexion et renvoyée par `/me` (servira au ML : jours depuis la dernière activité) ;
    - `ClockConfig` : horloge injectée pour figer le temps dans les tests.
  - 19 tests unitaires : `AuthServiceTest` (11, JUnit 5 + Mockito, sans base) et `RegisterRequestValidationTest` (8).
- **Vérification** : `./mvnw -q -pl user-service test` (19/19 verts) ; reconstruction du conteneur puis curl via la gateway : mot de passe faible refusé, `derniereConnexion` renseignée, 6ᵉ tentative bloquée avec « Réessaie dans 15 minutes » ; colonnes vérifiées dans phpMyAdmin.
- **Gardé / modifié / rejeté** :
  - Gardé : tout le code produit.
  - Modifié : le message de verrouillage affichait « 14 minutes » (troncature) → arrondi au supérieur avec `Math.ceilDiv`.
  - Non fait volontairement : les pages login/register (dossier `pages/`, socle) ne sont pas modifiées ; elles affichent déjà les messages du backend.
- **Captures** : _(à ajouter : `captures/2026-10-13_01_auth-tests.png`)_
- **Leçon** : tester l'existant avant d'écrire du code ; le test a révélé les vrais manques (mot de passe faible accepté, aucune protection brute force).
