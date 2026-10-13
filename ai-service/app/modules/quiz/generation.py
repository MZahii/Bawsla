"""Génération de QCM à partir des objectifs pédagogiques (IA 1 du module Quiz).

Étapes :
1. nettoyage des objectifs par le Sanitizer (le module ne lit jamais le texte ni le PDF du cours) ;
2. prompt Few-Shot avec 2 exemples par niveau de Bloom DEMANDÉ ;
3. appels `complete_json` par lots (QUIZ_LOT_MAX, réduit si le budget de jetons du secours l'exige) ;
4. validation stricte question par question (4 choix, 1 seul correct, explication, erreurAssociee sur chaque
   distracteur) : une question mal formée passe en REJETEE sans faire échouer le lot ;
5. Self-Consistency (coherence.verifier, QUIZ_NB_VERIFICATIONS passes) sur les questions bien formées.
Les erreurs du LLM (429, 502, 503, 504, 413) remontent telles quelles aux gestionnaires du socle.
"""

import json
from dataclasses import dataclass, field

from app.core.config import get_settings
from app.core.errors import LLMTropLongError
from app.core.llm_client import LLMClient, LLMMessage, estimer_tokens, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.quiz import coherence
from app.modules.quiz.config import QuizAiSettings
from app.modules.quiz.prompts import Prompt, prompt_generation, prompt_verification
from app.modules.quiz.prompts.exemples_bloom import exemples_pour
from app.modules.quiz.schemas import (
    ChoixQcm,
    GenererQuizRequest,
    GenererQuizResponse,
    GenerationLlm,
    NiveauBloom,
    QuestionLlm,
    QuestionQcm,
    StatutQuestion,
)

MODULE = "quiz"
# Sortie d'une génération : environ 450 jetons par question (énoncé, 4 choix, 3 erreurs, explication).
JETONS_SORTIE_BASE = 150
JETONS_SORTIE_PAR_QUESTION = 450
MESSAGE_TROP_LONG = "Objectifs trop longs pour le fournisseur de secours"
NOMS_DIFFICULTE = {"FACILE": "facile", "MOYEN": "moyenne", "DIFFICILE": "difficile"}


@dataclass
class Objectifs:
    """Objectifs nettoyés, prêts à entrer dans un prompt."""

    theme: str
    competences: list[str]
    niveaux: list[NiveauBloom]
    difficulte: str
    alertes: list[str] = field(default_factory=list)


def nettoyer(demande: GenererQuizRequest, sanitizer: Sanitizer) -> Objectifs:
    textes = [sanitizer.sanitize(demande.theme)] + [sanitizer.sanitize(c) for c in demande.competences]
    alertes: list[str] = []
    if any(t.pii_masked for t in textes):
        alertes.append("Des données personnelles ont été masquées dans les objectifs avant l'envoi au LLM")
    if any(t.injection_suspected for t in textes):
        alertes.append("Consigne suspecte dans les objectifs : elle a été traitée comme une simple donnée")
    competences = list(dict.fromkeys(t.text.strip() for t in textes[1:] if t.text.strip()))
    return Objectifs(textes[0].text.strip(), competences, demande.niveaux_bloom, demande.difficulte.value, alertes)


def max_tokens_generation(nb: int) -> int:
    return JETONS_SORTIE_BASE + JETONS_SORTIE_PAR_QUESTION * nb


def _messages(prompt: Prompt, obj: Objectifs, nb: int, deja: list[str]) -> list[LLMMessage]:
    exemples = "\n".join(json.dumps(ex, ensure_ascii=False) for ex in exemples_pour(obj.niveaux))
    rappel = ""
    if deja:
        rappel = " Ne reprends aucun de ces énoncés déjà posés :\n" + "\n".join(f"- {e}" for e in deja) + "\n"
    return [
        LLMMessage(role="system", content=prompt.systeme),
        LLMMessage(
            role="user",
            content=prompt.utilisateur(
                exemples=exemples,
                theme=obj.theme,
                competences="\n".join(f"- {c}" for c in obj.competences),
                niveaux=", ".join(n.value for n in obj.niveaux),
                difficulte=NOMS_DIFFICULTE[obj.difficulte],
                nb=str(nb),
                deja=rappel,
            ),
        ),
    ]


def _jetons(messages: list[LLMMessage]) -> int:
    return estimer_tokens("".join(m.content for m in messages))


def taille_lot(prompt: Prompt, obj: Objectifs, lot_max: int, budget: int, marge: int) -> int:
    """Plus grand nombre de questions par appel (≤ lot_max) dont le prompt + la sortie tiennent dans le budget."""
    for n in range(lot_max, 0, -1):
        if _jetons(_messages(prompt, obj, n, [])) + max_tokens_generation(n) + marge <= budget:
            return n
    raise LLMTropLongError(MESSAGE_TROP_LONG)


def _rejet(q: QuestionLlm, niveau: NiveauBloom | None, choix: list[ChoixQcm], raison: str) -> QuestionQcm:
    return QuestionQcm(
        enonce=q.enonce.strip(),
        niveau_bloom=niveau,
        competence=q.competence.strip() or None,
        choix=choix,
        explication=q.explication.strip(),
        statut=StatutQuestion.REJETEE,
        raison_rejet=f"Format invalide : {raison}",
    )


