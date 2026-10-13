"""Remédiation adaptative (IA 2 du module Quiz) : une mauvaise réponse → diagnostic, explication, rattrapage.

Étapes :
1. nettoyage par le Sanitizer de tous les textes reçus (aucune donnée de l'apprenant n'est reçue : extra="forbid") ;
2. un appel `complete_json` en Chain-of-Thought (prompt remediation_vN) : raisonnement, explication, conseil,
   question de rattrapage ;
3. validation stricte de la question de rattrapage (mêmes règles qu'une question générée, niveau de Bloom
   inférieur ou égal, différente de la question d'origine) ;
4. Self-Consistency (QUIZ_NB_VERIFICATIONS_REMEDIATION passe, 1 par défaut) : si elle échoue, le diagnostic est
   renvoyé sans question, avec la raison dans `alertes`.
Les erreurs du LLM (429, 502, 503, 504, 413) remontent telles quelles aux gestionnaires du socle : quiz-service
passe alors la remédiation en ECHEC ou relance.
Coût : environ 2 000 jetons par remédiation (diagnostic + vérification). Le budget n'est contrôlé qu'appel par
appel : avec Groq gratuit (8 000 jetons par minute), quiz-service doit étaler les remédiations et relancer après 429.
"""

import re

from app.core.config import get_settings
from app.core.errors import LLMInvalidResponseError, LLMTropLongError
from app.core.llm_client import LLMClient, LLMMessage, estimer_tokens
from app.core.sanitizer import Sanitizer
from app.modules.quiz import coherence
from app.modules.quiz.config import QuizAiSettings
from app.modules.quiz.generation import Objectifs, _cle, _noter, max_tokens_generation, valider
from app.modules.quiz.prompts import Prompt, prompt_remediation, prompt_verification
from app.modules.quiz.schemas import (
    NiveauBloom,
    QuestionQcm,
    RemediationLlm,
    RemedierRequest,
    RemedierResponse,
    StatutQuestion,
)

MODULE = "quiz"
# Sortie : une question de rattrapage (comme une génération d'une question), plus raisonnement, explication
# et conseil (~450 jetons).
MAX_TOKENS_REMEDIATION = max_tokens_generation(1, "INTERMEDIAIRE") + 450
MESSAGE_TROP_LONG = "Question trop longue pour le fournisseur de secours"
# Un texte qui ouvre ou ferme la balise de données pourrait en sortir : la balise est retirée.
BALISE = re.compile(r"<\s*/?\s*reponse_apprenant\s*>", re.IGNORECASE)


def _messages(prompt: Prompt, champs: dict[str, str]) -> list[LLMMessage]:
    return [
        LLMMessage(role="system", content=prompt.systeme),
        LLMMessage(role="user", content=prompt.utilisateur(**champs)),
    ]


def nettoyer(demande: RemedierRequest, sanitizer: Sanitizer) -> tuple[dict[str, str], list[str]]:
    """Textes nettoyés, prêts pour le prompt, et alertes éventuelles."""
    bruts = {
        "notion": demande.notion,
        "enonce": demande.enonce,
        "choix_donne": demande.choix_donne,
        "bonne_reponse": demande.bonne_reponse,
        "erreur_associee": demande.erreur_associee or "(non précisée)",
        "explication": demande.explication or "(non précisée)",
    }
    nettoyes = {cle: sanitizer.sanitize(texte) for cle, texte in bruts.items()}
    alertes: list[str] = []
    if any(t.pii_masked for t in nettoyes.values()):
        alertes.append("Des données personnelles ont été masquées avant l'envoi au LLM")
    if any(t.injection_suspected or BALISE.search(t.text) for t in nettoyes.values()):
        alertes.append("Consigne suspecte dans la question : elle a été traitée comme une simple donnée")
    champs = {cle: BALISE.sub("", t.text).strip() for cle, t in nettoyes.items()}
    champs["niveau"] = demande.niveau_bloom.value
    return champs, alertes


def _niveaux_jusqua(niveau: NiveauBloom) -> list[NiveauBloom]:
    """Niveaux de Bloom autorisés pour le rattrapage : le même, ou plus bas."""
    ordre = list(NiveauBloom)
    return ordre[: ordre.index(niveau) + 1]


def valider_rattrapage(reponse: RemediationLlm, demande: RemedierRequest, enonce: str) -> QuestionQcm | str:
    """Question de rattrapage bien formée (statut provisoire VALIDEE), ou la raison de son rejet."""
    if reponse.question_rattrapage is None:
        return "aucune question de rattrapage renvoyée"
    # Une seule notion est visée : la compétence est la notion d'origine (la même que `notion` en réponse).
    brute = reponse.question_rattrapage.model_copy(update={"competence": demande.notion})
    objectifs = Objectifs(demande.notion, [demande.notion], _niveaux_jusqua(demande.niveau_bloom), "INTERMEDIAIRE")
    question = valider(brute, objectifs)
    if question.statut is StatutQuestion.REJETEE:
        return question.raison_rejet or "format invalide"
    if _cle(question.enonce) == _cle(enonce):
        return "elle reprend la question d'origine"
    return question


async def remedier(
    llm: LLMClient, sanitizer: Sanitizer, demande: RemedierRequest, *, reglages: QuizAiSettings
) -> RemedierResponse:
    socle = get_settings()
    budget = reglages.quiz_budget_tokens or socle.groq_tokens_par_minute
    champs, alertes = nettoyer(demande, sanitizer)
    prompt = prompt_remediation(reglages.prompt_quiz_version)
    messages = _messages(prompt, champs)
    if estimer_tokens("".join(m.content for m in messages)) + MAX_TOKENS_REMEDIATION + socle.groq_marge_reflexion > budget:
        raise LLMTropLongError(MESSAGE_TROP_LONG)

    reponse = await llm.complete_json(
        messages,
        RemediationLlm,
        module=MODULE,
        temperature=reglages.quiz_temperature_remediation,
        max_tokens=MAX_TOKENS_REMEDIATION,
    )
    fournisseurs: list[str] = []
    _noter(fournisseurs)
    if not reponse.raisonnement.strip() or not reponse.explication.strip():
        raise LLMInvalidResponseError("Remédiation incomplète : raisonnement ou explication manquant")

    if not reponse.conseil.strip():
        alertes.append("Le LLM n'a pas proposé de conseil")
    rattrapage = valider_rattrapage(reponse, demande, champs["enonce"])
    if isinstance(rattrapage, QuestionQcm):
        (rattrapage,) = await coherence.verifier(
            llm,
            [rattrapage],
            prompt_verification(prompt.version),
            budget=budget,
            marge=socle.groq_marge_reflexion,
            nb_verifications=reglages.quiz_nb_verifications_remediation,
            temperature=reglages.quiz_temperature_verification,
        )
        _noter(fournisseurs)
        if rattrapage.statut is StatutQuestion.REJETEE:
            rattrapage = rattrapage.raison_rejet or "vérification échouée"
    if isinstance(rattrapage, str):
        alertes.append(f"Question de rattrapage écartée : {rattrapage}")
        rattrapage = None

    return RemedierResponse(
        # Notion d'origine (non reformulée) : quiz-service agrège les NotionRatee par compétence.
        notion=demande.notion,
        raisonnement=reponse.raisonnement.strip(),
        explication=reponse.explication.strip(),
        conseil=reponse.conseil.strip(),
        question_rattrapage=rattrapage,
        version_prompt=prompt.version,
        fournisseur="+".join(fournisseurs) or socle.llm_provider,
        alertes=alertes,
    )
