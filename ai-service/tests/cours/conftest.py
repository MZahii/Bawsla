"""Aides communes aux tests du module Cours : environnement isolé et faux LLM (aucun appel réseau)."""

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.core.llm_client import AudioResult, LLMClient, LLMMessage, LLMResult, Replique, get_llm_client
from app.main import app
from app.modules.cours.config import get_cours_ai_settings

UTILISATEUR = {"X-User-Id": "7", "X-User-Role": "ENSEIGNANT"}

COURS = (
    "Une jointure interne (INNER JOIN) ne garde que les lignes qui ont une correspondance dans les deux tables. "
    "Une jointure externe gauche (LEFT JOIN) garde toutes les lignes de la table de gauche, même sans correspondance.\n\n"
    "Pour un LEFT JOIN, le filtre sur la table de droite se place dans la clause ON, pas dans le WHERE. "
    "La clé primaire identifie chaque ligne de façon unique. Une table peut contenir 3 clés étrangères au maximum dans cet exemple.\n\n"
    "Contactez le formateur à prof@exemple.tn ou au 22 123 456 pour toute question."
)

EXTRAIT_INNER = "ne garde que les lignes qui ont une correspondance dans les deux tables"
EXTRAIT_LEFT = "garde toutes les lignes de la table de gauche, même sans correspondance"


class FauxLLM(LLMClient):
    """Rejoue des réponses `complete_json` dans l'ordre (ou lève l'exception donnée) et garde les messages reçus."""

    def __init__(self, *reponses):
        self.reponses = list(reponses)
        self.appels: list[dict] = []

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="résumé simple", model="faux")

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="image", model="faux")

    async def synthetiser(self, texte: str, voix: str, langue: str) -> AudioResult:
        return AudioResult(data=b"RIFF-faux-wav", mime_type="audio/wav", model="faux-tts")

    async def synthetiser_dialogue(self, repliques: list[Replique], voix_par_locuteur: dict[str, str], langue: str) -> AudioResult:
        return AudioResult(data=b"RIFF-faux-wav", mime_type="audio/wav", model="faux-tts")

    async def complete_json(self, messages: list[LLMMessage], schema, *, module, temperature=0.0, max_tokens=2048):
        self.appels.append({"messages": messages, "schema": schema, "temperature": temperature, "module": module})
        element = self.reponses.pop(0)
        if isinstance(element, Exception):
            raise element
        return element


@pytest.fixture(autouse=True)
def environnement(monkeypatch):
    """Isole des vrais réglages (.env du dépôt) : fournisseur stub, modèle de test, prompt par défaut."""
    monkeypatch.setenv("LLM_PROVIDER", "stub")
    monkeypatch.setenv("LLM_API_KEY", "")
    monkeypatch.setenv("LLM_MODEL", "modele-test")
    monkeypatch.setenv("PROMPT_FICHE_VERSION", "v3")
    # Chaîne vocale des tests de route : edge-tts seul (simulé). Aucune vraie clé, aucun appel réseau.
    monkeypatch.setenv("TTS_FOURNISSEURS", "edge")
    monkeypatch.setenv("ELEVENLABS_API_KEY", "")
    for fabrique in (get_settings, get_cours_ai_settings):
        fabrique.cache_clear()
    yield
    app.dependency_overrides.clear()
    for fabrique in (get_settings, get_cours_ai_settings):
        fabrique.cache_clear()


@pytest.fixture
def client():
    return TestClient(app)


@pytest.fixture
def avec_llm():
    """avec_llm(FauxLLM(...)) branche le faux LLM à la place du vrai."""

    def brancher(faux: FauxLLM) -> FauxLLM:
        app.dependency_overrides[get_llm_client] = lambda: faux
        return faux

    return brancher


# ---- Tests lents (vrais modèles Hugging Face) : exclus par défaut, activés avec RUN_SLOW=1 (pytest.ini est du socle).
def pytest_configure(config):
    config.addinivalue_line("markers", "slow: test lent (vrais modèles, téléchargements) ; exécuté seulement avec RUN_SLOW=1")


def pytest_collection_modifyitems(config, items):
    import os

    if os.environ.get("RUN_SLOW") == "1":
        return
    saut = pytest.mark.skip(reason="test lent : lance-le avec RUN_SLOW=1")
    for item in items:
        if "slow" in item.keywords:
            item.add_marker(saut)
