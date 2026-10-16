"""Aides de La Vigie autour des fils (plan §4.4, §4.6, §4.7) : résumé « En bref », « Améliorer ma question »,
nom des thèmes de l'onglet Tendances.

Tout texte passe par le Sanitizer et entre balises de données. Les sorties du LLM sont bornées et filtrées ici :
au plus 5 puces, étiquettes prises UNIQUEMENT dans la liste fournie, un nom par groupe. Si le LLM est indisponible,
`ameliorer` et `nommer_groupes` se replient sur des règles simples (repli = true) ; `resumer` renvoie l'erreur
(forum-service n'affiche alors pas d'encadré).
"""

import logging
import re
from collections import Counter

from app.core.errors import LLMError
from app.core.llm_client import LLMClient, LLMMessage, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.forum import prompts
from app.modules.forum.config import ForumAiSettings
from app.modules.forum.schemas import (
    AmeliorationLLM,
    AmeliorerRequest,
    AmeliorerResponse,
    NommerGroupesRequest,
    NommerGroupesResponse,
    NomsLLM,
    PucesLLM,
    ResumerRequest,
    ResumerResponse,
)
from app.modules.forum.securite import encadrer, preparer

MODULE = "forum"
PUCES_MAX = 5
MANQUES_MAX = 3
ETIQUETTES_MAX = 3
TITRE_MAX = 150
MESSAGE_MAX = 1500

log = logging.getLogger("bawsla.forum.aides")

_MOT = re.compile(r"[\w'-]{3,}", re.UNICODE)
_MOTS_VIDES = {
    "les", "des", "une", "est", "pas", "que", "qui", "dans", "pour", "avec", "sur", "mon", "mes", "son", "ses", "par",
    "comment", "pourquoi", "quoi", "quel", "quelle", "quels", "quelles", "faire", "fait", "entre", "plus", "sans",
    "elle", "ils", "nous", "vous", "tout", "mais", "aux", "the", "and", "how", "what", "why", "est-ce", "qu'un",
    "qu'une", "renvoie", "marche", "fonctionne", "question", "aide", "problème", "probleme", "erreur",
}


# ------------------------------------------------------------------ Résumé de fil


async def resumer(req: ResumerRequest, llm: LLMClient, sanitizer: Sanitizer,
                  reglages: ForumAiSettings) -> ResumerResponse:
    p = prompts.prompt("resumer", reglages.prompt_forum_version)
    titre = preparer(sanitizer, req.titre).texte
    lignes = [f"QUESTION : {titre}"]
    for m in req.messages:
        lignes.append(f"{m.role} : {preparer(sanitizer, m.texte[:MESSAGE_MAX]).texte}")
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(fil=encadrer("fil", "\n\n".join(lignes)))),
    ]
    sortie = await llm.complete_json(messages, PucesLLM, module=MODULE, max_tokens=600)
    puces = [x.strip(" -•\n") for x in sortie.puces if x.strip(" -•\n")][:PUCES_MAX]
    if not puces:
        puces = ["Pas encore de solution retenue dans ce fil."]
    return ResumerResponse(puces=puces, version_prompt=p.version,
                           fournisseur=fournisseur_courant() or getattr(llm, "nom", None))


# ------------------------------------------------------------------ Améliorer ma question


