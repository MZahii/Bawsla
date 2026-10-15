"""Endpoints /api/ai/user/* : rôles, refus des données personnelles, formats de réponse."""

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.modules.user.ml import entrainement

ADMIN = {"X-User-Id": "1", "X-User-Role": "ADMIN"}
FORMATEUR = {"X-User-Id": "2", "X-User-Role": "ENSEIGNANT"}
APPRENANT = {"X-User-Id": "3", "X-User-Role": "ETUDIANT"}

A_L_AISE = {"noteMoyenne": 0.86, "tauxReussite": 0.85, "nbQuiz": 9, "nbNotionsRatees": 1, "nbErreurs": 1,
            "progressionMoyenne": 0.8, "tempsPasseMinutes": 1300, "nbCoursSuivis": 4, "nbCoursTermines": 1,
            "joursActifs30j": 18, "joursDepuisDerniereConnexion": 1}
EN_DIFFICULTE = A_L_AISE | {"noteMoyenne": 0.35, "tauxReussite": 0.33, "nbNotionsRatees": 7, "nbErreurs": 15,
                            "progressionMoyenne": 0.2, "tempsPasseMinutes": 90, "nbCoursSuivis": 1,
                            "nbCoursTermines": 0, "joursActifs30j": 2, "joursDepuisDerniereConnexion": 40}


@pytest.fixture(scope="module")
def client(tmp_path_factory):
    # Modèles entraînés une fois dans un dossier temporaire, puis servis depuis le cache mémoire.
    modeles = entrainement.entrainer_tout(tmp_path_factory.mktemp("modeles"))
    entrainement._cache = modeles
    yield TestClient(app)
    entrainement.vider_cache()


def test_sans_entetes_gateway_401(client):
    r = client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE]})
    assert r.status_code == 401
    assert r.json()["success"] is False


# ---------- Profil (K-Means) ----------

def test_profil_par_lot_dans_l_ordre(client):
    r = client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE, EN_DIFFICULTE]}, headers=APPRENANT)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["donneesSimulees"] is True
    assert [g["nom"] for g in data["groupes"]][1] == "Décrocheur"
    assert all(g["description"] for g in data["groupes"])


def test_donnee_personnelle_refusee(client):
    r = client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE | {"email": "a@b.com"}]}, headers=ADMIN)
    assert r.status_code == 400
    r = client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE], "etudiantId": 3}, headers=ADMIN)
    assert r.status_code == 400


def test_valeurs_hors_bornes_refusees(client):
    r = client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE | {"noteMoyenne": 1.5}]}, headers=ADMIN)
    assert r.status_code == 400
    assert "noteMoyenne" in " ".join(r.json()["details"])
    manquant = {k: v for k, v in A_L_AISE.items() if k != "joursActifs30j"}
    assert client.post("/api/ai/user/profil", json={"apprenants": [manquant]}, headers=ADMIN).status_code == 400


def test_lot_vide_ou_trop_grand_refuse(client):
    assert client.post("/api/ai/user/profil", json={"apprenants": []}, headers=ADMIN).status_code == 400
    assert client.post("/api/ai/user/profil", json={"apprenants": [A_L_AISE] * 501}, headers=ADMIN).status_code == 400


# ---------- Risque (Random Forest) ----------

def test_risque_interdit_a_l_apprenant(client):
    r = client.post("/api/ai/user/risque", json={"apprenants": [A_L_AISE]}, headers=APPRENANT)
    assert r.status_code == 403


def test_risque_pour_le_formateur(client):
    r = client.post("/api/ai/user/risque", json={"apprenants": [A_L_AISE, EN_DIFFICULTE]}, headers=FORMATEUR)
    assert r.status_code == 200
    data = r.json()["data"]
    faible, eleve = data["resultats"]
    assert faible["niveau"] == "faible" and faible["facteurs"] == []
    assert eleve["niveau"] == "eleve" and 1 <= len(eleve["facteurs"]) <= 3
    assert 0 < data["seuil"] < 1


# ---------- Recommandations ----------

def test_recommandations(client):
    corps = {"coursSuivis": [8], "domaines": ["Back-end"], "coursEnDifficulte": [8], "nombre": 3}
    r = client.post("/api/ai/user/recommandations", json=corps, headers=APPRENANT)
    assert r.status_code == 200
    recos = r.json()["data"]["recommandations"]
    assert len(recos) == 3
    assert recos[0]["coursId"] == 7 and "revoir les bases" in recos[0]["raison"]
    assert 8 not in [x["coursId"] for x in recos]
    assert {"coursId", "titre", "score", "raison"} <= set(recos[0])


def test_recommandations_demarrage_a_froid_et_catalogue(client):
    r = client.post("/api/ai/user/recommandations", json={"domaines": ["Web"], "catalogue": [5, 6], "nombre": 5},
                    headers=APPRENANT)
    assert [x["coursId"] for x in r.json()["data"]["recommandations"]] == [5, 6]


def test_recommandations_refusent_l_ancien_format(client):
    """L'ancien stub recevait etudiantId : il est désormais refusé (aucun identifiant)."""
    assert client.post("/api/ai/user/recommandations", json={"etudiantId": 3}, headers=APPRENANT).status_code == 400


# ---------- Métriques et entraînement (admin) ----------

def test_metriques_admin(client):
    assert client.get("/api/ai/user/metriques", headers=FORMATEUR).status_code == 403
    r = client.get("/api/ai/user/metriques", headers=ADMIN)
    assert r.status_code == 200
    met = r.json()["data"]
    assert met["donneesSimulees"] is True
    assert {"profils", "risque", "recommandation", "versions", "dateEntrainement"} <= set(met)


def test_entrainer_admin(client, monkeypatch):
    assert client.post("/api/ai/user/entrainer", headers=APPRENANT).status_code == 403
    actuels = entrainement._cache
    monkeypatch.setattr(entrainement, "reentrainer", lambda: actuels)
    r = client.post("/api/ai/user/entrainer", headers=ADMIN)
    assert r.status_code == 200
    assert r.json()["message"].startswith("Modèles réentraînés")


def test_recommandations_sur_le_vrai_catalogue(client):
    corps = {"domaines": ["Données"], "nombre": 2, "coursDisponibles": [
        {"coursId": 101, "titre": "SQL pour débutants", "categorie": "Données", "niveau": "DEBUTANT"},
        {"coursId": 103, "titre": "Introduction à React", "categorie": "Web", "niveau": "DEBUTANT"},
    ]}
    r = client.post("/api/ai/user/recommandations", json=corps, headers=APPRENANT)
    assert r.status_code == 200
    recos = r.json()["data"]["recommandations"]
    assert [x["coursId"] for x in recos] == [101, 103]
    assert recos[0]["titre"] == "SQL pour débutants"


def test_vrai_catalogue_refuse_les_champs_inattendus(client):
    corps = {"coursDisponibles": [{"coursId": 1, "titre": "x", "enseignantEmail": "a@b.com"}]}
    assert client.post("/api/ai/user/recommandations", json=corps, headers=APPRENANT).status_code == 400
