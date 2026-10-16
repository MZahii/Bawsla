"""Traitements NLP du forum (plan §4.1 et §4.2, partie ai-service) : vectorisation, indexation d'un cours,
reformulation des questions. Sans état : forum-service stocke les vecteurs et calcule les similarités.
"""

import logging

from app.core.errors import LLMError, LLMTropLongError
from app.core.llm_client import LLMClient, LLMMessage, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.forum import prompts
from app.modules.forum.config import ForumAiSettings
from app.modules.forum.decoupage import Source, decouper
from app.modules.forum.schemas import (
    IndexerRequest,
    IndexerResponse,
    PassageIndexe,
    ReformulationLLM,
    ReformulerRequest,
    ReformulerResponse,
    VectoriserRequest,
    VectoriserResponse,
)
from app.modules.forum.securite import encadrer, preparer

MODULE = "forum"
MAX_PASSAGES = 3000
HISTORIQUE_MAX = 6

log = logging.getLogger("bawsla.forum.nlp")


async def vectoriser(req: VectoriserRequest, llm: LLMClient, sanitizer: Sanitizer) -> VectoriserResponse:
    """Chaque texte est nettoyé (données personnelles masquées) avant d'être envoyé au fournisseur."""
    textes = [sanitizer.sanitize(t).text for t in req.textes]
    resultat = await llm.embed(textes, module=MODULE, tache=req.tache)
    return VectoriserResponse(vecteurs=resultat.vecteurs, modele=resultat.modele, dimension=resultat.dimension,
                              fournisseur=resultat.fournisseur)


async def indexer(req: IndexerRequest, llm: LLMClient, sanitizer: Sanitizer,
                  reglages: ForumAiSettings) -> IndexerResponse:
    """Découpe le cours, nettoie chaque passage puis le vectorise (titre de section en tête pour le contexte)."""
    sources = [Source(req.texte)] if req.texte else []
    sources += [Source(p.texte, page=p.numero) for p in req.pages]
    sources += [Source(c.contenu, chapitre_id=c.id, titre=c.titre) for c in req.chapitres]
    passages = decouper(sources, reglages.forum_taille_passage, reglages.forum_chevauchement)
    if len(passages) > MAX_PASSAGES:
        raise LLMTropLongError(f"Cours trop long : plus de {MAX_PASSAGES} passages")
    nettoyes = [sanitizer.sanitize(p.texte) for p in passages]
    masquees = sum(n.pii_masked for n in nettoyes)
    a_vectoriser = [f"{p.titre_section} : {n.text}" if p.titre_section else n.text for p, n in zip(passages, nettoyes)]
    resultat = await llm.embed(a_vectoriser, module=MODULE, tache="DOCUMENT")
    if masquees:
        log.info("Indexation du cours %s : %d donnée(s) personnelle(s) masquée(s)", req.cours_id, masquees)
    return IndexerResponse(
        passages=[
            PassageIndexe(chapitre_id=p.chapitre_id, page=p.page, ordre=p.ordre, titre_section=p.titre_section,
                          texte=n.text, vecteur=v)
            for p, n, v in zip(passages, nettoyes, resultat.vecteurs)
        ],
        modele=resultat.modele,
        dimension=resultat.dimension,
        fournisseur=resultat.fournisseur,
        donnees_masquees=masquees,
    )


async def reformuler(req: ReformulerRequest, llm: LLMClient, sanitizer: Sanitizer,
                     reglages: ForumAiSettings) -> ReformulerResponse:
    """Requête technique autonome en français. Si le LLM ne répond pas (quota, stub, erreur), repli : la question
    nettoyée est renvoyée telle quelle, et forum-service la vectorise directement (plan §4.1)."""
    question = preparer(sanitizer, req.question)
    p = prompts.prompt("reformuler", reglages.prompt_forum_version)
    historique = ""
    if req.historique:
        lignes = [f"{m.role.lower()} : {preparer(sanitizer, m.texte).texte}" for m in req.historique[-HISTORIQUE_MAX:]]
        historique = encadrer("historique", "\n".join(lignes)) + "\n\n"
    messages = [
        LLMMessage(role="system", content=p.systeme),
        LLMMessage(role="user", content=p.utilisateur(question=question.texte, historique=historique)),
    ]
    try:
        sortie = await llm.complete_json(messages, ReformulationLLM, module=MODULE, max_tokens=300)
    except LLMError as exc:
        log.info("Reformulation indisponible (%s) : repli sur la question telle quelle", exc.code)
        return ReformulerResponse(requete=question.texte, langue_detectee="inconnue", version_prompt=p.version,
                                  repli=True)
    return ReformulerResponse(requete=sortie.requete.strip(), langue_detectee=sortie.langue_detectee,
                              version_prompt=p.version, fournisseur=fournisseur_courant() or getattr(llm, "nom", None))
