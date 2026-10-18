"""Outils communs des tests du module Forum : application FastAPI avec un LLM simulé (aucun appel réseau)."""

import pytest
from fastapi.testclient import TestClient

from app.core.errors import LLMError
from app.core.llm_client import LLMMessage, StubLLMClient
from app.core.sanitizer import BasicSanitizer, get_sanitizer
from app.main import app
from app.modules.forum.limiteur import limiteur
from app.core.llm_client import get_llm_client

ENTETES = {"X-User-Id": "30", "X-User-Role": "ETUDIANT"}


class FauxLLM(StubLLMClient):
    """Vectorise comme le stub (déterministe) ; rejoue des réponses JSON (objets ou exceptions) dans l'ordre."""

    nom = "faux"

    def __init__(self, *reponses):
        self.reponses = list(reponses)
        self.messages: list[list[LLMMessage]] = []
        self.textes_vectorises: list[str] = []

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        self.messages.append(messages)
        reponse = self.reponses.pop(0)
        if isinstance(reponse, LLMError):
            raise reponse
        return schema.model_validate(reponse)

    async def embed(self, textes, *, module, tache="DOCUMENT"):
        self.textes_vectorises.extend(textes)
        return await super().embed(textes, module=module, tache=tache)


@pytest.fixture(autouse=True)
def limiteur_vide():
    """La limite d'appels IA est globale au processus : chaque test repart d'une fenêtre vide."""
    limiteur.vider()
    yield
    limiteur.vider()


@pytest.fixture
def faux_llm():
    return FauxLLM()


@pytest.fixture
def client(faux_llm):
    app.dependency_overrides[get_llm_client] = lambda: faux_llm
    app.dependency_overrides[get_sanitizer] = lambda: BasicSanitizer(20_000)
    with TestClient(app) as c:
        yield c
    app.dependency_overrides.clear()
