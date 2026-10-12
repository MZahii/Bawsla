"""Génération de la fiche de révision d'un cours : découpage, appels LLM (via le socle), fusion, contrôle de fidélité."""

import logging

from app.core.llm_client import LLMClient, LLMMessage
from app.modules.cours.fournisseur import fournisseur_utilise
from app.core.sanitizer import Sanitizer
from app.modules.cours import fidelite
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.decoupage import decouper
from app.modules.cours.prompts import SYNTHESE, Prompt, prompt_fiche
from app.modules.cours.schemas import FicheLLM, FicheResponse, Niveau, Notion, NotionVerifiee, ResumeSeul

MODULE = "cours"
TEMPERATURE = 0.2
MAX_MOTS_CLES = 10
MAX_NOTIONS = 12

log = logging.getLogger("bawsla.cours.fiche")


async def generer_fiche(
    llm: LLMClient,
    sanitizer: Sanitizer,
    texte: str,
    niveau: Niveau,
    *,
    reglages: CoursAiSettings,
    modele: str,
) -> FicheResponse:
    prompt = prompt_fiche(reglages.prompt_fiche_version)
    alertes: list[str] = []

    morceaux = decouper(texte, reglages.fiche_morceau_chars)
    if len(morceaux) > reglages.fiche_morceaux_max:
        utilises = reglages.fiche_morceaux_max
        morceaux = morceaux[:utilises]
        alertes.append(
            f"Le cours est très long : seuls ses {utilises} premiers morceaux (environ {sum(map(len, morceaux))} caractères) "
            "ont été résumés."
        )

    # Chaque morceau est nettoyé séparément (masquage des données personnelles, troncature du socle).
    nettoyes: list[str] = []
    injection = False
    for morceau in morceaux:
        propre = sanitizer.sanitize(morceau)
        nettoyes.append(propre.text)
        injection = injection or propre.injection_suspected
    if injection:
        alertes.append(
            "Le texte du cours contient une formulation qui ressemble à une consigne pour l'IA. "
            "Elle a été traitée comme du simple texte, mais relis la fiche avec attention."
        )

    # Un appel par morceau (l'un après l'autre : on respecte les quotas du fournisseur).
    partielles = [await _fiche_du_morceau(llm, prompt, niveau, t) for t in nettoyes]
    if len(partielles) == 1:
        brute = partielles[0]
    else:
        brute = await _fusionner(llm, niveau, partielles)

    source = "\n\n".join(nettoyes)
    mots_cles = _mots_cles(brute.mots_cles)
    notions = _notions(brute.notions)
    controle = fidelite.evaluer(brute.resume, mots_cles, notions, source)

    return FicheResponse(
        resume=brute.resume.strip(),
        mots_cles=mots_cles,
        notions=[
            NotionVerifiee(titre=n.titre, definition=n.definition, extrait_source=n.extrait_source, justifiee=c.justifiee)
            for n, c in zip(notions, controle.notions, strict=True)
        ],
        niveau=niveau,
        score_fidelite=controle.score,
        alertes=[*alertes, *controle.alertes],
        version_prompt=prompt.version,
        modele=modele,
        fournisseur=fournisseur_utilise(),
    )


async def _fiche_du_morceau(llm: LLMClient, prompt: Prompt, niveau: Niveau, texte: str) -> FicheLLM:
    messages = [
        LLMMessage(role="system", content=prompt.systeme),
        LLMMessage(role="user", content=prompt.utilisateur(niveau=niveau.value, texte=texte)),
    ]
    return await llm.complete_json(messages, FicheLLM, module=MODULE, temperature=TEMPERATURE, max_tokens=3000)


async def _fusionner(llm: LLMClient, niveau: Niveau, partielles: list[FicheLLM]) -> FicheLLM:
    """Plusieurs morceaux : un seul résumé global (un appel de plus), mots-clés et notions regroupés."""
    resumes = "\n\n".join(f"Partie {i}. {p.resume.strip()}" for i, p in enumerate(partielles, start=1))
    messages = [
        LLMMessage(role="system", content=SYNTHESE.systeme),
        LLMMessage(role="user", content=SYNTHESE.utilisateur(niveau=niveau.value, resumes=resumes)),
    ]
    global_ = await llm.complete_json(messages, ResumeSeul, module=MODULE, temperature=TEMPERATURE, max_tokens=1500)
    return FicheLLM(
        resume=global_.resume,
        mots_cles=[m for p in partielles for m in p.mots_cles],
        notions=[n for p in partielles for n in p.notions],
    )


def _mots_cles(mots: list[str]) -> list[str]:
    vus: dict[str, str] = {}
    for mot in mots:
        propre = mot.strip()
        if propre:
            vus.setdefault(propre.casefold(), propre)
    return list(vus.values())[:MAX_MOTS_CLES]


def _notions(notions: list[Notion]) -> list[Notion]:
    vues: dict[str, Notion] = {}
    for n in notions:
        vues.setdefault(n.titre.strip().casefold(), Notion(titre=n.titre.strip(), definition=n.definition.strip(), extrait_source=n.extrait_source.strip()))
    return list(vues.values())[:MAX_NOTIONS]
