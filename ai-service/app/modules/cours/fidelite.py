"""Contrôle de fidélité d'une fiche de révision au texte du cours. SANS LLM (rapidfuzz uniquement).

Trois vérifications, pondérées :
- extraits sources (50 %) : chaque `extraitSource` doit se retrouver dans le texte (tolérance aux sauts de ligne
  et à la ponctuation d'un PDF) ; sinon la notion est marquée non justifiée ;
- mots-clés (25 %) : chaque mot-clé doit apparaître dans le texte ;
- nombres (25 %) : les nombres cités dans le résumé et les définitions doivent exister dans le texte.
Résultat : un score de 0 à 100 et des alertes lisibles par le formateur.
"""

import re
import unicodedata
from dataclasses import dataclass, field

from rapidfuzz import fuzz

SEUIL_EXTRAIT = 88  # similarité minimale (0-100) pour considérer un extrait « retrouvé »
SEUIL_MOT_CLE = 85
SEUIL_ALERTE_SCORE = 60
MAX_ALERTES_PAR_TYPE = 5

POIDS_EXTRAITS, POIDS_MOTS_CLES, POIDS_NOMBRES = 0.50, 0.25, 0.25

_NOMBRE = re.compile(r"(?<![\w.,])\d+(?:[.,]\d+)?(?![\w])")
_ESPACES = re.compile(r"\s+")
_TIRETS_COUPURE = re.compile(r"(\w)-\s*\n\s*(\w)")
_REMPLACEMENTS = str.maketrans({"’": "'", "‘": "'", "“": '"', "”": '"', "«": '"', "»": '"', "–": "-", "—": "-", " ": " "})


@dataclass
class NotionControlee:
    titre: str
    justifiee: bool


@dataclass
class ResultatFidelite:
    score: int
    alertes: list[str] = field(default_factory=list)
    notions: list[NotionControlee] = field(default_factory=list)


def normaliser(texte: str) -> str:
    """Forme comparable : mots recollés (césure), guillemets et espaces unifiés, minuscules."""
    t = _TIRETS_COUPURE.sub(r"\1\2", texte or "")
    t = unicodedata.normalize("NFKC", t).translate(_REMPLACEMENTS).casefold()
    return _ESPACES.sub(" ", t).strip()


def _retrouve(motif: str, source_norm: str, seuil: int) -> bool:
    """Le motif (déjà normalisé) figure-t-il dans la source, à peu près ? Test exact d'abord (rapide)."""
    if not motif:
        return False
    if motif in source_norm:
        return True
    return fuzz.partial_ratio(motif, source_norm, score_cutoff=seuil) >= seuil


def _nombres(texte: str) -> list[str]:
    return [n.replace(",", ".") for n in _NOMBRE.findall(texte or "")]


def evaluer(resume: str, mots_cles: list[str], notions: list, texte: str) -> ResultatFidelite:
    """`notions` : objets avec `titre`, `definition` et `extrait_source`."""
    source = normaliser(texte)
    alertes: list[str] = []

    # ---- extraits sources
    controlees: list[NotionControlee] = []
    for n in notions:
        controlees.append(NotionControlee(n.titre, _retrouve(normaliser(n.extrait_source), source, SEUIL_EXTRAIT)))
    introuvables = [c.titre for c in controlees if not c.justifiee]
    part_extraits = (len(controlees) - len(introuvables)) / len(controlees) if controlees else 0.0
    if not controlees:
        alertes.append("Aucune notion n'a été produite : la fiche est incomplète.")
    for titre in introuvables[:MAX_ALERTES_PAR_TYPE]:
        alertes.append(f"Notion « {titre} » : l'extrait cité est introuvable dans le texte du cours. Vérifie-la avant de valider.")
    if len(introuvables) > MAX_ALERTES_PAR_TYPE:
        alertes.append(f"{len(introuvables) - MAX_ALERTES_PAR_TYPE} autre(s) notion(s) ont aussi un extrait introuvable.")

    # ---- mots-clés
    absents = [m for m in mots_cles if not _retrouve(normaliser(m), source, SEUIL_MOT_CLE)]
    part_mots = (len(mots_cles) - len(absents)) / len(mots_cles) if mots_cles else 0.0
    if not mots_cles:
        alertes.append("Aucun mot-clé n'a été produit.")
    if absents:
        alertes.append("Mot(s)-clé(s) absent(s) du texte du cours : " + ", ".join(absents[:MAX_ALERTES_PAR_TYPE]) + ".")

    # ---- nombres
    citees = _nombres(" ".join([resume, *(n.definition for n in notions)]))
    connus = set(_nombres(texte))
    inconnus = list(dict.fromkeys(n for n in citees if n not in connus))
    part_nombres = (len(citees) - len([n for n in citees if n not in connus])) / len(citees) if citees else 1.0
    if inconnus:
        alertes.append("Nombre(s) du résumé ou des définitions absent(s) du texte du cours : " + ", ".join(inconnus[:MAX_ALERTES_PAR_TYPE]) + ".")

    score = round(100 * (POIDS_EXTRAITS * part_extraits + POIDS_MOTS_CLES * part_mots + POIDS_NOMBRES * part_nombres))
    if score < SEUIL_ALERTE_SCORE:
        alertes.insert(0, f"Fidélité faible ({score} / 100) : relis attentivement la fiche avant de la valider.")
    return ResultatFidelite(score=score, alertes=alertes, notions=controlees)
