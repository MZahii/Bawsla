# Journal des prompts — module User (authentification, profils, rôles)

**Membre** : _(nom prénom)_
**Branche** : `feature/user`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

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
