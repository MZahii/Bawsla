"""Aides communes aux tests du module Quiz : environnement isolé et faux LLM (aucun appel réseau)."""

import re

import pytest
from fastapi.testclient import TestClient

from app.core import llm_client as socle_llm
from app.core.config import get_settings
from app.core.llm_client import LLMClient, LLMMessage, LLMResult, get_llm_client
from app.main import app
from app.modules.quiz.config import get_quiz_ai_settings
from app.modules.quiz.schemas import GenerationLlm, VerificationLlm

UTILISATEUR = {"X-User-Id": "7", "X-User-Role": "ENSEIGNANT"}

DEMANDE = {
    "coursId": 3,
    "theme": "Jointures SQL",
    "competences": ["Écrire une jointure interne", "Choisir entre INNER JOIN et LEFT JOIN"],
    "niveauxBloom": ["APPLIQUER", "ANALYSER"],
    "difficulte": "MOYEN",
    "nbQuestions": 2,
}


def question(
    n: int = 1,
    *,
    niveau: str = "APPLIQUER",
    competence: str = "Écrire une jointure interne",
    enonce: str | None = None,
    choix: list[dict] | None = None,
    explication: str = "Parce que la jointure interne ne garde que les correspondances.",
) -> dict:
    """Une question bien formée au format JSON du LLM (camelCase)."""
    if choix is None:
        choix = [
            {"texte": f"Bonne réponse {n}", "estCorrecte": True},
            {"texte": f"Distracteur {n}-1", "estCorrecte": False, "erreurAssociee": "Confond INNER et LEFT"},
            {"texte": f"Distracteur {n}-2", "estCorrecte": False, "erreurAssociee": "Oublie la clause ON"},
            {"texte": f"Distracteur {n}-3", "estCorrecte": False, "erreurAssociee": "Filtre dans le WHERE"},
        ]
    return {
        "enonce": enonce or f"Question {n} sur les jointures ?",
        "niveauBloom": niveau,
        "competence": competence,
        "choix": choix,
        "explication": explication,
    }


def generation(*questions: dict) -> GenerationLlm:
    return GenerationLlm.model_validate({"questions": list(questions)})


_BLOC = re.compile(r"^(\d+)\. (.*)$")
_CHOIX = re.compile(r"^\s+([ABCD])\. (.*)$")


def lire_qcm(messages: list[LLMMessage]) -> dict[int, tuple[str, dict[str, str]]]:
    """Relit le QCM envoyé à la vérification : {numero: (enonce, {lettre: texte})}."""
    qcm: dict[int, tuple[str, dict[str, str]]] = {}
    numero = 0
    for ligne in messages[-1].content.splitlines():
        if m := _BLOC.match(ligne):
            numero = int(m.group(1))
            qcm[numero] = (m.group(2), {})
        elif (m := _CHOIX.match(ligne)) and numero:
            qcm[numero][1][m.group(1)] = m.group(2)
    return qcm


def repondre(fausses: set[str] = frozenset(), sans_reponse: set[str] = frozenset()):
    """Vérificateur simulé : choisit « Bonne réponse … » sauf pour les énoncés de `fausses` (premier distracteur)."""

    def verifier(messages: list[LLMMessage]) -> VerificationLlm:
        reponses = []
        for numero, (enonce, choix) in lire_qcm(messages).items():
            if enonce in sans_reponse:
                continue
            cible = "Distracteur" if enonce in fausses else "Bonne réponse"
            lettre = next(l for l, t in choix.items() if t.startswith(cible))
            reponses.append({"numero": numero, "choix": lettre, "justification": "Raisonnement court."})
        return VerificationLlm.model_validate({"reponses": reponses})

    return verifier


class FauxLLM(LLMClient):
    """Rejoue des réponses `complete_json` dans l'ordre : objet, exception levée, ou fonction(messages).

    `fournisseur` simule le fournisseur qui répond (comme LLMRoutage, via fournisseur_courant()).
    """

    def __init__(self, *reponses, fournisseur: str | None = None):
        self.reponses = list(reponses)
        self.appels: list[dict] = []
        self.fournisseur = fournisseur

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="texte", model="faux")

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="image", model="faux")

    async def synthetiser(self, texte, voix, langue):
        raise NotImplementedError

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        raise NotImplementedError

    async def complete_json(self, messages: list[LLMMessage], schema, *, module, temperature=0.0, max_tokens=2048):
        self.appels.append(
            {"messages": messages, "schema": schema, "temperature": temperature, "module": module, "max_tokens": max_tokens}
        )
        element = self.reponses.pop(0)
        if isinstance(element, Exception):
            raise element
        if callable(element):
            element = element(messages)
        if isinstance(element, dict):  # JSON brut : validé par le schéma, comme le fait complete_json
            element = schema.model_validate(element)
        if self.fournisseur:
            socle_llm._fournisseur_utilise.set(self.fournisseur)
        return element

    def texte_envoye(self) -> str:
        return "\n".join(m.content for appel in self.appels for m in appel["messages"])


@pytest.fixture(autouse=True)
def environnement(monkeypatch):
    """Isole des vrais réglages (.env du dépôt) : fournisseur stub, budget et lots par défaut."""
    monkeypatch.setenv("LLM_PROVIDER", "stub")
    monkeypatch.setenv("LLM_API_KEY", "")
    monkeypatch.setenv("LLM_MODEL", "modele-test")
    monkeypatch.setenv("LLM_SECOURS", "")
    monkeypatch.setenv("GROQ_TOKENS_PAR_MINUTE", "8000")
    monkeypatch.setenv("GROQ_MARGE_REFLEXION", "400")
    monkeypatch.setenv("PROMPT_QUIZ_VERSION", "v1")
    monkeypatch.setenv("QUIZ_LOT_MAX", "5")
    monkeypatch.setenv("QUIZ_BUDGET_TOKENS", "0")
    # Une seule passe de vérification par défaut : test_quiz_coherence couvre les passes multiples.
    monkeypatch.setenv("QUIZ_NB_VERIFICATIONS", "1")
    monkeypatch.setenv("QUIZ_TEMPERATURE_VERIFICATION", "0.7")
    for fabrique in (get_settings, get_quiz_ai_settings):
        fabrique.cache_clear()
    yield
    app.dependency_overrides.clear()
    for fabrique in (get_settings, get_quiz_ai_settings):
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
