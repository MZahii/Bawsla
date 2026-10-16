"""Résumé EXTRACTIF : les phrases les plus importantes du cours, recopiées telles quelles (TextRank). Zéro hallucination.

Aucune dépendance : le graphe de similarité et l'itération de PageRank sont écrits ici. Fonctionne en français et en anglais.
"""

import math
import re

from app.modules.cours.fidelite import normaliser
from app.modules.cours.local.langue import MOTS_VIDES_EN, MOTS_VIDES_FR
from app.modules.cours.local.schemas import Longueur
from app.modules.cours.local.texte import Unite

#: Nombre de phrases gardées par longueur.
PHRASES = {Longueur.COURTE: 3, Longueur.MOYENNE: 6, Longueur.LONGUE: 10}
#: Au-delà, on ne classe que les unités les plus riches (le graphe est quadratique).
UNITES_MAX = 600
AMORTISSEMENT = 0.85
ITERATIONS = 60
#: Deux phrases qui se ressemblent à plus de 60 % ne sont pas gardées toutes les deux.
REDONDANCE = 0.6

_MOT = re.compile(r"[^\W\d_]{3,}", re.UNICODE)


def _racines(texte: str) -> set[str]:
    """Mots significatifs d'une phrase, ramenés à leurs 6 premières lettres (pluriels et conjugaisons se rejoignent)."""
    vides = MOTS_VIDES_FR | MOTS_VIDES_EN
    return {m[:6] for m in _MOT.findall(normaliser(texte)) if m not in vides}


def _similarite(a: set[str], b: set[str]) -> float:
    if not a or not b or len(a) < 2 or len(b) < 2:
        return 0.0
    commun = len(a & b)
    return commun / (math.log(len(a) + 1) + math.log(len(b) + 1)) if commun else 0.0


def classer(unites: list[Unite]) -> list[float]:
    """Score TextRank de chaque unité (même ordre que `unites`)."""
    racines = [_racines(u.texte) for u in unites]
    n = len(unites)
    poids = [[0.0] * n for _ in range(n)]
    for i in range(n):
        for j in range(i + 1, n):
            w = _similarite(racines[i], racines[j])
            poids[i][j] = poids[j][i] = w
    sorties = [sum(ligne) for ligne in poids]
    scores = [1.0 / n] * n
    for _ in range(ITERATIONS):
        nouveaux = []
        for i in range(n):
            somme = sum(poids[j][i] / sorties[j] * scores[j] for j in range(n) if poids[j][i] and sorties[j])
            nouveaux.append((1 - AMORTISSEMENT) / n + AMORTISSEMENT * somme)
        ecart = sum(abs(a - b) for a, b in zip(nouveaux, scores, strict=True))
        scores = nouveaux
        if ecart < 1e-6:
            break
    return scores


def resumer_extractif(unites: list[Unite], longueur: Longueur) -> list[Unite]:
    """Les phrases clés, dans l'ordre du cours. Une unité n'est jamais modifiée."""
    if not unites:
        return []
    candidates = unites
    if len(candidates) > UNITES_MAX:  # on garde les plus longues, donc les plus informatives
        plus_riches = sorted(range(len(candidates)), key=lambda i: -len(candidates[i].texte))[:UNITES_MAX]
        candidates = [candidates[i] for i in sorted(plus_riches)]
    scores = [s * _qualite(u.texte) for s, u in zip(classer(candidates), candidates, strict=True)]
    ordre = sorted(range(len(candidates)), key=lambda i: -scores[i])
    voulues = min(PHRASES[longueur], len(candidates))
    gardees: list[int] = []
    for i in ordre:
        racines = _racines(candidates[i].texte)
        if any(_chevauche(racines, _racines(candidates[k].texte)) for k in gardees):
            continue
        gardees.append(i)
        if len(gardees) == voulues:
            break
    return [candidates[i] for i in sorted(gardees)]


def _chevauche(a: set[str], b: set[str]) -> bool:
    return bool(a and b) and len(a & b) / min(len(a), len(b)) > REDONDANCE


def _qualite(texte: str) -> float:
    """Coefficient de lisibilité : un long bloc sans ponctuation finale (tableau ou diapositive aplatie) est un mauvais résumé."""
    mots = len(texte.split())
    if not texte.rstrip().endswith((".", "!", "?", "…")) and mots > 25:
        return 0.3
    return 1.0 if mots <= 45 else 0.6
