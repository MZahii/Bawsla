"""Génération du script d'explication orale : entrée (texte complet + plan), appel LLM, structure, fidélité."""

import logging

from app.core.errors import LLMError
from app.core.llm_client import LLMClient, LLMMessage
from app.core.sanitizer import Sanitizer
from app.modules.cours import fidelite
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.explication.schemas import (
    ExplicationLLM,
    ExplicationRequest,
    ExplicationResponse,
    Locuteur,
    PageCours,
    SegmentEntree,
    SegmentLLM,
    SegmentVerifie,
    TypeSegment,
)
from app.modules.cours.explication.selection import selectionner_pages
from app.modules.cours.explication.structure import MOTS_MAX, MOTS_MIN, verifier_structure
from app.modules.cours.prompts import EXPLICATION

MODULE = "cours"
TEMPERATURE = 0.4
MAX_TOKENS = 6000
MOTS_PAR_MINUTE = 150

log = logging.getLogger("bawsla.cours.explication")


class CoursSansTexteError(LLMError):
    """Le cours n'a pas assez de texte pour être expliqué (vide, ou PDF scanné). Aucun appel au LLM n'est fait."""

    status_code = 400
    code = "BAD_REQUEST"


def nettoyer_pages(pages: list[PageCours], sanitizer: Sanitizer) -> list[PageCours]:
    """Pages sans texte écartées ; les emails et téléphones sont masqués page par page (le Sanitizer tronque une page trop longue)."""
    propres = []
    for page in pages:
        texte = sanitizer.sanitize(page.texte).text.strip() if page.texte.strip() else ""
        if texte:
            propres.append(PageCours(numero=page.numero, texte=texte))
    return propres


def texte_du_cours(pages: list[PageCours]) -> str:
    return "\n\n".join(f"[Page {p.numero}]\n{p.texte}" for p in pages)


def verifier_segments(segments: list[SegmentEntree | SegmentLLM], pages: list[PageCours], notions: list[str]):
    return fidelite.evaluer_explication(segments, [(p.numero, p.texte) for p in pages], notions)


def construire_segments(segments, controle: fidelite.ResultatExplication) -> list[SegmentVerifie]:
    """Segments numérotés avec le verdict de fidélité ; la page est celle où l'extrait a vraiment été trouvé."""
    return [
        SegmentVerifie(
            ordre=c.ordre, type=s.type, locuteur=s.locuteur, texte=s.texte.strip(), notion=s.notion,
            extrait_source=s.extrait_source, page=c.page, justifiee=c.justifiee, analogie=c.analogie,
        )
        for s, c in zip(segments, controle.segments, strict=True)
    ]


async def generer_explication(
    llm: LLMClient, sanitizer: Sanitizer, requete: ExplicationRequest, reglages: CoursAiSettings, modele: str
) -> ExplicationResponse:
    pages = nettoyer_pages(requete.pages, sanitizer)
    if sum(len(p.texte) for p in pages) < reglages.explication_texte_min_chars:
        raise CoursSansTexteError(
            "Le cours ne contient pas assez de texte à expliquer. Si le PDF est un scan, envoie un PDF avec du texte "
            "sélectionnable ou ajoute du contenu dans les chapitres."
        )
    notions = [n.strip() for n in requete.notions if n.strip()]
    alertes: list[str] = []
    if not notions:
        alertes.append("Notions choisies par l'IA : aucune fiche de révision validée ne fournit de plan pour ce cours.")

    selection = selectionner_pages(pages, notions, reglages.explication_texte_max_chars)
    if selection.tronque:
        alertes.append(
            f"Cours tronqué : {selection.pages_gardees} page(s) sur {selection.pages_totales} ont été envoyées à l'IA "
            "(en priorité celles qui parlent des notions du plan). Le reste du cours n'est pas couvert."
        )

    mots_min = max(MOTS_MIN, round(requete.duree_minutes * MOTS_PAR_MINUTE * 0.75))
    mots_max = min(MOTS_MAX, round(requete.duree_minutes * MOTS_PAR_MINUTE * 1.25))
    messages = [
        LLMMessage(role="system", content=EXPLICATION.systeme),
        LLMMessage(
            role="user",
            content=EXPLICATION.utilisateur(
                niveau=requete.niveau.value, mode=requete.mode.value, duree=str(requete.duree_minutes),
                mots_min=str(mots_min), mots_max=str(mots_max),
                plan="\n".join(f"- {n}" for n in notions) if notions else "(aucun : choisis toi-même 3 à 5 notions)",
                texte=texte_du_cours(selection.pages),
            ),
        ),
    ]
    script = await _appeler(llm, messages)
    problemes = verifier_structure(script.segments, requete.mode)
    if problemes:
        # Une seule régénération, avec la liste précise des écarts ; on garde le meilleur des deux scripts.
        log.info("Script d'explication hors structure (%d écart(s)) : régénération", len(problemes))
        correction = [
            *messages,
            LLMMessage(role="assistant", content=script.model_dump_json(by_alias=True)),
            LLMMessage(
                role="user",
                content="Ton script ne respecte pas la structure demandée : " + " ; ".join(problemes)
                + ". Corrige ces points et renvoie le script COMPLET au même format JSON.",
            ),
        ]
        try:
            second = await _appeler(llm, correction)
        except LLMError:
            second = None
        if second is not None:
            restants = verifier_structure(second.segments, requete.mode)
            if len(restants) <= len(problemes):
                script, problemes = second, restants
    alertes.extend(f"Structure : {p}." for p in problemes)

    return construire_reponse(script.segments, pages, notions, requete, alertes, modele)


async def _appeler(llm: LLMClient, messages: list[LLMMessage]) -> ExplicationLLM:
    return await llm.complete_json(messages, ExplicationLLM, module=MODULE, temperature=TEMPERATURE, max_tokens=MAX_TOKENS)


def construire_reponse(
    segments: list[SegmentLLM], pages: list[PageCours], notions: list[str], requete: ExplicationRequest,
    alertes: list[str], modele: str,
) -> ExplicationResponse:
    """Numérote les segments, contrôle leur fidélité au cours COMPLET et assemble la réponse."""
    numerotes = [
        SegmentEntree(
            ordre=i, type=s.type, locuteur=Locuteur.PROF if requete.mode.value == "monologue" else s.locuteur,
            texte=s.texte, notion=s.notion, extrait_source=s.extrait_source, page=s.page,
        )
        for i, s in enumerate(segments, start=1)
    ]
    controle = verifier_segments(numerotes, pages, notions)
    return ExplicationResponse(
        langue=requete.langue, mode=requete.mode, segments=construire_segments(numerotes, controle),
        score_fidelite=controle.score, alertes=[*alertes, *controle.alertes], version_prompt=EXPLICATION.version, modele=modele,
        nb_mots=fidelite.compter_mots(numerotes), duree_estimee_secondes=fidelite.duree_estimee_secondes(numerotes),
        notions_choisies_par_ia=not notions,
    )
