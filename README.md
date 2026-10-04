# Bawsla (البوصلة) — plateforme e-learning

Mini-projet ESPRIT 5SAE9 « IA for Software Engineering » (2026-2027).
Architecture microservices : **Angular → API Gateway → services Spring Boot (Eureka) → MySQL**, plus un **ai-service FastAPI**.

- [CLAUDE.md](CLAUDE.md) : règles du projet (stack, conventions, sécurité, propriété des fichiers). **À lire en premier.**
- [CONTRATS_API.md](CONTRATS_API.md) : contrats entre modules.
- [prompts_journal/](prompts_journal/) : journal des prompts IA de chaque membre.

## Prérequis

| Outil | Version |
|---|---|
| JDK | **21 ou plus** (le code cible Java 21 ; un JDK 23 compile aussi en `release 21`) |
| Maven | inutile, le wrapper `backend/mvnw` suffit |
| Node.js | 20.19+, 22.12+ ou 24+ (une version LTS paire) |
| Python | 3.11+ |
| Docker | Docker Desktop (pour MySQL) |

## Installation (une seule fois)

```bash
cp .env.example .env                       # puis changer JWT_SECRET (32 caractères minimum)

cd backend && ./mvnw -q package -DskipTests && cd ..

cd ai-service
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt     # Linux/Mac : .venv/bin/pip
cd ..

cd frontend && npm install && cd ..
```

Les services Spring lisent automatiquement le `.env` de la racine. ai-service le lit aussi.

## Ordre de lancement

Un terminal par composant, **dans cet ordre** :

| # | Composant | Commande | Prêt quand |
|---|---|---|---|
| 1 | MySQL | `docker compose up -d` | `docker compose ps` affiche `healthy` |
| 2 | Eureka | `cd backend/eureka-server && ../mvnw spring-boot:run` | http://localhost:8761 répond |
| 3 | Gateway | `cd backend/api-gateway && ../mvnw spring-boot:run` | port 8080 ouvert |
| 4 | Services | `cd backend/user-service && ../mvnw spring-boot:run`, idem pour `cours-service`, `quiz-service` et `forum-service` | chaque service apparaît sur le tableau Eureka |
| 5 | ai-service | `cd ai-service && .venv/Scripts/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload` | http://127.0.0.1:8000/docs |
| 6 | Front | `cd frontend && npx ng serve` | http://localhost:4200 |

Variante sans Maven à chaque fois : `java -jar backend/<svc>/target/<svc>-0.1.0-SNAPSHOT.jar`, lancé **depuis le dossier du service** pour que le `.env` soit trouvé.

> **Note :** la gateway met jusqu'à 30 s à découvrir un service qui vient de démarrer. Un `503` juste après le lancement est normal : réessayer.
>
> **Windows :** utiliser `127.0.0.1` plutôt que `localhost` pour les appels en ligne de commande. `localhost` tente d'abord l'IPv6 et ajoute environ 2 s par requête vers uvicorn.
>
> **Machine juste en RAM :** ajouter `-Xmx256m` aux `java -jar` (6 JVM tournent en parallèle).

Au premier démarrage, user-service crée le compte **ADMIN** défini dans `.env` (`ADMIN_EMAIL` / `ADMIN_PASSWORD`, par défaut `admin@bawsla.tn` / `Admin123!`).

## Tester de bout en bout

### Par l'interface

1. Ouvrir http://localhost:4200 et créer un compte **Enseignant**.
2. Le menu latéral affiche *Cours / Quiz / Forum*. *Utilisateurs* n'apparaît que pour l'ADMIN.
3. Se déconnecter, puis se connecter avec le compte ADMIN : le menu *Utilisateurs* liste les comptes.

### En ligne de commande (bash / Git Bash)

```bash
G=http://127.0.0.1:8080/api

# 1. Inscription + connexion d'un enseignant
curl -s -X POST $G/auth/register -H 'Content-Type: application/json' \
  -d '{"nom":"Ben Ali","prenom":"Sami","email":"prof@esprit.tn","motDePasse":"Password1","role":"ENSEIGNANT"}'
TOKEN=$(curl -s -X POST $G/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"prof@esprit.tn","motDePasse":"Password1"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

# 2. Profil (le JWT est validé par la gateway, user-service lit X-User-Id)
curl -s $G/users/me -H "Authorization: Bearer $TOKEN"

# 3. CRUD cours : Front → Gateway → cours-service → MySQL (bawsla_cours)
curl -s -X POST $G/cours -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"titre":"Introduction à Spring Boot","categorie":"Backend","niveau":"DEBUTANT"}'
curl -s $G/cours -H "Authorization: Bearer $TOKEN"

# 4. Quiz : quiz-service vérifie le cours via Feign (GET /api/cours/{id})
curl -s -X POST $G/quiz -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"titre":"Quiz Spring","coursId":1}'                 # 201
curl -s -X POST $G/quiz -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"titre":"Quiz","coursId":999}'                      # 400 "Le cours 999 n'existe pas"

# 5. Forum
curl -s -X POST $G/forum/discussions -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"titre":"Question","contenu":"Comment configurer Eureka ?","coursId":1}'

# 6. IA (stub) via la gateway
curl -s -X POST $G/ai/cours/resume -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"coursId":1,"texte":"Mon cours..."}'

# 7. Sécurité : sans token → 401, étudiant qui crée un cours → 403
curl -s $G/users/me
```

## Structure

Voir la section « Structure » de [CLAUDE.md](CLAUDE.md). En résumé : `backend/` (Maven multi-module), `ai-service/` (FastAPI), `frontend/` (Angular) et `prompts_journal/`.

## État du socle

| Élément | État |
|---|---|
| Eureka, Gateway (routes, CORS, JWT, en-têtes X-User-*) | ✅ fonctionnel |
| user-service : inscription, connexion JWT, BCrypt, rôles, `/me`, liste ADMIN, `/users/{id}` public, ADMIN au démarrage | ✅ fonctionnel |
| Format de réponse et d'erreur commun, gestion globale des exceptions, health | ✅ fonctionnel (4 services + ai-service) |
| CRUD minimal Cours / Quiz / Discussion + Feign quiz → cours | ✅ fonctionnel (preuve de chaîne) |
| Front : login, register, intercepteur, guards, layout et menu par rôle, pages liste | ✅ fonctionnel |
| `GET /api/cours/{id}/texte` | 🟡 stub |
| `GET /api/quiz/notions-ratees` | 🟡 stub (liste vide) |
| ai-service : 4 routers, interface `LLMClient` + `Sanitizer` | 🟡 stubs, aucun appel LLM réel |

## Branches

`main` (stable) ← `develop` (intégration) ← `feature/user`, `feature/cours`, `feature/quiz`, `feature/forum`. Détails dans [CLAUDE.md](CLAUDE.md#git).
