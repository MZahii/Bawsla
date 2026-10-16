"""Tests de LLMClient.embed (Gemini, OpenAI, Groq, stub, routage) avec des SDK simulés : aucun appel réseau."""

import math
from types import SimpleNamespace

import pytest
from google.genai import errors

from app.core.config import Settings
from app.core.errors import LLMInvalidResponseError, LLMMissingKeyError, LLMQuotaError, LLMTimeoutError
from app.core.llm_client import (
    GeminiLLMClient,
    LLMClient,
    LLMRoutage,
    OpenAILLMClient,
    StubLLMClient,
    build_groq_client,
    build_llm_client,
    build_openai_client,
)


def norme(v):
    return math.sqrt(sum(x * x for x in v))


def cosinus(a, b):
    return sum(x * y for x, y in zip(a, b))


class FauxEmbedGemini:
    def __init__(self, *scenario):
        self.scenario = list(scenario)
        self.appels = []

    async def embed_content(self, *, model, contents, config):
        self.appels.append({"model": model, "contents": contents, "config": config})
        element = self.scenario.pop(0)
        if isinstance(element, Exception):
            raise element
        if element == "un-par-texte":
            return SimpleNamespace(embeddings=[SimpleNamespace(values=[3.0, 4.0]) for _ in contents])
        return element


def gemini(*scenario, **options):
    modeles = FauxEmbedGemini(*scenario)
    client = SimpleNamespace(aio=SimpleNamespace(models=modeles))
    return GeminiLLMClient("cle", "gemini-test", client=client, attente_entre_tentatives=0, **options), modeles


class FauxEmbedOpenAI:
    def __init__(self, *scenario):
        self.scenario = list(scenario)
        self.appels = []

    async def create(self, *, model, input, dimensions):
        self.appels.append({"model": model, "input": input, "dimensions": dimensions})
        element = self.scenario.pop(0)
        if isinstance(element, Exception):
            raise element
        # Dans le désordre, comme le permet l'API : le client doit trier par index.
        donnees = [SimpleNamespace(index=i, embedding=[0.0, 2.0]) for i in range(len(input))]
        return SimpleNamespace(data=list(reversed(donnees)))


# ---------------------------------------------------------------- Stub


async def test_le_stub_est_deterministe_normalise_et_rapproche_les_textes_qui_partagent_des_mots():
    stub = StubLLMClient()
    r = await stub.embed(["Pourquoi mon LEFT JOIN renvoie trop de lignes",
                          "LEFT JOIN renvoie trop de lignes, pourquoi ?",
                          "Recette de la pâte à crêpes"], module="forum")
    assert r.modele == "stub-hachage-768" and r.dimension == 768 and r.stub and r.fournisseur == "stub"
    assert all(abs(norme(v) - 1) < 1e-9 for v in r.vecteurs)
    assert cosinus(r.vecteurs[0], r.vecteurs[1]) > 0.7 > cosinus(r.vecteurs[0], r.vecteurs[2])
    encore = await stub.embed(["Pourquoi mon LEFT JOIN renvoie trop de lignes"], module="forum")
    assert encore.vecteurs[0] == r.vecteurs[0]


async def test_le_stub_accepte_un_texte_vide():
    r = await StubLLMClient().embed([""], module="forum")
    assert r.vecteurs[0] == [0.0] * 768


# ---------------------------------------------------------------- Gemini


async def test_gemini_envoie_un_content_par_texte_avec_la_tache_et_la_dimension_puis_normalise():
    client, modeles = gemini("un-par-texte")
    r = await client.embed(["a", "b"], module="forum", tache="REQUETE")
    appel = modeles.appels[0]
    assert appel["model"] == "gemini-embedding-001"
    assert len(appel["contents"]) == 2 and appel["contents"][0].parts[0].text == "a"
    assert appel["config"].task_type == "RETRIEVAL_QUERY"
    assert appel["config"].output_dimensionality == 768
    assert r.vecteurs == [[0.6, 0.8], [0.6, 0.8]]
    assert r.modele == "gemini-embedding-001" and r.fournisseur == "gemini" and r.dimension == 2


