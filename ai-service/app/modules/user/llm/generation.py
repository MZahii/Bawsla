"""IA générative du module User : le LLM met en mots les résultats du Machine Learning.

Pour chaque fonctionnalité :
1. tous les textes reçus (notions, titres, objectif, phrase libre) passent par le Sanitizer (emails et téléphones
   masqués, longueur bornée), et les balises qui pourraient fermer le bloc de données sont retirées ;
2. un seul appel `complete_json` (prompt versionné, voir prompts.py), via le client LLM du socle ;
3. la réponse est contrôlée : listes bornées, valeurs prises dans les listes autorisées, durées du plan dans le temps
   disponible, et aucun mot de risque dans un texte destiné à l'apprenant (sinon `LLMInvalidResponseError`).
Les erreurs du LLM (quota 429, indisponible 502/503, délai 504) remontent aux gestionnaires du socle : user-service
affiche alors un message clair, et les résultats du Machine Learning restent visibles.
"""

import re

from app.core.errors import LLMInvalidResponseError
from app.core.llm_client import LLMClient, LLMMessage, fournisseur_courant
from app.core.sanitizer import Sanitizer
from app.modules.user.llm import prompts
from app.modules.user.llm.prompts import Prompt
from app.modules.user.llm.schemas import (
    CohorteLlm,
    CohorteRequest,
    CohorteResponse,
    ConseilLlm,
    ConseilRequest,
    ConseilResponse,
    DomainesLlm,
    DomainesRequest,
    DomainesResponse,
    ExplicationRisqueLlm,
    ExplicationRisqueRequest,
    ExplicationRisqueResponse,
    PlanLlm,
    PlanRequest,
    PlanResponse,
    Seance,
)
from app.modules.user.schemas import Indicateurs

MODULE = "user"
MAX_ACTIONS = 3
# Un texte qui ouvre ou ferme une balise de données pourrait en sortir : la balise est retirée.
BALISE = re.compile(r"<\s*/?\s*(donnees|texte_apprenant)\s*>", re.IGNORECASE)
# Le risque n'est jamais montré à l'apprenant (DESIGN.md) : un texte qui lui est destiné ne doit pas en parler.
MOTS_INTERDITS = re.compile(r"\b(risques?|échecs?|echecs?|abandon\w*|décroch\w*)\b", re.IGNORECASE)
JOURS = ["Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche"]
DUREE_MIN, DUREE_MAX, MAX_SEANCES = 10, 120, 6
# Libellés du parcours (« Ton rythme ») → minutes par semaine ; inconnu : 3 h.
BUDGETS = {"moins de 1 h": 60, "1 à 3 h": 180, "3 à 5 h": 300, "plus de 5 h": 420}
BUDGET_PAR_DEFAUT = 180
LIBELLE_RISQUE = {"faible": "faible", "moyen": "moyen (à surveiller)", "eleve": "élevé"}


# ---------------------------------------------------------------- outils
def nettoyer(sanitizer: Sanitizer, texte: str | None, defaut: str = "(non précisé)") -> str:
    if texte is None or not texte.strip():
        return defaut
    return BALISE.sub("", sanitizer.sanitize(texte).text).strip() or defaut


def liste(sanitizer: Sanitizer, textes: list[str], defaut: str = "(aucun)") -> str:
    propres = [nettoyer(sanitizer, t, "") for t in textes]
    propres = [t for t in propres if t]
    return " ; ".join(propres) if propres else defaut


def pourcent(x: float | None) -> str:
    return "(inconnue)" if x is None else f"{round(x * 100)} %"


def decrire(i: Indicateurs) -> str:
    """Les 11 indicateurs en lignes lisibles (seulement des nombres, aucun identifiant)."""
    return "\n".join([
        f"- Note moyenne aux quiz : {pourcent(i.note_moyenne)} ({i.nb_quiz} quiz passés)",
        f"- Bonnes réponses : {pourcent(i.taux_reussite)}",
        f"- Notions ratées : {i.nb_notions_ratees} ({i.nb_erreurs} erreurs)",
        f"- Progression moyenne dans les cours : {pourcent(i.progression_moyenne)}",
        f"- Cours suivis : {i.nb_cours_suivis}, dont {i.nb_cours_termines} terminés",
        f"- Temps passé dans les cours : {i.temps_passe_minutes} minutes",
        f"- Jours actifs sur les 30 derniers : {i.jours_actifs30j}",
        f"- Jours depuis la dernière connexion : {i.jours_depuis_derniere_connexion}",
    ])


def phrases(textes: list[str], maxi: int) -> list[str]:
    return [t.strip() for t in textes if t and t.strip()][:maxi]