def valider(q: QuestionLlm, obj: Objectifs) -> QuestionQcm:
    """Contrôle strict d'une question renvoyée par le LLM. Statut provisoire VALIDEE, ou REJETEE avec la raison."""
    choix = [
        ChoixQcm(
            texte=c.texte.strip(),
            est_correcte=c.est_correcte,
            erreur_associee=None if c.est_correcte else (c.erreur_associee.strip() or None),
        )
        for c in q.choix
    ]
    code_niveau = q.niveau_bloom.strip().upper()
    niveau = NiveauBloom(code_niveau) if code_niveau in NiveauBloom.__members__ else None
    competence = next((c for c in obj.competences if c.casefold() == q.competence.strip().casefold()), None)

    if not q.enonce.strip():
        return _rejet(q, niveau, choix, "énoncé manquant")
    if len(choix) != 4:
        return _rejet(q, niveau, choix, f"{len(choix)} choix au lieu de 4")
    if any(not c.texte for c in choix):
        return _rejet(q, niveau, choix, "choix vide")
    if len({c.texte.casefold() for c in choix}) != 4:
        return _rejet(q, niveau, choix, "choix en double")
    nb_corrects = sum(c.est_correcte for c in choix)
    if nb_corrects != 1:
        return _rejet(q, niveau, choix, f"{nb_corrects} choix corrects au lieu de 1")
    sans_erreur = [c.texte for c in choix if not c.est_correcte and not c.erreur_associee]
    if sans_erreur:
        return _rejet(q, niveau, choix, f"erreurAssociee manquante pour « {sans_erreur[0]} »")
    if not q.explication.strip():
        return _rejet(q, niveau, choix, "explication manquante")
    if niveau is None or niveau not in obj.niveaux:
        return _rejet(q, niveau, choix, f"niveau de Bloom non demandé ({q.niveau_bloom or 'absent'})")
    if competence is None:
        return _rejet(q, niveau, choix, f"compétence non demandée ({q.competence.strip() or 'absente'})")
    return QuestionQcm(
        enonce=q.enonce.strip(),
        niveau_bloom=niveau,
        competence=competence,
        choix=choix,
        explication=q.explication.strip(),
        statut=StatutQuestion.VALIDEE,
    )


def _cle(enonce: str) -> str:
    return " ".join(enonce.casefold().split())


async def generer_quiz(
    llm: LLMClient, sanitizer: Sanitizer, demande: GenererQuizRequest, *, reglages: QuizAiSettings
) -> GenererQuizResponse:
    socle = get_settings()
    budget = reglages.quiz_budget_tokens or socle.groq_tokens_par_minute
    marge = socle.groq_marge_reflexion
    obj = nettoyer(demande, sanitizer)
    generation = prompt_generation(reglages.prompt_quiz_version)
    verification = prompt_verification(generation.version)
    lot = taille_lot(generation, obj, max(1, reglages.quiz_lot_max), budget, marge)

    questions: list[QuestionQcm] = []
    deja: list[str] = []  # énoncés rappelés au LLM pour les lots suivants
    cles_validees: set[str] = set()  # doublons : comparés aux seules questions retenues
    alertes = list(obj.alertes)
    fournisseurs: list[str] = []
    restant = demande.nb_questions
    while restant > 0:
        nb = min(lot, restant)
        rappel = list(deja)
        while rappel and _jetons(_messages(generation, obj, nb, rappel)) + max_tokens_generation(nb) + marge > budget:
            rappel.pop(0)  # budget serré : on ne rappelle que les énoncés les plus récents
        reponse = await llm.complete_json(
            _messages(generation, obj, nb, rappel),
            GenerationLlm,
            module=MODULE,
            temperature=reglages.quiz_temperature_generation,
            max_tokens=max_tokens_generation(nb),
        )
        _noter(fournisseurs)
        recues = reponse.questions[:nb]
        if len(recues) < nb:
            alertes.append(f"Le LLM a renvoyé {len(recues)} question(s) au lieu de {nb} dans un lot")
        for brute in recues:
            question = valider(brute, obj)
            if question.statut is StatutQuestion.VALIDEE:
                if _cle(question.enonce) in cles_validees:
                    question = question.model_copy(
                        update={"statut": StatutQuestion.REJETEE, "raison_rejet": "Doublon d'une question déjà générée"}
                    )
                else:
                    cles_validees.add(_cle(question.enonce))
            questions.append(question)
            if question.enonce:
                deja.append(question.enonce)
        restant -= nb
        if not recues:
            break

    a_verifier = [i for i, q in enumerate(questions) if q.statut is StatutQuestion.VALIDEE]
    if a_verifier:
        verifiees = await coherence.verifier(
            llm,
            [questions[i] for i in a_verifier],
            verification,
            budget=budget,
            marge=marge,
            nb_verifications=reglages.quiz_nb_verifications,
            temperature=reglages.quiz_temperature_verification,
        )
        _noter(fournisseurs)
        for i, q in zip(a_verifier, verifiees, strict=True):
            questions[i] = q

    nb_validees = sum(q.statut is StatutQuestion.VALIDEE for q in questions)
    return GenererQuizResponse(
        cours_id=demande.cours_id,
        questions=questions,
        nb_validees=nb_validees,
        nb_rejetees=len(questions) - nb_validees,
        version_prompt=generation.version,
        # Sans secours configuré, le socle n'enregistre pas de fournisseur : on reprend LLM_PROVIDER.
        fournisseur="+".join(fournisseurs) or socle.llm_provider,
        alertes=alertes,
    )


def _noter(fournisseurs: list[str]) -> None:
    """Retient le fournisseur qui vient de répondre (gemini, openai, groq…), sans doublon."""
    nom = fournisseur_courant()
    if nom and nom not in fournisseurs:
        fournisseurs.append(nom)
