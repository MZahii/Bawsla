# Checklist — module User

Suivi du travail restant. On coche au fur et à mesure (`[x]`).

## 0. Ménage (à faire par toi)
- [ ] Redémarrer VS Code (JAVA_HOME = JDK 23)
- [ ] Vérifier visuellement login / register (clair, sombre, mobile)
- [ ] Capture `captures/2026-10-13_01_auth-tests.png`
- [ ] Commit du nom dans le journal + capture, puis `git push origin feature/user`

## 1. Web — comptes et administration
- [x] Inscription / connexion : mot de passe renforcé, verrouillage brute force, dernière connexion, 19 tests
- [x] 1.1 Gestion des utilisateurs (admin) : liste réelle + filtres, création, modification (rôle compris), activer/désactiver (aussi en lot), réinitialiser le mot de passe ; `admin-users` branchée
- [x] 1.2 Mon compte : modifier nom/prénom/présentation, changer son mot de passe ; page `features/user/pages/mon-compte` (routes `app.routes.ts` : commit socle à faire relire dans la PR)
- [x] 1.6 Parcours de l'apprenant stocké côté serveur (questionnaire d'accueil + Mon compte)
- [x] 1.3 Photo de profil : PNG/JPEG/WebP, 2 Mo, type vérifié par signature, affichée dans Mon compte et les listes admin
- [x] 1.4 Supprimer mon compte : désactivation confirmée par le mot de passe, puis déconnexion
- [x] 1.5 Demandes formateur : à l'inscription ou depuis Mon compte, liste admin, accepter / refuser avec motif, statut visible ; `formateurValide` ajouté au contrat 4 (commit socle)

## 2. IA — Machine Learning (ai-service/app/modules/user)
- [x] 2.1 Socle : `scikit-learn`, `pandas`, `numpy`, `joblib` dans `requirements.txt` (commit `chore(socle)`)
- [x] 2.2 Générateur de jeu de données simulé : 200 apprenants, 4 profils cachés, 15 cours, 630 inscriptions, graine 42
- [x] 2.3 K-Means : k = 3 (meilleure silhouette entre 3 et 6), groupes Régulier / Débutant en difficulté / Décrocheur, ARI 0,42
- [x] 2.4 Random Forest risque d'échec : test accuracy 0,825, rappel 0,69, F1 0,76, AUC 0,81 ; comparé à 2 références ; raisons lisibles
- [x] 2.5 Filtrage collaboratif hybride (collaboratif 0,5 + popularité 0,2 + domaines 0,3) : réussite@3 0,64 contre 0,31 ; notions ratées ; démarrage à froid
- [x] 2.6 Endpoints `profil`, `risque` (formateur/admin), `recommandations`, `metriques`, `entrainer` (admin) + contrat 5 ; testés via la gateway
- [x] 2.7 Page Admin → Supervision IA : vraies métriques des 3 modèles, graphiques, matrice de confusion, bouton Réentraîner

## 3. Intégration avec les autres modules
- [ ] 3.0 Prévenir le membre Cours : refuser la publication (403) si `formateurValide` est false (contrat 4)
- [x] 3.1 Contrats 6 (Quiz : tentatives), 7 et 8 (Cours : inscriptions) proposés sur `docs/contrat-user-ia` ; contrat 3 déjà en place
- [ ] 3.1b Pousser `docs/contrat-user-ia`, ouvrir la PR et prévenir les membres Quiz et Cours
- [x] 3.2a user-service rassemble les indicateurs (Feign : contrats 3, 6, 7 ; activité) avec l'état de chaque source
- [x] 3.2b user-service appelle ai-service : `/me/ia` (profil + recommandations sur le vrai catalogue), `/{id}/ia` (risque) ; aucune prédiction sur données incomplètes
- [x] 3.3 Page `progres` (apprenant) : recommandations réelles, profil, sources des données ; jamais le risque
- [x] 3.4 Page `apprenants` (formateur) : inscrits à ses cours, risque et raisons, profil ; message tant que le contrat 7 manque

## 4. Qualité et rendu
- [ ] 4.1 Tests : UserService, endpoints admin, pytest pour le ML
- [ ] 4.2 Journal des prompts à jour à chaque étape (+ captures)
- [ ] 4.3 Rapport : données simulées, métriques, biais, limites
- [ ] 4.4 PR `feature/user` → `develop`
- [ ] 4.5 Répétition de la démo (admin IA, progrès apprenant, risque formateur)