async def ameliorer(req: AmeliorerRequest, llm: LLMClient, sanitizer: Sanitizer,
                    reglages: ForumAiSettings) -> AmeliorerResponse:
    p = prompts.prompt("ameliorer", reglages.prompt_forum_version)
    existantes = {e.strip().lower(): e.strip() for e in req.etiquettes_existantes if e.strip()}
    titre = preparer(sanitizer, req.titre).texte
    contenu = preparer(sanitizer, req.contenu).texte
    etiquettes = preparer(sanitizer, ", ".join(existantes.values())).texte
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(titre=titre, contenu=contenu, etiquettes=etiquettes)),
    ]
    try:
        sortie = await llm.complete_json(messages, AmeliorationLLM, module=MODULE, max_tokens=500)
    except LLMError as e:
        log.warning("Aide à la rédaction sans LLM (%s) : règles seules", type(e).__name__)
        return AmeliorerResponse(titre=req.titre.strip(), manques=manques_par_regles(req.titre, req.contenu),
                                 repli=True, version_prompt=p.version)
    choisies: list[str] = []
    for e in sortie.etiquettes:
        cle = e.strip().lower()
        if cle in existantes and existantes[cle] not in choisies:
            choisies.append(existantes[cle])
    nouveau_titre = sortie.titre.strip()[:TITRE_MAX] or req.titre.strip()
    manques = [m.strip() for m in sortie.manques if m.strip()][:MANQUES_MAX]
    return AmeliorerResponse(titre=nouveau_titre, manques=manques, etiquettes=choisies[:ETIQUETTES_MAX],
                             version_prompt=p.version, fournisseur=fournisseur_courant() or getattr(llm, "nom", None))


def manques_par_regles(titre: str, contenu: str) -> list[str]:
    """Ce qui manque souvent à une question, détecté sans LLM (repli)."""
    texte = f"{titre}\n{contenu}".lower()
    manques = []
    if re.search(r"\b(erreur|error|exception|plante|crash)", texte) and not re.search(r"`|\"|«", contenu):
        manques.append("Copie le message d'erreur exact.")
    if re.search(r"\b(select|join|requête|requete|code|fonction|classe)\b", texte) and "`" not in contenu:
        manques.append("Ajoute le code ou la requête concernés, dans un bloc de code.")
    if not re.search(r"\b(essay|tent|tried)", texte):
        manques.append("Dis ce que tu as déjà essayé.")
    if len(contenu.strip()) < 80:
        manques.append("Décris ce que tu attends et ce que tu obtiens.")
    return manques[:MANQUES_MAX]


# ------------------------------------------------------------------ Nom des thèmes (Tendances)


async def nommer_groupes(req: NommerGroupesRequest, llm: LLMClient, sanitizer: Sanitizer,
                         reglages: ForumAiSettings) -> NommerGroupesResponse:
    p = prompts.prompt("nommer_groupes", reglages.prompt_forum_version)
    groupes = [[preparer(sanitizer, t[:300]).texte for t in g.titres] for g in req.groupes]
    bloc = "\n\n".join(encadrer("groupe", "\n".join(titres), id=i) for i, titres in enumerate(groupes, start=1))
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(groupes=bloc)),
    ]
    try:
        sortie = await llm.complete_json(messages, NomsLLM, module=MODULE, max_tokens=300)
        noms = [n.strip()[:60] for n in sortie.noms]
        if len(noms) == len(groupes) and all(noms) and len(set(n.lower() for n in noms)) == len(noms):
            return NommerGroupesResponse(noms=noms, version_prompt=p.version,
                                         fournisseur=fournisseur_courant() or getattr(llm, "nom", None))
        log.warning("Noms de thèmes invalides (%d pour %d groupes) : repli", len(noms), len(groupes))
    except LLMError as e:
        log.warning("Nom des thèmes sans LLM (%s) : repli", type(e).__name__)
    return NommerGroupesResponse(noms=noms_par_frequence(groupes), repli=True, version_prompt=p.version)


def noms_par_frequence(groupes: list[list[str]]) -> list[str]:
    """Nom de repli : les deux mots les plus fréquents du groupe (hors mots vides), distincts d'un groupe à l'autre."""
    noms: list[str] = []
    for i, titres in enumerate(groupes, start=1):
        compte = Counter(m for t in titres for m in {x.lower() for x in _MOT.findall(t)} if m not in _MOTS_VIDES)
        nom = " ".join(m for m, _ in compte.most_common(2)) or f"Thème {i}"
        if nom.lower() in (n.lower() for n in noms):
            nom = f"{nom} ({i})"
        noms.append(nom[:1].upper() + nom[1:])
    return noms
