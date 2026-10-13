# Journal des prompts — module User (authentification, profils, rôles)

**Membre** : Belkhouja Hamza
**Branche** : `feature/user`

Modèle d'entrée et règles : voir [../README.md](../README.md). Les entrées les plus récentes vont en haut.

---

### 06 — 2026-10-13 — IA : K-Means, regroupement des apprenants (étape C1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : apprentissage non supervisé du sujet : regrouper les apprenants selon leurs notes, leur temps passé et les cours suivis
- **Contexte fourni** : jeu de données simulé (entrée 05), capture du sujet
- **Prompt** :
  > yes
  (réponse à « Shall I start C1? »)
- **Résultat** :
  - Expérience d'abord : k de 2 à 8, avec et sans transformation log, trois jeux de variables, silhouette et accord avec les profils cachés (ARI).
  - `ml/clustering.py` : `StandardScaler` + `KMeans` (graine 42) sur 7 variables (note, progression, temps, cours suivis, notions ratées, jours actifs, jours depuis la dernière connexion) ; inertie et silhouette pour k = 2 à 8 (données des graphiques coude et silhouette) ; k retenu = meilleure silhouette entre 3 et 6 ; groupes nommés d'après leur centre par des règles lisibles ; `predire()` pour un nouvel apprenant.
  - Résultat : k = 3, silhouette 0,289, ARI 0,419. « Régulier » (100 apprenants, 17 % d'échec : tous les avancés et la plupart des réguliers), « Débutant en difficulté » (59, 58 %), « Décrocheur » (41, 71 %).
  - 12 tests pytest (`tests/user/test_clustering.py`).
- **Vérification** : `pytest tests/user` (19/19) ; affichage des centres et de la répartition des profils cachés par groupe.
- **Gardé / modifié / rejeté** :
  - Rejeté : k = 2, qui a la meilleure silhouette (0,363) mais ne sépare que « ça va / ça ne va pas ». Règle retenue et justifiée : au moins 3 groupes, comme le sujet.
  - Rejeté : la transformation log du temps et des jours (pas de gain net d'ARI ni de silhouette).
  - Limite gardée et à expliquer dans le rapport : le K-Means sépare surtout par l'engagement ; il fusionne « avancé » et « régulier » (leurs comportements sont proches) mais ne classe aucun avancé en difficulté ni en décrocheur.
- **Captures** : _(à ajouter : tableau k / inertie / silhouette)_
- **Leçon** : la meilleure valeur d'une métrique (ici la silhouette à k = 2) n'est pas forcément le meilleur choix pour le produit ; il faut une règle de choix explicite et la justifier.

### 05 — 2026-10-13 — IA : préparation et jeu de données simulé (étapes A1, A2, B1)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : préparer le ML du module User et produire les données d'entraînement simulées demandées par le sujet (« environ 200 étudiants fictifs, à dire clairement dans le rapport »)
- **Contexte fourni** : capture du sujet (partie IA du module User), lecture de toutes les branches du dépôt (quiz-service n'a pas encore de tentatives ni de notions ratées ; cours-service n'a ni inscriptions, ni progression, ni temps passé ; contrat 3 `notions-ratees` garanti stable par le membre Quiz)
- **Prompts** :
  > okay lets start with simulated data and start working ! but before that i need a summuray on what we gonna do from begging to the end in the ia working !

  > okay lets start working on it check the image !
- **Résultat** :
  - A1 : `develop` fusionnée dans `feature/user` (client LLM du socle), sans conflit.
  - A2 (commit socle) : `scikit-learn 1.9.1`, `pandas 3.0.6`, `numpy 2.5.4`, `joblib 1.6.0` dans `requirements.txt`.
  - B1 : `ml/donnees_simulees.py` : 200 apprenants tirés de 4 profils cachés (débutant en difficulté, régulier, avancé, décrocheur) avec du bruit, 15 cours (catégories et niveaux de cours-service), 630 inscriptions ; échec à l'examen selon une loi logistique des chiffres plus du hasard ; graine fixe 42 ; colonnes nommées comme les contrats (`nbErreurs`, `tauxReussite`, `note`, `progression`, `tempsPasseMinutes`…) pour brancher les vraies données sans changer les modèles ; identifiants `A001`… sans aucune donnée personnelle. CSV générés dans `ml/donnees/`.
  - 7 tests pytest (`tests/user/test_donnees_simulees.py`).
- **Vérification** : `pytest` (105/105, dont 7 nouveaux) ; statistiques par profil ; image Docker reconstruite (les 4 bibliothèques s'installent sous Python 3.12).
- **Gardé / modifié / rejeté** :
  - Modifié : la première version donnait 60 % d'échecs (30 % même chez les avancés), peu réaliste. Constante de la loi logistique recalée : 40 % d'échecs au total, 11 % chez les avancés, 20 % chez les réguliers, environ 70 % chez les débutants en difficulté et les décrocheurs.
  - Gardé : la colonne `profilCache` (vérité terrain) sert uniquement à évaluer le K-Means, jamais d'entrée à un modèle.
- **Captures** : _(à ajouter : statistiques par profil)_
- **Leçon** : vérifier les ordres de grandeur des données simulées avant d'entraîner : un taux d'échec irréaliste aurait faussé toutes les métriques.

### 04 — 2026-10-13 — Fin de la partie web : suppression, parcours, photo, demandes formateur (checklist 1.3 à 1.6)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : terminer toute la partie web du module User avant l'IA, pour que les autres membres puissent s'appuyer dessus
- **Contexte fourni** : CHECKLIST.md ; découpage proposé par l'IA en 8 petites étapes (supprimer mon compte, parcours côté serveur, photo, puis demandes formateur en 4 étapes, puis push)
- **Prompts** :
  > i got confused too much ! lets make it with little steps so i can understand !

  > okay do them all and then run a test !
- **Résultat** :
  - Suppression du compte : `POST /api/users/me/desactivation` (mot de passe exigé ; compte désactivé et non effacé pour ne pas casser les données des autres modules ; interdit à l'admin).
  - Parcours : `GET`/`PUT /api/users/me/parcours` (`@Embeddable Parcours`, domaines en JSON via `ListeTexteConverter`) ; enregistré à la fin du questionnaire d'accueil et modifiable dans Mon compte.
  - Photo : table `avatars` séparée (MEDIUMBLOB) ; `POST`/`DELETE /me/avatar`, `GET /{id}/avatar` ; type déduit des premiers octets (PNG, JPEG, WebP), jamais du nom ni du Content-Type ; 2 Mo ; le front charge l'image en blob car la gateway exige le JWT (`AvatarService`, cache par version).
  - Demandes formateur : entité `DemandeFormateur` (EN_ATTENTE, ACCEPTEE, REFUSEE) ; créée à l'inscription si l'expertise est donnée, ou depuis Mon compte ; admin : liste, accepter (rôle ENSEIGNANT + `formateurValide`), refuser avec motif obligatoire ; nouvelle demande possible après un refus.
  - `formateurValide` dans `UserDto` et dans le contrat 4 (`GET /api/users/{id}`), pour que cours-service bloque la publication d'un formateur non validé ; un formateur créé ou promu par l'admin est validé d'office.
  - Pages : Mon compte (photo, parcours, onglet « Devenir formateur » / « Profil de formateur », zone de suppression), Admin → Demandes formateurs (KPI réels, en attente / traitées), Admin → Utilisateurs (photos, « non validé »).
  - Socle (commit séparé) : `RegisterRequest` (front) et page d'inscription qui envoient expertise, présentation, lien ; questionnaire d'accueil qui enregistre aussi côté serveur ; `CONTRATS_API.md`.
  - 60 tests unitaires (23 nouveaux : `DemandeFormateurServiceTest`, `AvatarServiceTest`, ajouts dans `UserServiceTest` et `AuthServiceTest`).
- **Vérification** : `mvnw test` (60/60) ; curl via la gateway pour chaque étape (demande à l'inscription, doublon 409, contrat avant et après validation, refus sans motif 400, double traitement 409, nouvelle demande après refus, apprenant devenu formateur, parcours, photo relue octet par octet par un autre utilisateur, faux PNG, plus de 2 Mo, sans fichier, sans token, suppression) ; scénario complet en navigateur headless (étapes 1 à 7) + 2 captures.
- **Gardé / modifié / rejeté** :
  - Faux échecs des tests curl : les JSON avec accents envoyés depuis le terminal Windows arrivaient mal encodés (« Requête mal formée »). Rejoués depuis des fichiers UTF-8 : tout passe. Ce n'était pas un bug de l'application.
  - Corrigé après le test navigateur : espace manquant dans « Motif du refus : » (Angular supprime les espaces entre balises) ; la zone de dépôt gardait le dernier fichier refusé (elle est maintenant recréée après chaque envoi).
  - Choix : l'inscription d'un formateur sans expertise reste acceptée (exemples du README) ; la demande pourra être faite depuis Mon compte.
  - Non fait : la photo dans l'en-tête et le badge « 3 » du menu admin (socle, `core/layouts`) ; le bandeau « en attente » de l'accueil formateur lit encore le stockage local (socle).
- **Captures** : `captures/2026-10-13_04_admin-formateurs.png`, `captures/2026-10-13_04_mon-compte-photo.png` (navigateur headless, comptes de test supprimés ensuite)
- **Leçon** : quand un test échoue seulement sur certaines données (ici les accents), vérifier l'outil de test avant de chercher un bug dans le code.

### 03 — 2026-10-13 — Mon compte : profil et mot de passe (checklist 1.2)

- **Outil / modèle** : Claude Code (Claude Opus 5.5), extension VS Code
- **Objectif** : que chaque utilisateur modifie ses informations et son mot de passe, sur une page du module User
- **Contexte fourni** : la page `pages/profile` (socle, données de démo), `app.routes.ts`, CHECKLIST.md
- **Prompt** :
  > A
  (réponse au choix proposé par l'IA : A = nouvelle page dans `features/user` + petite PR sur `app.routes.ts`, B = modifier directement `pages/profile` par PR)
- **Résultat** :
  - Backend : champ `bio` (500 car.) ; `PUT /api/users/me` (nom, prénom, présentation ; email et rôle ignorés) ; `PUT /api/users/me/mot-de-passe` (ancien exigé, nouveau différent et conforme aux règles).
  - Mauvais mot de passe actuel → 400 et non 401, car le front déconnecte sur tout 401 hors `/auth`.
  - Front : page `features/user/pages/mon-compte` (chargement, erreur, informations, parcours, sécurité, thème), en-tête mis à jour après enregistrement (`refreshMe`).
  - Socle : `app.routes.ts`, 2 lignes (`/app/profil` et `/studio/profil` pointent vers la nouvelle page), dans un commit séparé à faire relire dans la PR.
  - 5 tests de plus dans `UserServiceTest` (36 au total).
- **Vérification** : `mvnw test` (36/36) ; 11 appels curl (modification, email/rôle ignorés, champs vides, bio trop longue, mauvais ancien mot de passe, même mot de passe, mot de passe faible, succès, ancien refusé et nouveau accepté à la connexion, sans token) ; navigateur headless (apprenant : modification + mot de passe, reste sur la page après une erreur ; formateur : onglet dédié) + capture.
- **Gardé / modifié / rejeté** :
  - Modifié : « Membre depuis » affichait le mois en anglais (pas de `LOCALE_ID` français) → `Intl.DateTimeFormat('fr-FR')`.
  - Retiré de la page (données inventées ou non enregistrées) : statistiques fictives de la carte de gauche, sessions ouvertes, onglet Notifications, langue et réglages non sauvegardés, dépôt de photo (arrive en 1.3), faux profil de formateur (arrive en 1.5).
  - Gardé : le parcours de l'apprenant, enregistré sur l'appareil comme avant (ajouté à la checklist en 1.6 pour le stocker côté serveur).
  - `pages/profile` n'est pas supprimée (socle) : à retirer dans la PR si l'équipe valide.
- **Captures** : `captures/2026-10-13_03_mon-compte.png` (navigateur headless, compte de test supprimé ensuite)
- **Leçon** : vérifier comment le front réagit aux codes d'erreur (ici le 401 qui déconnecte) avant de choisir le code renvoyé par le backend.

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
