"""Limite d'appels IA par apprenant sur les routes du Forum (I9 : appel direct des routes en contournant le forum)."""

import pytest

from app.modules.forum.config import ForumAiSettings, get_forum_ai_settings
from app.modules.forum.limiteur import LimiteurAppels
from app.main import app

ENTETES = {"X-User-Id": "30", "X-User-Role": "ETUDIANT"}
URL = "/api/ai/forum/vectoriser"
CORPS = {"textes": ["Qu'est-ce qu'une boucle for ?"], "tache": "REQUETE"}


@pytest.fixture
def limite_3(client):
    app.dependency_overrides[get_forum_ai_settings] = lambda: ForumAiSettings(forum_appels_ia_par_minute=3)
    yield client


def test_apprenant_bloque_au_dela_de_la_limite(limite_3):
    for _ in range(3):
        assert limite_3.post(URL, headers=ENTETES, json=CORPS).status_code == 200
    r = limite_3.post(URL, headers=ENTETES, json=CORPS)
    assert r.status_code == 429
    corps = r.json()
    assert corps["success"] is False and corps["status"] == 429
    assert "Réessaie" in corps["message"]


def test_limite_par_utilisateur(limite_3):
    for _ in range(3):
        limite_3.post(URL, headers=ENTETES, json=CORPS)
    autre = {"X-User-Id": "31", "X-User-Role": "ETUDIANT"}
    assert limite_3.post(URL, headers=autre, json=CORPS).status_code == 200


@pytest.mark.parametrize("role", ["ENSEIGNANT", "ADMIN"])
def test_formateur_et_admin_non_limites(limite_3, role):
    entetes = {"X-User-Id": "5", "X-User-Role": role}
    for _ in range(5):
        assert limite_3.post(URL, headers=entetes, json=CORPS).status_code == 200


def test_fenetre_glissante_libere_les_appels():
    temps = [0.0]
    limiteur = LimiteurAppels(horloge=lambda: temps[0])
    assert limiteur.autoriser("30", 2) and limiteur.autoriser("30", 2)
    assert not limiteur.autoriser("30", 2)
    temps[0] = 59.0
    assert not limiteur.autoriser("30", 2)
    temps[0] = 60.0
    assert limiteur.autoriser("30", 2)


def test_limite_zero_desactive():
    limiteur = LimiteurAppels()
    assert all(limiteur.autoriser("30", 0) for _ in range(100))
