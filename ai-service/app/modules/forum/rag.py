"""Réponse sourcée de La Vigie (plan §4.2, étapes 4 à 6) et réponse générale à la demande (niveau 3).

Le modèle ne fait que proposer : tout ce qu'il renvoie est revérifié ici, SANS LLM.
- Chaque citation doit figurer dans le passage qu'elle cite (rapidfuzz, partial_ratio ≥ 90 par défaut) ;
  une citation introuvable est retirée, et les numéros [n] sans citation vérifiée sont retirés du texte.
- S'il ne reste aucune citation vérifiée, la réponse est rejetée et remplacée par l'abstention.
- La confiance (0 à 100) est CALCULÉE : 40 % meilleur score de récupération, 30 % part des phrases citées,
  30 % part des citations vérifiées. Jamais recopiée du modèle.
"""

import logging
import re

from rapidfuzz import fuzz

from app.core.llm_client import LLMClient, LLMMessage, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.forum import prompts
from app.modules.forum.config import ForumAiSettings
from app.modules.forum.schemas import (
    PassageContexte,
    RepondreGeneralRequest,
    RepondreGeneralResponse,
    RepondreRequest,
    RepondreResponse,
    ReponseGeneraleLLM,
    ReponseLLM,
    SourceReponse,
)
from app.modules.forum.securite import encadrer, preparer

MODULE = "forum"
ABSTENTION = "Le cours ne traite pas ce point. J'ai prévenu le formateur du cours."
CITATION_MIN = 12

_NUMERO = re.compile(r"\[(\d+)\]")
_PHRASES = re.compile(r"(?<=[.!?])\s+")
_GUILLEMETS = re.compile(r"[«»\"“”‘’'`]")
_ESPACES = re.compile(r"\s+")

log = logging.getLogger("bawsla.forum.rag")


async def repondre(req: RepondreRequest, llm: LLMClient, sanitizer: Sanitizer,
                   reglages: ForumAiSettings) -> RepondreResponse:
    tache = "guider" if req.mode == "GUIDEE" else "repondre"
    p = prompts.prompt(tache, reglages.prompt_forum_version)
    if not req.passages:
        return _abstention(p.version, None, ["Aucun passage fourni"])
    question = preparer(sanitizer, req.question)
    passages = {i: (src, preparer(sanitizer, src.texte).texte) for i, src in enumerate(req.passages, start=1)}
    alertes = ["Tentative d'injection neutralisée dans la question"] if question.injection_suspectee else []

    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(question=question.texte, passages=_bloc_passages(passages))),
    ]
    sortie = await llm.complete_json(messages, ReponseLLM, module=MODULE, max_tokens=1200)
    fournisseur = fournisseur_courant() or getattr(llm, "nom", None)
    if sortie.hors_cours or not sortie.reponse.strip():
        return _abstention(p.version, fournisseur, alertes)

    verifiees, rejetees = verifier_citations(sortie, passages, reglages.forum_seuil_citation)
    alertes += [f"Citation introuvable dans le passage [{n}], retirée" for n in rejetees]
    if not verifiees:
        return _abstention(p.version, fournisseur, alertes + ["Aucune citation vérifiée : réponse rejetée"])

    reponse = retirer_numeros(sortie.reponse, set(verifiees))
    indices = [retirer_numeros(i, set(verifiees)) for i in sortie.indices]
    sources = [
        SourceReponse(numero=n, id=passages[n][0].id, origine=passages[n][0].origine, cours=passages[n][0].cours,
                      passage=passages[n][1], chapitre=passages[n][0].chapitre, page=passages[n][0].page,
                      citation=citation)
        for n, citation in sorted(verifiees.items())
    ]
    confiance = calculer_confiance(
        meilleur_score=max(src.score for src, _ in passages.values()),
        part_citee=part_phrases_citees(reponse),
        part_verifiee=len(verifiees) / max(1, len(verifiees) + len(rejetees)),
    )
    return RepondreResponse(reponse=reponse, sources=sources, confiance=confiance, hors_cours=False,
                            indices=indices, version_prompt=p.version, fournisseur=fournisseur, alertes=alertes)


async def repondre_general(req: RepondreGeneralRequest, llm: LLMClient, sanitizer: Sanitizer,
                           reglages: ForumAiSettings) -> RepondreGeneralResponse:
    p = prompts.prompt("repondre_general", reglages.prompt_forum_version)
    question = preparer(sanitizer, req.question)
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(question=question.texte)),
    ]
    sortie = await llm.complete_json(messages, ReponseGeneraleLLM, module=MODULE, max_tokens=900)
    return RepondreGeneralResponse(reponse=sortie.reponse.strip(), incertitudes=sortie.incertitudes,
                                   a_verifier=sortie.a_verifier, version_prompt=p.version,
                                   fournisseur=fournisseur_courant() or getattr(llm, "nom", None))


# ------------------------------------------------------------------ Vérifications (sans LLM)


def verifier_citations(sortie: ReponseLLM, passages: dict[int, tuple[PassageContexte, str]],
                       seuil: int) -> tuple[dict[int, str], list[int]]:
    """Citations vérifiées par numéro de passage (la première valide de chaque passage) et numéros rejetés."""
    verifiees: dict[int, str] = {}
    rejetees: list[int] = []
    for c in sortie.citations:
        if c.passage in verifiees:
            continue
        texte = passages.get(c.passage, (None, ""))[1]
        if texte and len(c.citation.strip()) >= CITATION_MIN and fuzz.partial_ratio(
                _normaliser(c.citation), _normaliser(texte)) >= seuil:
            verifiees[c.passage] = c.citation.strip()
        elif c.passage not in rejetees:
            rejetees.append(c.passage)
    return verifiees, [n for n in rejetees if n not in verifiees]


def retirer_numeros(texte: str, valides: set[int]) -> str:
    """Retire les [n] qui ne renvoient pas à un passage dont la citation est vérifiée."""
    propre = _NUMERO.sub(lambda m: m.group(0) if int(m.group(1)) in valides else "", texte)
    return re.sub(r"\s+([.,;:!?])", r"\1", _ESPACES.sub(" ", propre)).strip()


def part_phrases_citees(texte: str) -> float:
    phrases = [p for p in _PHRASES.split(texte.strip()) if p.strip()]
    if not phrases:
        return 0.0
    return sum(1 for p in phrases if _NUMERO.search(p)) / len(phrases)


def calculer_confiance(meilleur_score: float, part_citee: float, part_verifiee: float) -> int:
    score = 0.4 * min(1.0, max(0.0, meilleur_score)) + 0.3 * part_citee + 0.3 * part_verifiee
    return round(100 * score)


def _normaliser(texte: str) -> str:
    return _ESPACES.sub(" ", _GUILLEMETS.sub("", texte.lower())).strip()


def _bloc_passages(passages: dict[int, tuple[PassageContexte, str]]) -> str:
    blocs = []
    for n, (src, texte) in passages.items():
        source = src.cours if src.origine == "COURS" else "Réponse validée du forum"
        if src.chapitre:
            source += f", {src.chapitre}"
        if src.page:
            source += f", page {src.page}"
        blocs.append(encadrer("passage", texte, id=n, origine=src.origine, source=source.replace('"', "'")))
    return "\n\n".join(blocs)


def _abstention(version: str, fournisseur: str | None, alertes: list[str]) -> RepondreResponse:
    return RepondreResponse(reponse=ABSTENTION, sources=[], confiance=0, hors_cours=True, version_prompt=version,
                            fournisseur=fournisseur, alertes=alertes)
