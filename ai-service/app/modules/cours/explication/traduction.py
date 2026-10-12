"""Traduction du script FR VALIDÉ en anglais. Le LLM ne traduit que les textes : tout le reste est recopié par le code."""

import json
import logging

from app.core.llm_client import LLMClient, LLMMessage
from app.modules.cours.fournisseur import fournisseur_utilise
from app.core.errors import LLMInvalidResponseError
from app.core.schemas import CamelModel
from app.modules.cours import fidelite
from app.modules.cours.explication.generation import MAX_TOKENS, MODULE, construire_segments, nettoyer_pages, verifier_segments
from app.modules.cours.explication.schemas import ExplicationResponse, TraductionRequest
from app.core.sanitizer import Sanitizer
from app.modules.cours.prompts import TRADUCTION
from pydantic import Field

log = logging.getLogger("bawsla.cours.explication")

NOMS_LANGUES = {"fr": "français", "en": "anglais"}


class SegmentTraduit(CamelModel):
    ordre: int
    texte: str = Field(min_length=1)


class TraductionLLM(CamelModel):
    segments: list[SegmentTraduit]


def _ecarts(attendus: list[int], recus: list[SegmentTraduit]) -> list[str]:
    ordres = [s.ordre for s in recus]
    if ordres == attendus:
        return []
    manquants = sorted(set(attendus) - set(ordres))
    en_trop = sorted(set(ordres) - set(attendus))
    doublons = sorted({o for o in ordres if ordres.count(o) > 1})
    ecarts = []
    if manquants:
        ecarts.append(f"segments manquants : {manquants}")
    if en_trop:
        ecarts.append(f"segments inconnus : {en_trop}")
    if doublons:
        ecarts.append(f"segments en double : {doublons}")
    return ecarts or ["l'ordre des segments a changé"]


async def traduire_explication(llm: LLMClient, sanitizer: Sanitizer, requete: TraductionRequest, modele: str) -> ExplicationResponse:
    pages = nettoyer_pages(requete.pages, sanitizer)
    notions = [n.strip() for n in requete.notions if n.strip()]
    source = sorted(requete.segments, key=lambda s: s.ordre)
    attendus = [s.ordre for s in source]
    if len(set(attendus)) != len(attendus):
        raise LLMInvalidResponseError("Les segments à traduire doivent avoir des numéros d'ordre distincts")

    entree = json.dumps([{"ordre": s.ordre, "texte": s.texte} for s in source], ensure_ascii=False, indent=1)
    messages = [
        LLMMessage(role="system", content=TRADUCTION.systeme),
        LLMMessage(
            role="user",
            content=TRADUCTION.utilisateur(
                langue_source=NOMS_LANGUES[requete.langue_source.value], langue_cible=NOMS_LANGUES[requete.langue_cible.value], segments=entree
            ),
        ),
    ]
    traduit = await llm.complete_json(messages, TraductionLLM, module=MODULE, temperature=0.2, max_tokens=MAX_TOKENS)
    ecarts = _ecarts(attendus, traduit.segments)
    if ecarts:
        log.info("Traduction : segments non conformes (%s), nouvelle tentative", "; ".join(ecarts))
        messages += [
            LLMMessage(role="assistant", content=traduit.model_dump_json(by_alias=True)),
            LLMMessage(
                role="user",
                content="La traduction ne contient pas exactement les mêmes segments : " + " ; ".join(ecarts)
                + ". Renvoie la traduction COMPLÈTE avec les mêmes numéros « ordre », une seule fois chacun.",
            ),
        ]
        traduit = await llm.complete_json(messages, TraductionLLM, module=MODULE, temperature=0.2, max_tokens=MAX_TOKENS)
        ecarts = _ecarts(attendus, traduit.segments)
        if ecarts:
            raise LLMInvalidResponseError("La traduction ne respecte pas le découpage du script : " + "; ".join(ecarts))

    # Type, locuteur, notion, page et extrait FR sont recopiés du script validé : seul le texte change.
    textes = {s.ordre: s.texte.strip() for s in traduit.segments}
    traduits = [s.model_copy(update={"texte": textes[s.ordre]}) for s in source]
    controle = verifier_segments(traduits, pages, notions)

    alertes: list[str] = []
    for original, nouveau in zip(source, traduits, strict=True):
        if original.type.value != "analogie" and set(fidelite._nombres(original.texte)) != set(fidelite._nombres(nouveau.texte)):
            alertes.append(f"Segment {original.ordre} : les nombres de la traduction diffèrent de ceux du français. Compare les deux versions.")
    return ExplicationResponse(
        langue=requete.langue_cible, mode=requete.mode, segments=construire_segments(traduits, controle),
        score_fidelite=controle.score, alertes=[*alertes, *controle.alertes], version_prompt=TRADUCTION.version, modele=modele,
        fournisseur=fournisseur_utilise(),
        nb_mots=fidelite.compter_mots(traduits), duree_estimee_secondes=fidelite.duree_estimee_secondes(traduits),
        notions_choisies_par_ia=not notions,
    )
