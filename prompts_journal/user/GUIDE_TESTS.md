# Manual test guide — User module (web + AI)

Branch: `feature/user-ia`. Tick each box (`[ ]` → `[x]`) as you go.
Each test gives **the steps** and **the expected result**. If a result differs, note the test number and what you saw.

**Table of contents**
0. [Preparation](#0-preparation) · 1. [Sign-up and login](#1-sign-up-and-login) · 2. [Welcome questionnaire](#2-welcome-questionnaire)
3. [My account](#3-my-account) · 4. [Trainer requests](#4-trainer-requests) · 5. [Admin: users](#5-admin-users)
6. [AI: Admin supervision page](#6-ai-admin--supervision-ia) · 7. [AI: learner Progress page](#7-ai-learner--progrès) · 8. [AI: trainer Learners page](#8-ai-trainer--apprenants)
9. [AI: API tests (PowerShell)](#9-ai-api-tests-powershell) · 10. [Security](#10-security) · 11. [Database checks](#11-database-checks-phpmyadmin)
12. [Automated tests](#12-automated-tests) · 13. [Known limits](#13-known-limits-not-bugs) · 14. [Cleanup](#14-cleanup)

---

## 0. Preparation

### 0.1 Start everything
```powershell
cd "H:\Téléchargements\Bawsla"
git switch feature/user-ia
docker compose up -d
docker compose ps          # wait until the 8 services show "healthy" (1–2 min)
docker start bawsla-phpmyadmin
```
If you changed code since the last build, rebuild first:
```powershell
docker compose up -d --build user-service ai-service
docker build --network=host -t bawsla/frontend frontend     # the front (avoids the Google Fonts hang)
docker compose up -d --no-build frontend
```

| Address | What |
|---|---|
| http://localhost:4200 | The app |
| http://localhost:8090 | phpMyAdmin (database) |
| http://localhost:8761 | Eureka (services list) |

> In the browser, press **Ctrl + F5** once to load the latest version of the front.
> After a restart of `ai-service`, the **first AI call takes 30 s to 2 min** (the models retrain). It is normal.

### 0.2 Accounts to use
| Role | Email | Password | How to get it |
|---|---|---|---|
| Admin | `admin@bawsla.tn` | `Admin123!` | Created at startup |
| Learner A | `lina@test.com` | `Password1` | Test 1.1 |
| Trainer B | `imen@test.com` | `Password1` | Test 1.4 |
| Learner C | `sami@test.com` | `Password1` | Test 4.2 |
| Trainer D | `rim@test.com` | `Password1` | Test 5.3 (created by the admin) |

> Tip: use a **private window** for a second account, so you can stay logged in as admin in the normal window.

---

## 1. Sign-up and login

| # | Steps | Expected |
|---|---|---|
| [ ] 1.1 | `/register` → **Je veux apprendre** → Lina Trabelsi, `lina@test.com`, `Password1` → **Créer mon compte** | Redirected to `/demarrer` (welcome questionnaire). Logged in. |
| [ ] 1.2 | Log out. `/register` → learner with password `aaaaaaaa` | Red message: **« Au moins une lettre et un chiffre »** |
| [ ] 1.3 | `/register` with `LINA@TEST.COM` (capitals) | **« Un compte existe déjà avec cet email »** |
| [ ] 1.4 | `/register` → **Je veux enseigner** → Imen Ayari, `imen@test.com`, `Password1`, expertise « Bases de données », presentation, link → **Envoyer ma demande** | Redirected to `/studio`. A trainer request is created (checked in 4.1). |
| [ ] 1.5 | `/login` with `lina@test.com` / `Password1` | Logged in, learner space `/app` |
| [ ] 1.6 | `/login` with a wrong password | **« Email ou mot de passe incorrect »** |
| [ ] 1.7 | `/login` with `inconnu@test.com` | **Same message** as 1.6 (nobody can guess which emails exist) |
| [ ] 1.8 | **Brute force:** 5 wrong passwords in a row for Lina, then the **right** one | **« Trop de tentatives échouées. Réessaie dans 15 minutes »**, even with the right password |
| [ ] 1.9 | Unlock Lina: Admin → Utilisateurs → ⋮ on Lina → **Réinitialiser le mot de passe** → `Password2` | Lina can log in with `Password2` (the reset also unlocks). |
| [ ] 1.10 | Admin → Utilisateurs → ⋮ on Lina → **Désactiver**. Then log in as Lina | **« Compte désactivé »**. Reactivate her afterwards (⋮ → **Réactiver**). |
| [ ] 1.11 | As Lina, type `/admin` in the address bar | Sent back to her space (no access to the admin area) |

---

## 2. Welcome questionnaire

| # | Steps | Expected |
|---|---|---|
| [ ] 2.1 | Right after 1.1 (or `/demarrer` as Lina): choose a profile, an objective, **2 fields** (e.g. Données, Web), a study time → **Voir mes recommandations** | Redirected to `/app` |
| [ ] 2.2 | Avatar (top right) → **Mon compte** → tab **Mon parcours** | The 4 answers of 2.1 are shown (saved **on the server**, not only in the browser) |
| [ ] 2.3 | phpMyAdmin → `bawsla_user` → `users` → Lina's row | Columns `parcours_profil`, `parcours_domaines` (JSON), `parcours_temps` filled |

---

## 3. My account

As Lina → avatar → **Mon compte** (`/app/profil`).

| # | Steps | Expected |
|---|---|---|
| [ ] 3.1 | Left card | Name, email, « Apprenant », « Membre depuis octobre 2026 », last login |
| [ ] 3.2 | **Informations**: change the first name, write a bio (watch the counter /500) → **Enregistrer** | « Informations enregistrées ». Left card and header updated. |
| [ ] 3.3 | Empty the first name | **Enregistrer** stays disabled / « Le prénom est obligatoire » |
| [ ] 3.4 | **Photo**: drop a PNG or JPG (< 2 MB) | « Photo enregistrée », the photo replaces the initials |
| [ ] 3.5 | Drop a `.txt` file renamed `.png` | **« Format accepté : PNG, JPEG ou WebP »** (the server checks the real file content) |
| [ ] 3.6 | Drop an image > 2 MB | **« Image trop lourde : 2 Mo maximum »** |
| [ ] 3.7 | **Retirer la photo** | Back to the initials |
| [ ] 3.8 | **Mon parcours**: change the fields → **Enregistrer** | « Parcours enregistré » |
| [ ] 3.9 | **Sécurité**: wrong current password + valid new one + confirmation | **« Mot de passe actuel incorrect »** and you **stay logged in** |
| [ ] 3.10 | New ≠ confirmation | **« Les deux mots de passe ne sont pas identiques. »** |
| [ ] 3.11 | Same password as the current one | **« Le nouveau mot de passe doit être différent de l'actuel »** |
| [ ] 3.12 | Correct change (`Password2` → `Password3`), log out, log in with `Password3` | Works; the old password is refused |
| [ ] 3.13 | **Préférences**: Sombre / Clair | The theme changes |

**Delete my account** (use a throwaway account, e.g. register `jetable@test.com`):

| # | Steps | Expected |
|---|---|---|
| [ ] 3.14 | **Sécurité** → **Supprimer mon compte** → wrong password → **Supprimer définitivement mon accès** | **« Mot de passe incorrect »**, still logged in |
| [ ] 3.15 | Right password → confirm | Logged out, back to `/bienvenue`. Logging in again: **« Compte désactivé »** |
| [ ] 3.16 | phpMyAdmin → `users` | The row still exists with `actif = 0` (deactivated, not erased) |

---

## 4. Trainer requests

| # | Steps | Expected |
|---|---|---|
| [ ] 4.1 | As Imen (1.4) → **Mon compte** (`/studio/profil`) → tab **Profil de formateur** | « Ta demande est en cours d'examen », expertise and link shown; left card « Validation en cours » |
| [ ] 4.2 | Register Sami as a **learner** (`sami@test.com`). **Mon compte** → tab **Devenir formateur** → submit with an **empty** expertise | « Indique au moins un domaine. » |
| [ ] 4.3 | Submit with expertise « Data science » | « Demande envoyée », status « en cours d'examen » |
| [ ] 4.4 | Admin → **Formateurs** (`/admin/formateurs`) | 2 requests **En attente** (Imen, Sami), real counters |
| [ ] 4.5 | On Sami → **Refuser** without a reason | **Confirmer le refus** stays disabled |
| [ ] 4.6 | Write a reason → **Confirmer le refus** | « Demande de Sami … refusée », moved to the **Traitées** tab with the reason |
| [ ] 4.7 | As Sami → **Devenir formateur** | « … n'a pas été acceptée », **the reason** shown, form available again |
| [ ] 4.8 | Sami submits a new request; admin **Accepte** it | Sami becomes **Formateur** (log out / in): his space is now `/studio`, « Formateur validé » |
| [ ] 4.9 | Admin **Accepte** Imen | Imen's **Profil de formateur** tab: « Tu es formateur validé » |
| [ ] 4.10 | Admin → Utilisateurs | A trainer not yet validated shows « non validé » next to the role |

---

## 5. Admin: users

As admin → **Utilisateurs** (`/admin/utilisateurs`).

| # | Steps | Expected |
|---|---|---|
| [ ] 5.1 | Open the page | Real accounts (newest first), real counters, photos when set, your row marked « (toi) » |
| [ ] 5.2 | Filters: **Apprenant**, **Formateur**, status **Désactivés**, search « lina » | The list filters correctly; sort by clicking the column headers |
| [ ] 5.3 | **Ajouter** → Rim Gafsi, `rim@test.com`, password `abc` | **Enregistrer** disabled (weak password) |
| [ ] 5.4 | Same with `Password1`, role **Formateur** → **Enregistrer** | « Compte créé ». Rim can log in; a trainer created by the admin is **validated** directly |
| [ ] 5.5 | Ajouter with an existing email | « Un compte existe déjà avec cet email » |
| [ ] 5.6 | ⋮ on Rim → **Modifier** → role **Apprenant** → Enregistrer | Role changed |
| [ ] 5.7 | Select 2 accounts (checkboxes) → **Désactiver**, then **Activer** | Both change status; the table updates immediately |
| [ ] 5.8 | ⋮ on **your own** row | No « Désactiver » option |
| [ ] 5.9 | ⋮ on your row → **Modifier** | Role and « Compte actif » are locked (« Tu ne peux pas modifier ton propre rôle ») |

---

## 6. AI: Admin → Supervision IA

As admin → **Supervision IA** (`/admin/ia`). The first load can take up to 2 min (training).

| # | Steps | Expected |
|---|---|---|
| [ ] 6.1 | Open the page | Orange banner **« Données simulées »** with the date, duration, Python and scikit-learn versions |
| [ ] 6.2 | The 4 KPIs | Accuracy **82,5 %**, recall **69 %**, AUC **0,81**, recommendation top 3 **64 %** |
| [ ] 6.3 | **Comparaison avec deux références** | 3 models × 3 bars: Random Forest ≥ régression logistique > classe majoritaire |
| [ ] 6.4 | **Matrice de confusion** | 22 / 2 (fausses alertes) / 5 (ratés) / 11 (repérés), seuil 0,4 |
| [ ] 6.5 | **Variables les plus importantes** | Horizontal bars; « Note moyenne aux quiz » near the top |
| [ ] 6.6 | **Profils : K-Means** | Elbow and silhouette curves (k = 2 to 8); table of **3 groups** (Décrocheur, Régulier, Débutant en difficulté) with sizes and failure rates |
| [ ] 6.7 | **Recommandation** | 3 methods; « Hybride (retenu) » is the highest |
| [ ] 6.8 | **Réentraîner** | Button shows « Entraînement en cours… », then « Modèles réentraînés en … s »; the date in the banner changes |
| [ ] 6.9 | As a learner or trainer, open `/admin/ia` | No access |

---

## 7. AI: learner → Progrès

As Lina (learner with fields of interest from 2.1) → **Progrès** (`/app/progres`).

**Preparation:** as a validated trainer (Imen), create 3 courses (Studio → Mes cours), for example: « SQL pour débutants » (Données, débutant), « Optimiser ses requêtes » (Données, avancé), « Introduction à React » (Web, débutant). Publish them if the Cours module asks for it.

| # | Steps | Expected |
|---|---|---|
| [ ] 7.1 | Open the page | 4 KPIs: **Jours actifs ≥ 1**; the 3 others show **—** (data from Quiz and Cours not available yet) |
| [ ] 7.2 | **Cours recommandés pour toi** | Up to 3 **real** courses, those in Lina's fields first, each with a **reason** (« Dans un domaine qui t'intéresse : … », « Nouveau dans le catalogue ») and the note « données simulées » |
| [ ] 7.3 | **Voir** on a course | Opens the course page `/app/explorer/{id}` |
| [ ] 7.4 | **Ton profil d'apprentissage** | « Pas encore assez de données… » (no prediction on incomplete data) |
| [ ] 7.5 | **Ce que ta boussole prend en compte** | Quiz results / notions / course progress: « Bientôt disponible »; activity: « Pris en compte » |
| [ ] 7.6 | Change Lina's fields (Mon compte → Mon parcours) to « Web », reload Progrès | « Introduction à React » moves up with « Dans un domaine qui t'intéresse : Web » |
| [ ] 7.7 | Search the page for the word « risque » | **Never shown** to a learner |

---

## 8. AI: trainer → Apprenants

| # | Steps | Expected |
|---|---|---|
| [ ] 8.1 | As Imen (with courses) → **Apprenants** (`/studio/apprenants`) | « Mes cours » = her number of courses; message **« La liste des inscrits arrivera avec les inscriptions aux cours (cours-service, contrat 7) »** |
| [ ] 8.2 | As Rim (trainer **without** courses) → Apprenants | **« Tu n'as pas encore de cours. »** |
| [ ] 8.3 | As a learner, call the API (see 9.6) | **403** |

> The full table (risk, reasons, side panel) appears only once Cours delivers contract 7. It was checked with a simulated response in the test browser (journal, entry 16).

---

## 9. AI: API tests (PowerShell)

Paste this once in the PowerShell terminal (VS Code):
```powershell
$G = "http://127.0.0.1:8080/api"
function Login($email, $mdp) {
  $r = Invoke-RestMethod -Method Post "$G/auth/login" -ContentType "application/json" -Body (@{ email = $email; motDePasse = $mdp } | ConvertTo-Json)
  @{ Authorization = "Bearer $($r.data.token)" }
}
function Code($bloc) { try { & $bloc | Out-Null; 200 } catch { $_.Exception.Response.StatusCode.value__ } }
$ADMIN = Login "admin@bawsla.tn" "Admin123!"
$APP   = Login "lina@test.com" "Password3"      # Lina's current password
$FORM  = Login "imen@test.com" "Password1"
$OK = '{"noteMoyenne":0.86,"tauxReussite":0.85,"nbQuiz":9,"nbNotionsRatees":1,"nbErreurs":1,"progressionMoyenne":0.8,"tempsPasseMinutes":1300,"nbCoursSuivis":4,"nbCoursTermines":1,"joursActifs30j":18,"joursDepuisDerniereConnexion":1}'
$KO = '{"noteMoyenne":0.35,"tauxReussite":0.33,"nbQuiz":4,"nbNotionsRatees":7,"nbErreurs":15,"progressionMoyenne":0.2,"tempsPasseMinutes":90,"nbCoursSuivis":1,"nbCoursTermines":0,"joursActifs30j":2,"joursDepuisDerniereConnexion":40}'
```

> PowerShell 5.1 shows accents badly (`RÃ©gulier` instead of `Régulier`): only a display problem of the terminal, the data are correct (check in the browser).

| # | Command | Expected |
|---|---|---|
| [ ] 9.1 | `(Invoke-RestMethod -Method Post "$G/ai/user/profil" -Headers $APP -ContentType "application/json" -Body "{`"apprenants`":[$OK,$KO]}").data.groupes` | 2 groups, the 2nd is **Décrocheur** |
| [ ] 9.2 | `(Invoke-RestMethod -Method Post "$G/ai/user/risque" -Headers $FORM -ContentType "application/json" -Body "{`"apprenants`":[$OK,$KO]}").data.resultats` | 1st: `faible`, no reasons; 2nd: `eleve` (≈ 90 %), 3 reasons |
| [ ] 9.3 | `Code { Invoke-RestMethod -Method Post "$G/ai/user/risque" -Headers $APP -ContentType "application/json" -Body "{`"apprenants`":[$KO]}" }` | **403** (a learner never gets a risk) |
| [ ] 9.4 | `Code { Invoke-RestMethod -Method Post "$G/ai/user/recommandations" -Headers $APP -ContentType "application/json" -Body '{"etudiantId":3}' }` | **400** (no identifier accepted) |
| [ ] 9.5 | `Code { Invoke-RestMethod -Method Post "$G/ai/user/profil" -Headers $APP -ContentType "application/json" -Body '{"apprenants":[{"noteMoyenne":1.5}]}' }` | **400** (value out of range / missing fields) |
| [ ] 9.6 | `Code { Invoke-RestMethod "$G/users/formateur/apprenants" -Headers $APP }` | **403** |
| [ ] 9.7 | `(Invoke-RestMethod "$G/users/me/indicateurs" -Headers $APP).data.sources` | `tentatives` and `inscriptions`: `PAS_ENCORE_DISPONIBLE`; `activite`: `DISPONIBLE` |
| [ ] 9.8 | `(Invoke-RestMethod "$G/users/me/ia" -Headers $APP).data` | `recommandations` (real courses), `profilIndisponible`, **no** `risque` |
| [ ] 9.9 | `Code { Invoke-RestMethod "$G/ai/user/metriques" -Headers $FORM }` then with `$ADMIN` | **403** then **200** |
| [ ] 9.10 | `Code { Invoke-RestMethod -Method Post "$G/ai/user/entrainer" -Headers $APP }` | **403** (only the admin retrains) |

---

## 10. Security

| # | Command / action | Expected |
|---|---|---|
| [ ] 10.1 | `Code { Invoke-RestMethod "$G/users/me" }` (no token) | **401** |
| [ ] 10.2 | `Code { Invoke-RestMethod "$G/users" -Headers @{ "X-User-Id" = "1"; "X-User-Role" = "ADMIN" } }` (forged headers, no token) | **401** (the gateway ignores them) |
| [ ] 10.3 | `Code { Invoke-RestMethod "$G/users" -Headers $APP }` | **403** (admin list) |
| [ ] 10.4 | `Code { Invoke-RestMethod "$G/users/1/indicateurs" -Headers $APP }` | **403** (a learner can't read someone else's data) |
| [ ] 10.5 | `(Invoke-RestMethod "$G/users/2" -Headers $APP).data` | Public info only: `id`, `nom`, `prenom`, `role`, `formateurValide` — **never** the email |
| [ ] 10.6 | phpMyAdmin → `users.mot_de_passe` | Starts with `$2a$` (BCrypt), never readable |

---

## 11. Database checks (phpMyAdmin)

http://localhost:8090 → database **`bawsla_user`**.

| # | Table | Check |
|---|---|---|
| [ ] 11.1 | `users` | Columns `derniere_connexion`, `tentatives_echouees`, `verrouille_jusqua`, `formateur_valide`, `avatar_version`, `bio`, `parcours_*` |
| [ ] 11.2 | `activites_jour` | One row per user and per day of login (no duplicates on the same day) |
| [ ] 11.3 | `demandes_formateur` | Requests of 4.x with `statut` (`EN_ATTENTE` / `ACCEPTEE` / `REFUSEE`), `motif_refus`, `date_traitement` |
| [ ] 11.4 | `avatars` | One row per photo (`type_mime` image/png or image/jpeg) |

---

## 12. Automated tests

```powershell
cd "H:\Téléchargements\Bawsla\backend"
.\mvnw -q -pl user-service test        # nothing printed = all passed (85 tests)

cd "H:\Téléchargements\Bawsla\ai-service"
.venv\Scripts\python -m pytest -q tests/user   # 75 passed (≈ 2–3 min)
```
| # | Expected |
|---|---|
| [ ] 12.1 | Java: no error (details in `backend/user-service/target/surefire-reports/`) |
| [ ] 12.2 | Python: `75 passed` |
| [ ] 12.3 | Train the models by hand: `.venv\Scripts\python -m app.modules.user.ml.entrainement` → the 3 scores of 6.2 |

---

## 13. Known limits (not bugs)

| What you see | Why |
|---|---|
| « Données simulées » banner, `donneesSimulees: true` | Models trained on 200 simulated learners (allowed by the subject, to be stated in the report) |
| « — » in Progrès, « Bientôt disponible », « Pas encore assez de données » | Quiz (contract 6) and Cours (contracts 7 and 8) not delivered yet |
| Apprenants page with no table | Needs Cours contract 7 |
| `notionsRatees: STUB` | The Quiz version running in this Docker is older than `feature/quiz` |
| First AI call very slow after a restart | The models retrain inside the container (30 s – 2 min) |
| Header avatar still shows initials | The header belongs to the shared core (`core/layouts`), not changed |
| Badge « 3 » / « 4 » in the admin menu | Fixed numbers in the shared core, not changed |

---

## 14. Cleanup

In phpMyAdmin → `bawsla_user` → **SQL** tab (adapt the emails if you used others):
```sql
DELETE FROM demandes_formateur WHERE utilisateur_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@test.com') t);
DELETE FROM avatars WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@test.com') t);
DELETE FROM activites_jour WHERE user_id IN (SELECT id FROM (SELECT id FROM users WHERE email LIKE '%@test.com') t);
DELETE FROM users WHERE email LIKE '%@test.com';
```
Delete the test courses from Admin → Catalogue (or as their trainer).

---

**Report:** for each failed test, note the number, what you did and what you saw, and send it to me.