async def test_gemini_document_par_defaut_et_lots_de_cent():
    client, modeles = gemini("un-par-texte", "un-par-texte")
    r = await client.embed([f"t{i}" for i in range(150)], module="forum")
    assert [len(a["contents"]) for a in modeles.appels] == [100, 50]
    assert modeles.appels[0]["config"].task_type == "RETRIEVAL_DOCUMENT"
    assert len(r.vecteurs) == 150


async def test_gemini_liste_vide_sans_appel():
    client, modeles = gemini()
    r = await client.embed([], module="forum")
    assert r.vecteurs == [] and modeles.appels == []


async def test_gemini_refuse_une_reponse_incomplete():
    client, _ = gemini(SimpleNamespace(embeddings=[SimpleNamespace(values=[1.0])]))
    with pytest.raises(LLMInvalidResponseError):
        await client.embed(["a", "b"], module="forum")


async def test_gemini_convertit_le_quota_et_relance_les_erreurs_passageres():
    client, _ = gemini(errors.ClientError(429, {"error": {"code": 429, "message": "quota", "status": "RESOURCE_EXHAUSTED"}}))
    with pytest.raises(LLMQuotaError):
        await client.embed(["a"], module="forum")
    surcharge = errors.ServerError(503, {"error": {"code": 503, "message": "surcharge", "status": "UNAVAILABLE"}})
    client, modeles = gemini(surcharge, "un-par-texte")
    r = await client.embed(["a"], module="forum")
    assert len(modeles.appels) == 2 and r.vecteurs == [[0.6, 0.8]]


async def test_gemini_sans_modele_de_vectorisation():
    client, _ = gemini(embedding_model=None)
    with pytest.raises(LLMMissingKeyError):
        await client.embed(["a"], module="forum")


# ---------------------------------------------------------------- OpenAI et Groq


async def test_openai_trie_par_index_normalise_et_transmet_la_dimension():
    faux = FauxEmbedOpenAI("ok")
    client = OpenAILLMClient("cle", "gpt", client=SimpleNamespace(embeddings=faux),
                             embedding_model="text-embedding-3-small", embedding_dimension=768)
    r = await client.embed(["a", "b"], module="forum")
    assert faux.appels[0] == {"model": "text-embedding-3-small", "input": ["a", "b"], "dimensions": 768}
    assert r.vecteurs == [[0.0, 1.0], [0.0, 1.0]] and r.fournisseur == "openai"


async def test_openai_convertit_le_delai_depasse():
    client = OpenAILLMClient("cle", "gpt", client=SimpleNamespace(embeddings=FauxEmbedOpenAI(TimeoutError())),
                             embedding_model="text-embedding-3-small")
    with pytest.raises(LLMTimeoutError):
        await client.embed(["a"], module="forum")


async def test_groq_n_a_pas_de_vectorisation():
    groq = build_groq_client(Settings(_env_file=None, groq_api_key="cle"))
    with pytest.raises(LLMMissingKeyError):
        await groq.embed(["a"], module="forum")
    openai = build_openai_client(Settings(_env_file=None, openai_api_key="cle"))
    assert openai._embedding_model == "text-embedding-3-small"


# ---------------------------------------------------------------- Routage et configuration


async def test_le_routage_bascule_sur_le_secours_et_annonce_son_modele():
    principal, _ = gemini(errors.ClientError(429, {"error": {"code": 429, "message": "q", "status": "RESOURCE_EXHAUSTED"}}))
    secours = OpenAILLMClient("cle", "gpt", client=SimpleNamespace(embeddings=FauxEmbedOpenAI("ok")),
                              embedding_model="text-embedding-3-small")
    r = await LLMRoutage(principal, secours).embed(["a"], module="forum", tache="REQUETE")
    assert r.fournisseur == "openai" and r.modele == "text-embedding-3-small"


async def test_un_client_sans_vectorisation_le_dit():
    class SansEmbed(StubLLMClient):
        embed = LLMClient.embed

    with pytest.raises(LLMMissingKeyError):
        await SansEmbed().embed(["a"], module="forum")


def test_la_configuration_choisit_le_modele_et_la_dimension():
    reglages = Settings(_env_file=None, llm_provider="gemini", llm_api_key="cle", llm_model="gemini-test",
                        llm_embedding_model="gemini-embedding-2", llm_embedding_dimension=1536)
    client = build_llm_client(reglages)
    assert client._embedding_model == "gemini-embedding-2" and client._embedding_dimension == 1536
