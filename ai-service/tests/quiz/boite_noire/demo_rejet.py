"""Démo reproductible de la Self-Consistency avec le VRAI LLM : une question fausse est rejetée automatiquement.

Le lot contient 2 questions correctes et 2 questions dont la « bonne réponse » est volontairement fausse
(comme un LLM qui se trompe en générant). Le vérificateur repasse le lot sans connaître les réponses :
il retrouve seul les bonnes réponses des questions correctes et diverge sur les questions fausses,
qui passent en REJETEE avec la raison. Aucune donnée d'utilisateur : questions écrites à la main.

    cd ai-service
    .venv/Scripts/python -m tests.quiz.boite_noire.demo_rejet      # lit LLM_* dans ../.env (2 appels LLM)

Sorties : resultats/demo_rejet.json et resultats/demo_rejet.html (page à capturer pour la démo).
"""

import asyncio
import html
import json
import sys
from datetime import datetime
from pathlib import Path

from app.core.config import get_settings
from app.core.llm_client import fournisseur_courant, get_llm_client
from app.modules.quiz import coherence
from app.modules.quiz.config import get_quiz_ai_settings
from app.modules.quiz.prompts import prompt_verification
from app.modules.quiz.schemas import ChoixQcm, NiveauBloom, QuestionQcm, StatutQuestion
from tests.quiz.boite_noire.scenarios import page

RESULTATS = Path(__file__).parent / "resultats"


def _q(enonce: str, bonne: str, autres: list[tuple[str, str]], explication: str, niveau: NiveauBloom,
       note: str) -> tuple[QuestionQcm, str]:
    choix = [ChoixQcm(texte=bonne, est_correcte=True)] + [
        ChoixQcm(texte=t, est_correcte=False, erreur_associee=e) for t, e in autres
    ]
    return QuestionQcm(enonce=enonce, niveau_bloom=niveau, competence="Requêtes SQL", choix=choix,
                       explication=explication, statut=StatutQuestion.VALIDEE), note


LOT = [
    _q("Quelle jointure ne renvoie que les lignes qui ont une correspondance dans les deux tables ?",
       "INNER JOIN",
       [("LEFT JOIN", "Croit qu'une jointure externe filtre les lignes sans correspondance"),
        ("CROSS JOIN", "Confond produit cartésien et jointure sur condition"),
        ("FULL OUTER JOIN", "Confond jointure complète et intersection")],
       "INNER JOIN ne garde que les couples de lignes qui satisfont la condition de jointure.",
       NiveauBloom.COMPRENDRE, "Correcte"),
    _q("Une table contient 12 lignes, dont 3 ont la colonne email à NULL. Que renvoie SELECT COUNT(*) FROM t ?",
       "9",
       [("12", "Pense que COUNT(*) compte toutes les lignes"),
        ("3", "Compte les NULL au lieu des lignes"),
        ("NULL", "Croit qu'un seul NULL rend l'agrégat NULL")],
       "COUNT(*) ignore les NULL.",
       NiveauBloom.APPLIQUER, "Piège : la « bonne réponse » est fausse (COUNT(*) compte les 12 lignes)"),
    _q("Quel mot-clé supprime les doublons dans le résultat d'un SELECT ?",
       "UNIQUE",
       [("DISTINCT", "Confond DISTINCT et contrainte d'unicité"),
        ("GROUP", "Croit que GROUP seul suffit, sans BY"),
        ("LIMIT", "Confond limitation du nombre de lignes et dédoublonnage")],
       "UNIQUE placé après SELECT élimine les lignes identiques.",
       NiveauBloom.MEMORISER, "Piège : la « bonne réponse » est fausse (c'est DISTINCT)"),
    _q("Quelle clause filtre les groupes après un GROUP BY ?",
       "HAVING",
       [("WHERE", "Filtre les lignes avant le regroupement, pas les groupes"),
        ("ORDER BY", "Confond tri et filtre"),
        ("LIMIT", "Confond nombre de lignes et condition sur les groupes")],
       "HAVING s'applique aux groupes, après l'agrégation ; WHERE agit avant le regroupement.",
       NiveauBloom.COMPRENDRE, "Correcte"),
]


def _carte(i: int, q: QuestionQcm, note: str) -> str:
    badge = '<span class="badge v">VALIDÉE</span>' if q.statut is StatutQuestion.VALIDEE else \
        '<span class="badge r">REJETÉE</span>'
    choix = "".join(
        f"<div class=choix><span class='{'bon' if c.est_correcte else ''}'>{'✔' if c.est_correcte else '○'} "
        f"{html.escape(c.texte)}</span></div>"
        for c in q.choix
    )
    raison = f"<div class=raison><b>Raison du rejet :</b> {html.escape(q.raison_rejet)}</div>" if q.raison_rejet else ""
    return (f"<div class=carte>{badge} <b>Q{i}.</b> {html.escape(q.enonce)}<div class=sous>{q.niveau_bloom} · "
            f"{html.escape(note)}</div>{choix}{raison}</div>")


async def main() -> int:
    socle, reglages = get_settings(), get_quiz_ai_settings()
    llm = get_llm_client()
    prompt = prompt_verification(reglages.prompt_quiz_version)
    questions = [q for q, _ in LOT]
    verifiees = await coherence.verifier(
        llm, questions, prompt,
        budget=reglages.quiz_budget_tokens or socle.groq_tokens_par_minute,
        marge=socle.groq_marge_reflexion,
        nb_verifications=reglages.quiz_nb_verifications,
        temperature=reglages.quiz_temperature_verification,
    )
    fournisseur = fournisseur_courant() or socle.llm_provider
    rejetees = sum(q.statut is StatutQuestion.REJETEE for q in verifiees)

    RESULTATS.mkdir(exist_ok=True)
    (RESULTATS / "demo_rejet.json").write_text(json.dumps(
        {"versionPrompt": prompt.version, "fournisseur": fournisseur,
         "questions": [q.model_dump(by_alias=True, exclude_none=True) for q in verifiees]},
        ensure_ascii=False, indent=2), encoding="utf-8")
    cartes = "".join(_carte(i, q, note) for i, (q, (_, note)) in enumerate(zip(verifiees, LOT, strict=True), 1))
    (RESULTATS / "demo_rejet.html").write_text(page("Self-Consistency — question rejetée", f"""
<h1>Self-Consistency : rejet automatique d'une question fausse</h1>
<div class=sous>{datetime.now():%d/%m/%Y %H:%M} · LLM réel ({html.escape(fournisseur)}) · prompt de vérification
{prompt.version} · {reglages.quiz_nb_verifications} vérifications à {reglages.quiz_temperature_verification},
choix mélangés, bonne réponse jamais envoyée</div>
<div class=resume><div class=tuile><b class=ok>{len(verifiees) - rejetees}</b>validées</div>
<div class=tuile><b class=ko>{rejetees}</b>rejetées</div></div>{cartes}"""), encoding="utf-8")

    for q, (_, note) in zip(verifiees, LOT, strict=True):
        print(f"{q.statut:8} | {note} | {q.raison_rejet or ''}")
    attendu = [q.statut for q in verifiees] == [StatutQuestion.VALIDEE, StatutQuestion.REJETEE,
                                                StatutQuestion.REJETEE, StatutQuestion.VALIDEE]
    print("Démo conforme : 2 validées, les 2 pièges rejetés" if attendu else "Résultat inattendu : voir la page HTML")
    return 0 if attendu else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
