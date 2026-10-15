"""Génération du script d'explication orale : entrée (texte complet + plan), appel LLM, structure, fidélité."""

import logging

from app.core.errors import LLMError, LLMTropLongError
from app.core.llm_client import LLMClient, LLMMessage, LLMRoutage, OpenAILLMClient, estimer_tokens
from app.modules.cours.fournisseur import fournisseur_utilise
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
from app.modules.cours.explication.budget import (
    MOTS_PAR_MINUTE,
    bornes_mots,
    calculer_max_tokens,
    selection_pour_secours,
    texte_du_cours,
)
from app.modules.cours.explication.selection import Selection, selectionner_pages
from app.modules.cours.explication.structure import verifier_structure
from app.modules.cours.prompts import EXPLICATION

MODULE = "cours"
TEMPERATURE = 0.4

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

    mots_min, mots_max = bornes_mots(requete.duree_minutes)
    # Fenêtre acceptée par le contrôle de structure : mêmes proportions que 380 à 850 mots pour une cible de 600 (4 minutes).
    fenetre = (round(requete.duree_minutes * MOTS_PAR_MINUTE * 0.63), round(requete.duree_minutes * MOTS_PAR_MINUTE * 1.42))
    max_tokens = calculer_max_tokens(mots_max, min(len(notions) or 3, 5))

    def messages_pour(pages_envoyees: list[PageCours]) -> list[LLMMessage]:
        return _messages(requete, notions, mots_min, mots_max, texte_du_cours(pages_envoyees))

    tokens_prompt = estimer_tokens(EXPLICATION.systeme) + estimer_tokens(_messages(requete, notions, mots_min, mots_max, "")[1].content)
    _journaliser_budget(llm, tokens_prompt, estimer_tokens(texte_du_cours(selection.pages)), max_tokens)
    messages = messages_pour(selection.pages)
    try:
        script = await _appeler(llm, messages, max_tokens)
    except LLMTropLongError:
        # Gemini est indisponible et le secours (Groq) limite les jetons par minute : on n'envoie que les pages utiles.
        secours = secours_limite(llm)
        if secours is None:
            raise
        try:
            reduite = selection_pour_secours(pages, notions, secours, tokens_prompt=tokens_prompt, max_tokens=max_tokens)
        except ValueError:
            raise LLMTropLongError("Cours trop long pour le fournisseur de secours") from None
        alertes[:] = [a for a in alertes if not a.startswith("Cours tronqué")]
        alertes.append(
            f"Cours tronqué pour le fournisseur de secours ({secours.nom}) : {reduite.pages_gardees} page(s) sur {reduite.pages_totales} "
            f"ont été envoyées à l'IA, en priorité celles qui parlent des notions du plan. Le reste du cours n'est pas couvert."
        )
        _journaliser_budget(llm, tokens_prompt, estimer_tokens(texte_du_cours(reduite.pages)), max_tokens, secours)
        messages = messages_pour(reduite.pages)
        script = await _appeler(llm, messages, max_tokens)
    problemes = verifier_structure(script.segments, requete.mode, fenetre, (mots_min, mots_max))
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
            second = await _appeler(llm, correction, max_tokens)
        except LLMError:
            second = None
        if second is not None:
            restants = verifier_structure(second.segments, requete.mode, fenetre, (mots_min, mots_max))
            if len(restants) <= len(problemes):
                script, problemes = second, restants
    alertes.extend(f"Structure : {p}." for p in problemes)

    return construire_reponse(script.segments, pages, notions, requete, alertes, modele)


def _messages(requete: ExplicationRequest, notions: list[str], mots_min: int, mots_max: int, texte: str) -> list[LLMMessage]:
    return [
        LLMMessage(role="system", content=EXPLICATION.systeme),
        LLMMessage(
            role="user",
            content=EXPLICATION.utilisateur(
                niveau=requete.niveau.value, mode=requete.mode.value, duree=str(requete.duree_minutes),
                mots_min=str(mots_min), mots_max=str(mots_max),
                plan="\n".join(f"- {n}" for n in notions) if notions else "(aucun : choisis toi-même 3 à 5 notions)",
                texte=texte,
            ),
        ),
    ]


def secours_limite(llm: LLMClient) -> OpenAILLMClient | None:
    """Le client de secours (Groq) s'il limite les jetons par minute, sinon None."""
    secours = llm.secours if isinstance(llm, LLMRoutage) else None
    return secours if isinstance(secours, OpenAILLMClient) and secours.limite_tokens_minute else None


def _journaliser_budget(llm: LLMClient, tokens_prompt: int, tokens_texte: int, max_tokens: int, secours: OpenAILLMClient | None = None) -> None:
    """Jetons du texte, du prompt, max_tokens et total de la requête (estimations), avec la limite du secours si elle compte."""
    total = tokens_texte + tokens_prompt + max_tokens
    limite = (secours or secours_limite(llm))
    log.info(
        "Explication, budget de jetons : texte=%d prompt=%d max_tokens=%d total=%d%s",
        tokens_texte, tokens_prompt, max_tokens, total,
        f" (limite du secours {limite.nom} : {limite.limite_tokens_minute} par minute)" if limite else "",
    )


async def _appeler(llm: LLMClient, messages: list[LLMMessage], max_tokens: int) -> ExplicationLLM:
    return await llm.complete_json(messages, ExplicationLLM, module=MODULE, temperature=TEMPERATURE, max_tokens=max_tokens)


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
        fournisseur=fournisseur_utilise(),
        nb_mots=fidelite.compter_mots(numerotes), duree_estimee_secondes=fidelite.duree_estimee_secondes(numerotes),
        notions_choisies_par_ia=not notions,
    )
