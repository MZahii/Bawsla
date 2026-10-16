"""Garde-fou de fidélité du résumé local : chaque phrase est rapprochée de la phrase du cours la plus proche (sans LLM).

Même principe que fidelite.py (rapidfuzz sur du texte normalisé). Un résumé reformulé qui invente (« projet de loi » dans un cours
de réseaux) n'a aucune phrase proche dans le cours : son score s'effondre et le mode auto bascule sur l'extractif.
"""

import re
from dataclasses import dataclass

from rapidfuzz import fuzz, process

from app.modules.cours.fidelite import normaliser
from app.modules.cours.local import langue as detecteur
from app.modules.cours.local.schemas import PhraseResume
from app.modules.cours.local.texte import Unite

_MOT = re.compile(r"[^\W\d_]{4,}", re.UNICODE)
_NOMBRE = re.compile(r"\d+(?:[.,]\d+)?")
#: Retrait par nombre absent du cours, et plafond du retrait.
PENALITE_NOMBRE, PENALITE_NOMBRE_MAX = 5, 20
#: Retrait si le résumé n'est pas dans la langue du cours (BARThez écrit en français même sur un cours anglais).
PENALITE_LANGUE = 30


@dataclass(frozen=True)
class Controle:
    phrases: list[PhraseResume]
    score: int
    alertes: list[str]


def _prefixe(mot: str) -> str:
    return mot[:5]


def evaluer(phrases: list[str], unites: list[Unite], langue_cours: str, seuil_phrase: int) -> Controle:
    """Phrase par phrase : meilleure phrase du cours (extrait et page), puis score global 0-100.

    Similarité d'une phrase = moyenne de (a) la ressemblance avec sa meilleure phrase du cours (rapidfuzz token_set_ratio) et
    (b) la part de ses mots significatifs qu'on retrouve dans le vocabulaire du cours. Score = moyenne des phrases, moins les
    retraits pour chiffres absents du cours et langue différente.
    """
    if not phrases or not unites:
        return Controle([], 0, ["Résumé vide : rien à comparer au cours."])
    normalisees = [normaliser(u.texte) for u in unites]
    vocabulaire = {_prefixe(m) for t in normalisees for m in _MOT.findall(t)}
    texte_cours = " ".join(normalisees)
    chiffres_cours = set(_NOMBRE.findall(texte_cours))

    resultat: list[PhraseResume] = []
    similarites: list[float] = []
    nombres_absents: list[str] = []
    for phrase in phrases:
        norm = normaliser(phrase)
        meilleur = process.extractOne(norm, normalisees, scorer=fuzz.token_set_ratio)
        indice = meilleur[2] if meilleur else 0
        ressemblance = float(meilleur[1]) if meilleur else 0.0
        mots = _MOT.findall(norm)
        couverture = 100.0 * sum(1 for m in mots if _prefixe(m) in vocabulaire) / len(mots) if mots else ressemblance
        similarite = (ressemblance + couverture) / 2
        similarites.append(similarite)
        nombres_absents += [n for n in _NOMBRE.findall(norm) if n not in chiffres_cours]
        resultat.append(
            PhraseResume(
                texte=phrase, extrait_source=unites[indice].texte, page=unites[indice].page, justifiee=similarite >= seuil_phrase
            )
        )

    alertes: list[str] = []
    score = sum(similarites) / len(similarites)
    if nombres_absents:
        score -= min(PENALITE_NOMBRE_MAX, PENALITE_NOMBRE * len(nombres_absents))
        alertes.append("Chiffres absents du cours : " + ", ".join(sorted(set(nombres_absents))) + ".")
    langue_resume = detecteur.detecter_langue(" ".join(phrases), mots_min=8)  # un résumé court : seuil de mots réduit
    if langue_resume in (detecteur.FR, detecteur.EN) and langue_resume != langue_cours:
        score -= PENALITE_LANGUE
        alertes.append("Le résumé n'est pas dans la langue du cours.")
    non_justifiees = sum(1 for p in resultat if not p.justifiee)
    if non_justifiees:
        alertes.append(f"{non_justifiees} phrase(s) du résumé ne ressemblent à aucune phrase du cours.")
    return Controle(resultat, max(0, min(100, round(score))), alertes)


def controle_extractif(retenues: list[Unite]) -> Controle:
    """Un résumé extractif est recopié du cours : chaque phrase est sa propre source, le score est de 100."""
    return Controle([PhraseResume(texte=u.texte, extrait_source=u.texte, page=u.page, justifiee=True) for u in retenues], 100, [])