def sans_risque(*textes: str) -> None:
    """Refuse un texte destiné à l'apprenant qui parle de risque, d'échec ou d'abandon."""
    for texte in textes:
        if MOTS_INTERDITS.search(texte):
            raise LLMInvalidResponseError("Le texte destiné à l'apprenant parle de risque : réponse écartée")


async def _appeler(llm: LLMClient, prompt: Prompt, champs: dict[str, str], schema, *, temperature: float,
                   max_tokens: int):
    messages = [LLMMessage(role="system", content=prompt.systeme),
                LLMMessage(role="user", content=prompt.utilisateur(**champs))]
    reponse = await llm.complete_json(messages, schema, module=MODULE, temperature=temperature, max_tokens=max_tokens)
    # Sans fournisseur de secours, le client Gemini est appelé directement : on prend alors son nom.
    return reponse, fournisseur_courant() or getattr(llm, "nom", None)


# ---------------------------------------------------------------- 1. conseil
async def conseil(llm: LLMClient, sanitizer: Sanitizer, demande: ConseilRequest) -> ConseilResponse:
    champs = {
        "profil": nettoyer(sanitizer, demande.profil, "(pas encore calculé)"),
        "objectif": nettoyer(sanitizer, demande.objectif),
        "indicateurs": decrire(demande.indicateurs),
        "cours_suivis": liste(sanitizer, demande.cours_suivis),
        "notions": liste(sanitizer, demande.notions_a_revoir),
        "recommandes": liste(sanitizer, demande.cours_recommandes),
    }
    r, fournisseur = await _appeler(llm, prompts.CONSEIL, champs, ConseilLlm, temperature=0.4, max_tokens=600)
    actions = phrases(r.actions, MAX_ACTIONS)
    if not r.conseil.strip() or not actions:
        raise LLMInvalidResponseError("Conseil incomplet : texte ou actions manquants")
    sans_risque(r.conseil, *actions)
    return ConseilResponse(conseil=r.conseil.strip(), actions=actions, fournisseur=fournisseur,
                           version_prompt=prompts.VERSION)


# ---------------------------------------------------------------- 2. explication du risque
async def explication_risque(llm: LLMClient, sanitizer: Sanitizer,
                             demande: ExplicationRisqueRequest) -> ExplicationRisqueResponse:
    champs = {
        "niveau": LIBELLE_RISQUE[demande.niveau],
        "probabilite": pourcent(demande.probabilite),
        "facteurs": liste(sanitizer, demande.facteurs),
        "profil": nettoyer(sanitizer, demande.profil, "(pas encore calculé)"),
        "indicateurs": decrire(demande.indicateurs),
        "notions": liste(sanitizer, demande.notions_a_revoir),
    }
    r, fournisseur = await _appeler(llm, prompts.EXPLICATION_RISQUE, champs, ExplicationRisqueLlm,
                                    temperature=0.3, max_tokens=700)
    actions = phrases(r.actions, MAX_ACTIONS)
    if not r.resume.strip() or not actions or not r.message_suggere.strip():
        raise LLMInvalidResponseError("Explication incomplète : résumé, actions ou message manquant")
    sans_risque(r.message_suggere)  # le message est destiné à l'apprenant
    return ExplicationRisqueResponse(resume=r.resume.strip(), actions=actions,
                                     message_suggere=r.message_suggere.strip(), fournisseur=fournisseur,
                                     version_prompt=prompts.VERSION)


# ---------------------------------------------------------------- 3. plan de la semaine
def budget(temps: str | None) -> int:
    return BUDGETS.get((temps or "").strip().lower(), BUDGET_PAR_DEFAUT)


def _jour(texte: str) -> str | None:
    t = texte.strip().lower()
    return next((j for j in JOURS if j.lower() == t), None)


