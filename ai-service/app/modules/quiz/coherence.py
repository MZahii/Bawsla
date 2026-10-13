"""Self-Consistency : le LLM repasse les questions générées SANS la bonne réponse, et l'on compare.

- QUIZ_NB_VERIFICATIONS passes indépendantes (2 par défaut) à QUIZ_TEMPERATURE_VERIFICATION (0.7) :
  à température 0, le même modèle serait presque toujours d'accord avec lui-même ;
- à chaque passe, les choix sont mélangés dans un autre ordre, avec une graine fixe (reproductible) ;
- aucune indication d'exactitude, d'erreur associée ni d'explication n'est envoyée ;
- un appel par passe pour tout le lot (découpé seulement si le budget de jetons l'exige) ;
- une seule divergence (désaccord, réponse absente ou illisible) → REJETEE, avec la passe et la raison.
"""

import random

from app.core.llm_client import LLMClient, LLMMessage, estimer_tokens
from app.modules.quiz.prompts import Prompt
from app.modules.quiz.schemas import ChoixQcm, QuestionQcm, StatutQuestion, VerificationLlm

MODULE = "quiz"
GRAINE = "bawsla-quiz"
LETTRES = "ABCD"
# Sortie d'une vérification : environ 60 jetons par question, plus l'enveloppe JSON.
JETONS_SORTIE_BASE = 60
JETONS_SORTIE_PAR_QUESTION = 60


def melanger(question: QuestionQcm, passe: int = 1) -> QuestionQcm:
    """Même question, choix mélangés de façon reproductible (la graine dépend de l'énoncé et de la passe)."""
    choix = list(question.choix)
    graine = f"{GRAINE}:{question.enonce}" if passe == 1 else f"{GRAINE}:{passe}:{question.enonce}"
    random.Random(graine).shuffle(choix)
    return question.model_copy(update={"choix": choix})


def max_tokens_verification(nb: int) -> int:
    return JETONS_SORTIE_BASE + JETONS_SORTIE_PAR_QUESTION * nb


def _bloc(numero: int, question: QuestionQcm) -> str:
    lignes = [f"{numero}. {question.enonce}"]
    lignes += [f"   {LETTRES[i]}. {c.texte}" for i, c in enumerate(question.choix)]
    return "\n".join(lignes)


def _messages(prompt: Prompt, blocs: list[str]) -> list[LLMMessage]:
    return [
        LLMMessage(role="system", content=prompt.systeme),
        LLMMessage(role="user", content=prompt.utilisateur(questions="\n\n".join(blocs))),
    ]


def _paquets(prompt: Prompt, blocs: list[str], budget: int, marge: int) -> list[list[int]]:
    """Regroupe les questions (indices) en le moins d'appels possible sans dépasser le budget de jetons."""
    paquets: list[list[int]] = []
    courant: list[int] = []
    for i in range(len(blocs)):
        essai = courant + [i]
        texte = "".join(m.content for m in _messages(prompt, [blocs[j] for j in essai]))
        if courant and estimer_tokens(texte) + max_tokens_verification(len(essai)) + marge > budget:
            paquets.append(courant)
            courant = [i]
        else:
            courant = essai
    if courant:
        paquets.append(courant)
    return paquets


def _rejeter(question: QuestionQcm, raison: str) -> QuestionQcm:
    return question.model_copy(update={"statut": StatutQuestion.REJETEE, "raison_rejet": raison})


def _court(texte: str, limite: int = 160) -> str:
    texte = " ".join(texte.split())
    return texte if len(texte) <= limite else texte[: limite - 1] + "…"


async def verifier(
    llm: LLMClient,
    questions: list[QuestionQcm],
    prompt: Prompt,
    *,
    budget: int,
    marge: int,
    nb_verifications: int = 2,
    temperature: float = 0.7,
) -> list[QuestionQcm]:
    """Renvoie les questions (choix dans l'ordre de la 1re passe) : VALIDEE si chaque passe retrouve seule
    la bonne réponse, sinon REJETEE avec la raison de la première divergence."""
    nb_verifications = max(1, nb_verifications)
    raisons: list[str | None] = [None] * len(questions)
    for passe in range(1, nb_verifications + 1):
        melangees = [melanger(q, passe) for q in questions]
        blocs = [_bloc(i + 1, q) for i, q in enumerate(melangees)]
        for paquet in _paquets(prompt, blocs, budget, marge):
            sans_reponses = [_bloc(n + 1, melangees[i]) for n, i in enumerate(paquet)]
            verification = await llm.complete_json(
                _messages(prompt, sans_reponses),
                VerificationLlm,
                module=MODULE,
                temperature=temperature,
                max_tokens=max_tokens_verification(len(paquet)),
            )
            par_numero = {r.numero: r for r in verification.reponses}
            for n, i in enumerate(paquet):
                divergence = _divergence(melangees[i], par_numero.get(n + 1))
                if divergence and raisons[i] is None:
                    raisons[i] = f"{divergence} (vérification {passe}/{nb_verifications})"
    resultat: list[QuestionQcm] = []
    for question, raison in zip((melanger(q) for q in questions), raisons, strict=True):
        if raison:
            resultat.append(_rejeter(question, raison))
        else:
            resultat.append(question.model_copy(update={"statut": StatutQuestion.VALIDEE, "raison_rejet": None}))
    return resultat


def _divergence(question: QuestionQcm, reponse) -> str | None:
    """Raison du désaccord entre une passe de vérification et la bonne réponse, ou None si elles concordent."""
    if reponse is None:
        return "La vérification n'a pas répondu à cette question"
    lettre = reponse.choix.strip().upper()[:1]
    if lettre not in LETTRES:
        return f"Réponse de vérification illisible : {_court(reponse.choix, 20)!r}"
    choisi: ChoixQcm = question.choix[LETTRES.index(lettre)]
    if choisi.est_correcte:
        return None
    bonne = next(c for c in question.choix if c.est_correcte)
    raison = f"Désaccord à la vérification : « {_court(choisi.texte)} » choisi au lieu de « {_court(bonne.texte)} »"
    if reponse.justification.strip():
        raison += f" — {_court(reponse.justification)}"
    return raison
