"""Modération assistée de La Vigie (plan §4.5) : chaque publication est classée dans une catégorie avec un score.

1. Règles SANS LLM d'abord (rapides, gratuites, déterministes) : motifs d'injection connus, données personnelles
   (email, téléphone), liens multiples, répétitions, partage de réponses d'examen. La première qui tranche gagne.
2. Sinon, le LLM classe le texte (prompt `moderer`, texte nettoyé par le Sanitizer et placé entre balises).
3. Si le LLM est indisponible, le message est accepté (« OK », score 0, règle `repli`) : la modération ne bloque
   jamais le forum ; les signalements des utilisateurs restent possibles.

forum-service décide ensuite : score ≥ 0,85 masquage automatique, 0,5 à 0,85 signalement au formateur.
Les mots interdits réglés par l'admin sont appliqués par forum-service (il possède les réglages).
"""

import logging
import re
import unicodedata
from dataclasses import dataclass

from app.core.errors import LLMError
from app.core.llm_client import LLMClient, LLMMessage, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.forum import prompts
from app.modules.forum.config import ForumAiSettings
from app.modules.forum.schemas import ModerationLLM, ModererRequest, ModererResponse
from app.modules.forum.securite import encadrer, preparer

MODULE = "forum"
log = logging.getLogger("bawsla.forum.moderation")

# Motifs d'injection connus (français, anglais, arabizi courant), en minuscules et sans accents.
_INJECTION = re.compile(
    r"(ignore[sz]?\s+((toutes?|tes|les|vos|mes|all|the|any|previous|prior|your|precedentes?)\s+){0,3}"
    r"(instructions?|consignes?|regles?|prompts?)"
    r"|oublie\s+(tes|les|toutes?\s+tes)\s+(instructions?|consignes?|regles?)"
    r"|system\s*prompt|prompt\s+systeme|affiche\s+(ton|le)\s+prompt|reveal\s+your\s+(prompt|instructions)"
    r"|tu\s+es\s+(maintenant|desormais)\s+un|you\s+are\s+now\s+(a|an|dan)\b|jailbreak|mode\s+developpeur"
    r"|developer\s+mode|</?\s*(system|question|passage)\s*>)"
)
_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_TELEPHONE = re.compile(r"(?<![\w])(?:\+?\d[\s.-]?){7,14}\d(?![\w])")
_LIEN = re.compile(r"https?://|www\.", re.IGNORECASE)
_MOT = re.compile(r"\w+", re.UNICODE)
_CARACTERE_REPETE = re.compile(r"(.)\1{9,}")
# Fuite d'examen : mention d'un examen ou d'un quiz ET une liste de réponses (« 1) b 2) c », « Q3 : a »…).
_EXAMEN = re.compile(r"\b(examen|exam|quiz|certificat|test\s+final|evaluation\s+finale)\b")
_LISTE_REPONSES = re.compile(r"(?:\b(?:q\s*)?\d{1,2}\s*[).:=-]\s*[a-e]\b.*){3,}")
_PARTAGE_REPONSES = re.compile(r"\b(reponses?|corrige|solutions?|answers?)\b")

LIENS_MAX = 3
REPETITION_MAX = 0.5


@dataclass(frozen=True)
class Verdict:
    categorie: str
    score: float
    raison: str
    regle: str


def appliquer_regles(titre: str | None, contenu: str) -> Verdict | None:
    """Verdict des règles sans LLM, ou None si aucune ne tranche (le LLM décide alors)."""
    texte = f"{titre or ''}\n{contenu}"
    simple = _sans_accents(texte.lower())
    if _INJECTION.search(simple):
        return Verdict("INJECTION", 0.95, "Tentative de détourner les consignes de l'assistant", "injection")
    if _LISTE_REPONSES.search(simple) and (_EXAMEN.search(simple) or _PARTAGE_REPONSES.search(simple)):
        return Verdict("FUITE_EXAMEN", 0.9, "Liste de réponses d'une évaluation partagée", "fuite-examen")
    if _EMAIL.search(texte) or _TELEPHONE.search(texte):
        return Verdict("DONNEES_PERSONNELLES", 0.7, "Le message contient un email ou un numéro de téléphone",
                       "donnees-personnelles")
    if len(_LIEN.findall(texte)) >= LIENS_MAX:
        return Verdict("SPAM", 0.85, f"Au moins {LIENS_MAX} liens dans un même message", "liens")
    if _CARACTERE_REPETE.search(texte) or _repetitif(simple):
        return Verdict("SPAM", 0.85, "Texte très répétitif", "repetitions")
    return None


async def moderer(req: ModererRequest, llm: LLMClient, sanitizer: Sanitizer,
                  reglages: ForumAiSettings) -> ModererResponse:
    verdict = appliquer_regles(req.titre, req.contenu)
    if verdict is not None:
        return ModererResponse(categorie=verdict.categorie, score=verdict.score, raison=verdict.raison,
                               regle=verdict.regle)
    p = prompts.prompt("moderer", reglages.prompt_forum_version)
    titre = preparer(sanitizer, req.titre or "")
    contenu = preparer(sanitizer, req.contenu)
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(
            message=encadrer("message", f"{titre.texte}\n{contenu.texte}".strip()))),
    ]
    try:
        sortie = await llm.complete_json(messages, ModerationLLM, module=MODULE, max_tokens=300)
    except LLMError as e:
        log.warning("Modération par le LLM indisponible (%s) : message accepté", type(e).__name__)
        return ModererResponse(categorie="OK", score=0.0, raison="Modération automatique indisponible",
                               regle="repli", version_prompt=p.version)
    score = 0.0 if sortie.categorie == "OK" else sortie.score
    return ModererResponse(categorie=sortie.categorie, score=round(score, 3), raison=sortie.raison.strip()[:300],
                           version_prompt=p.version,
                           fournisseur=fournisseur_courant() or getattr(llm, "nom", None))


def _repetitif(texte: str) -> bool:
    """Au moins 12 mots et moins de la moitié distincts (« achetez achetez achetez… »)."""
    mots = _MOT.findall(texte)
    return len(mots) >= 12 and len(set(mots)) / len(mots) < REPETITION_MAX


def _sans_accents(texte: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", texte) if unicodedata.category(c) != "Mn")