def ajuster(seances: list[Seance], budget_minutes: int) -> list[Seance]:
    """Séances valides seulement (jour connu, une par jour), durées entre 10 et 120 min, total dans le budget,
    triées du lundi au dimanche."""
    gardees: dict[str, Seance] = {}
    for s in seances:
        jour = _jour(s.jour)
        if jour and jour not in gardees and s.activite.strip():
            duree = min(DUREE_MAX, max(DUREE_MIN, s.duree_minutes))
            gardees[jour] = Seance(jour=jour, activite=s.activite.strip(), duree_minutes=duree)
    liste_ = sorted(gardees.values(), key=lambda s: JOURS.index(s.jour))[:MAX_SEANCES]
    total = sum(s.duree_minutes for s in liste_)
    while liste_ and total > budget_minutes:
        # Trop long : on réduit proportionnellement (par tranches de 5 min) ; sinon on retire la dernière séance.
        facteur = budget_minutes / total
        reduites = [s.model_copy(update={"duree_minutes": max(DUREE_MIN, int(s.duree_minutes * facteur) // 5 * 5)})
                    for s in liste_]
        if sum(s.duree_minutes for s in reduites) <= budget_minutes:
            return reduites
        liste_ = liste_[:-1]
        total = sum(s.duree_minutes for s in liste_)
    return liste_


async def plan(llm: LLMClient, sanitizer: Sanitizer, demande: PlanRequest) -> PlanResponse:
    budget_minutes = budget(demande.temps_disponible)
    en_cours = [f"{nettoyer(sanitizer, c.titre)} ({pourcent(c.progression)})" for c in demande.cours_en_cours]
    champs = {
        "budget": str(budget_minutes),
        "objectif": nettoyer(sanitizer, demande.objectif),
        "temps": nettoyer(sanitizer, demande.temps_disponible),
        "profil": nettoyer(sanitizer, demande.profil, "(pas encore calculé)"),
        "cours_en_cours": " ; ".join(en_cours) if en_cours else "(aucun)",
        "notions": liste(sanitizer, demande.notions_a_revoir),
        "recommandes": liste(sanitizer, demande.cours_recommandes),
    }
    r, fournisseur = await _appeler(llm, prompts.PLAN, champs, PlanLlm, temperature=0.4, max_tokens=900)
    seances = ajuster(r.seances, budget_minutes)
    if not r.resume.strip() or not seances:
        raise LLMInvalidResponseError("Plan incomplet : aucune séance valide")
    sans_risque(r.resume, *(s.activite for s in seances))
    return PlanResponse(resume=r.resume.strip(), seances=seances, total_minutes=sum(s.duree_minutes for s in seances),
                        budget_minutes=budget_minutes, fournisseur=fournisseur, version_prompt=prompts.VERSION)


# ---------------------------------------------------------------- 4. rapport de la classe
async def rapport_cohorte(llm: LLMClient, sanitizer: Sanitizer, demande: CohorteRequest) -> CohorteResponse:
    r_ = demande.risques
    champs = {
        "nb_apprenants": str(demande.nb_apprenants),
        "nb_cours": str(demande.nb_cours),
        "risques": f"élevé {r_.eleve}, à surveiller {r_.moyen}, faible {r_.faible}, données incomplètes {r_.inconnu}",
        "progression": pourcent(demande.progression_moyenne),
        "note": pourcent(demande.note_moyenne),
        "profils": " ; ".join(f"{nettoyer(sanitizer, p.nom)} : {p.nombre}" for p in demande.profils) or "(aucun)",
        "notions": " ; ".join(f"{nettoyer(sanitizer, n.nom)} : {n.nombre}" for n in demande.notions) or "(aucune)",
        "cours": " ; ".join(f"{nettoyer(sanitizer, c.titre)} : {c.nb_inscrits} inscrits, {pourcent(c.progression_moyenne)}"
                            for c in demande.cours) or "(aucun)",
    }
    r, fournisseur = await _appeler(llm, prompts.COHORTE, champs, CohorteLlm, temperature=0.2, max_tokens=900)
    points, recos = phrases(r.points_cles, 4), phrases(r.recommandations, 4)
    if not r.synthese.strip() or not points or not recos:
        raise LLMInvalidResponseError("Rapport incomplet : synthèse, constats ou recommandations manquants")
    return CohorteResponse(synthese=r.synthese.strip(), points_cles=points, recommandations=recos,
                           fournisseur=fournisseur, version_prompt=prompts.VERSION)


# ---------------------------------------------------------------- 5. domaines depuis une phrase
def _canonique(valeur: str | None, autorisees: list[str]) -> str | None:
    if not valeur:
        return None
    cle = valeur.strip().lower()
    return next((a for a in autorisees if a.strip().lower() == cle), None)


async def domaines(llm: LLMClient, sanitizer: Sanitizer, demande: DomainesRequest) -> DomainesResponse:
    champs = {
        "domaines": " ; ".join(demande.domaines_disponibles),
        "objectifs": " ; ".join(demande.objectifs_disponibles) or "(aucun)",
        "texte": nettoyer(sanitizer, demande.texte),
    }
    r, fournisseur = await _appeler(llm, prompts.DOMAINES, champs, DomainesLlm, temperature=0.0, max_tokens=300)
    choisis: list[str] = []
    for d in r.domaines:  # seulement des valeurs exactes de la liste, sans doublon
        c = _canonique(d, demande.domaines_disponibles)
        if c and c not in choisis:
            choisis.append(c)
    return DomainesResponse(domaines=choisis[:3], objectif=_canonique(r.objectif, demande.objectifs_disponibles),
                            explication=r.explication.strip(), fournisseur=fournisseur, version_prompt=prompts.VERSION)
