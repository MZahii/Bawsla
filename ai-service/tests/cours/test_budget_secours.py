"""Budget de jetons de l'explication : sortie calculée sur la durée, et pages utiles seulement quand le secours (Groq) est limité."""

import logging

import pytest

from app.core.errors import LLMQuotaError, LLMTropLongError
from app.core.llm_client import LLMRoutage, OpenAILLMClient, estimer_tokens
from app.core.sanitizer import BasicSanitizer
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.explication.budget import calculer_max_tokens, selection_pour_secours, texte_du_cours
from app.modules.cours.explication.generation import generer_explication
from app.modules.cours.explication.schemas import ExplicationRequest, PageCours
from tests.core.test_openai_secours import FauxChat, Principal, reponse_chat
from tests.cours.explication_outils import EXTRAIT_A, PLAN, script_monologue

CLE_GROQ = "gsk-factice"


def groq(scenario, limite=8000) -> tuple[OpenAILLMClient, FauxChat]:
    faux = FauxChat(scenario)
    return OpenAILLMClient(CLE_GROQ, "openai/gpt-oss-120b", client=faux, nom="groq", max_retries=0,
                           limite_tokens_minute=limite, marge_reflexion=400), faux


def cours_long() -> list[PageCours]:
    """65 pages dont une seule parle des jointures : le texte total dépasse largement la limite de Groq."""
    pages = [PageCours(numero=n, texte=f"Page {n} : généralités sur l'organisation du cours et les rappels. " * 12) for n in range(1, 66)]
    pages[41] = PageCours(numero=42, texte=f"INNER JOIN : {EXTRAIT_A}. LEFT JOIN garde toutes les lignes de la table de gauche.")
    return pages


def requete(duree=2, pages=None) -> ExplicationRequest:
    return ExplicationRequest(cours_id=1, pages=pages or cours_long(), notions=PLAN, duree_minutes=duree)


def test_formule_du_max_tokens():
    # (375 mots x 1,6 + 14 segments x 80) x 1,10 = 1 892 -> 1 900 (2 minutes, 3 notions)
    assert calculer_max_tokens(375, 3) == 1900
    # 4 minutes (750 mots), 5 notions : (1 200 + 22 x 80) x 1,10 = 3 256 -> 3 300
    assert calculer_max_tokens(750, 5) == 3300
    assert calculer_max_tokens(10, 1) == 1200 and calculer_max_tokens(5000, 5) == 8000  # bornes


async def test_deux_minutes_passent_par_groq_avec_les_pages_utiles_seulement(caplog):
    caplog.set_level(logging.INFO)
    script = reponse_chat(script_monologue().model_dump_json(by_alias=True))
    secours, faux = groq([script, script])  # le script de test est trop long pour 2 minutes : une régénération
    llm = LLMRoutage(Principal(LLMQuotaError("Quota Gemini")), secours)

    rep = await generer_explication(llm, BasicSanitizer(20_000), requete(duree=2), CoursAiSettings(_env_file=None), "gpt")

    assert rep.fournisseur == "groq"
    envoye = faux.appels[0]
    assert envoye["max_completion_tokens"] == 1550 + 400  # plan de 2 notions : 10 segments  # sortie calculée sur la durée + marge de réflexion de Groq
    contenu = " ".join(m["content"] for m in envoye["messages"])
    assert "[Page 42]" in contenu and EXTRAIT_A in contenu  # la page du plan est gardée
    assert contenu.count("[Page ") < 65  # le reste du cours est coupé
    total = estimer_tokens(contenu) + envoye["max_completion_tokens"]
    assert total <= 8000 * 0.8 + 50  # 20 % de marge sous la limite par minute
    assert any(a.startswith("Cours tronqué pour le fournisseur de secours (groq)") for a in rep.alertes)
    assert sum(a.startswith("Cours tronqué") for a in rep.alertes) == 1
    assert "budget de jetons : texte=" in caplog.text and "prompt=" in caplog.text and "max_tokens=1550" in caplog.text
    assert "limite du secours groq : 8000" in caplog.text


async def test_gemini_repond_aucune_troncature_du_secours():
    secours, faux = groq([])
    llm = LLMRoutage(Principal(), secours)
    llm.principal.complete_json = None  # remplacé ci-dessous
    reponses = []

    async def complete_json(messages, schema, *, module, temperature=0.0, max_tokens=2048):
        reponses.append((messages, max_tokens))
        return script_monologue()

    llm.principal.complete_json = complete_json
    rep = await generer_explication(llm, BasicSanitizer(20_000), requete(duree=2), CoursAiSettings(_env_file=None), "gemini-test")
    assert faux.appels == [] and reponses[0][1] == 1550
    assert not any("secours" in a for a in rep.alertes)
    assert "[Page 65]" in " ".join(m.content for m in reponses[0][0])  # Gemini reçoit tout le cours (sous la limite de 60 000 caractères)


def test_selection_pour_secours_sans_place_leve_une_erreur():
    secours, _ = groq([], limite=1000)  # limite minuscule : le prompt seul la dépasse
    with pytest.raises(ValueError):
        selection_pour_secours(cours_long(), PLAN, secours, tokens_prompt=1200, max_tokens=1900)


async def test_duree_trop_longue_pour_le_secours_erreur_claire_sans_appel():
    secours, faux = groq([], limite=2500)
    llm = LLMRoutage(Principal(LLMQuotaError("Quota Gemini")), secours)
    with pytest.raises(LLMTropLongError, match="Cours trop long pour le fournisseur de secours"):
        await generer_explication(llm, BasicSanitizer(20_000), requete(duree=6), CoursAiSettings(_env_file=None), "gpt")
    assert faux.appels == []


def test_texte_du_cours_garde_les_intitules_de_page():
    assert texte_du_cours([PageCours(numero=3, texte="abc")]) == "[Page 3]\nabc"
