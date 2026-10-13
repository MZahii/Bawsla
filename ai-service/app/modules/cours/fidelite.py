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


# ====================================================================== explication orale (script d'une vidéo de cours)
#
# Même principe que pour la fiche, sans LLM, appliqué à un script découpé en segments :
# - les segments « explication », « exemple » et « demonstration » doivent citer un extrait retrouvé dans le cours
#   (au moins 6 mots) ; on en déduit la vraie page, qui remplace celle annoncée par le modèle si elle diffère ;
# - les nombres et les termes techniques (ACRONYMES, mots de code) cités hors analogie doivent figurer dans le cours ;
# - l'analogie est la seule partie libre : elle est marquée comme telle et n'est pas contrôlée ;
# - chaque notion du plan doit être traitée dans au moins un segment.
# Limite assumée : on ne détecte pas une affirmation fausse écrite avec le vocabulaire du cours ; le formateur valide.

TYPES_EXTRAIT_REQUIS = ("explication", "exemple", "demonstration")
MIN_MOTS_EXTRAIT = 6
POIDS_EXPLICATION_EXTRAITS, POIDS_EXPLICATION_NOMBRES, POIDS_EXPLICATION_TERMES = 0.60, 0.20, 0.20
MOTS_PAR_MINUTE = 150
_TERME_TECHNIQUE = re.compile(r"\b[A-Z][A-Z0-9_]{2,}\b")
_TERMES_IGNORES = frozenset({"FAQ", "NB", "OK"})


@dataclass
class ControleSegment:
    ordre: int
    justifiee: bool
    page: int | None
    analogie: bool


@dataclass
class ResultatExplication:
    score: int
    alertes: list[str] = field(default_factory=list)
    segments: list[ControleSegment] = field(default_factory=list)


def _type(segment) -> str:
    valeur = segment.type
    return getattr(valeur, "value", valeur)


def compter_mots(segments) -> int:
    return sum(len(s.texte.split()) for s in segments)


def duree_estimee_secondes(segments) -> int:
    """Durée de lecture à voix haute (environ 150 mots par minute)."""
    return round(60 * compter_mots(segments) / MOTS_PAR_MINUTE)


def _trouver_page(extrait_norm: str, pages_norm: list[tuple[int, str]]) -> int | None:
    """Numéro de la page qui contient l'extrait (exactement, sinon à peu près), ou None."""
    for numero, texte in pages_norm:
        if extrait_norm in texte:
            return numero
    meilleure, meilleur_score = None, 0.0
    for numero, texte in pages_norm:
        score = fuzz.partial_ratio(extrait_norm, texte, score_cutoff=SEUIL_EXTRAIT)
        if score >= SEUIL_EXTRAIT and score > meilleur_score:
            meilleure, meilleur_score = numero, score
    return meilleure


def _notion_traitee(notion: str, segments) -> bool:
    cible = normaliser(notion)
    for s in segments:
        autre = normaliser(s.notion or "")
        if autre and (cible == autre or cible in autre or autre in cible or fuzz.ratio(cible, autre) >= 80):
            return True
    return False


def evaluer_explication(segments, pages: list[tuple[int, str]], notions: list[str]) -> ResultatExplication:
    """Contrôle un script. `segments` : objets avec ordre, type, texte, notion, extrait_source, page ; `pages` : (numéro, texte)."""
    pages_norm = [(numero, normaliser(texte)) for numero, texte in pages]
    source_norm = " ".join(texte for _, texte in pages_norm)
    nombres_cours = set(_nombres(" ".join(texte for _, texte in pages)))

    controles: list[ControleSegment] = []
    alertes: list[str] = []
    requis = justifies = 0
    nombres_total = nombres_ok = termes_total = termes_ok = 0

    for segment in segments:
        type_ = _type(segment)
        etiquette = f"Segment {segment.ordre} ({type_})"
        analogie = type_ == "analogie"
        justifiee = True
        page = segment.page
        extrait = (segment.extrait_source or "").strip()

        if type_ in TYPES_EXTRAIT_REQUIS:
            requis += 1
            if not extrait:
                justifiee = False
                alertes.append(f"{etiquette} : aucun extrait du cours ne le justifie.")
            elif len(extrait.split()) < MIN_MOTS_EXTRAIT:
                justifiee = False
                alertes.append(f"{etiquette} : extrait trop court pour être vérifié (moins de {MIN_MOTS_EXTRAIT} mots).")
            else:
                trouvee = _trouver_page(normaliser(extrait), pages_norm)
                if trouvee is None:
                    justifiee = False
                    alertes.append(f"{etiquette} : l'extrait cité est introuvable dans le cours. Vérifie ce passage avant de valider.")
                else:
                    if page is not None and page != trouvee:
                        alertes.append(f"{etiquette} : page corrigée (page {trouvee} au lieu de {page}).")
                    page = trouvee
            justifies += justifiee
        elif extrait:  # extrait facultatif (intro, question, récap) : on s'en sert seulement pour donner la page
            page = _trouver_page(normaliser(extrait), pages_norm) or page

        if not analogie:
            for nombre in sorted(set(_nombres(segment.texte))):
                nombres_total += 1
                if nombre in nombres_cours:
                    nombres_ok += 1
                else:
                    alertes.append(f"{etiquette} : le nombre {nombre} ne figure pas dans le cours.")
            for terme in sorted(set(_TERME_TECHNIQUE.findall(segment.texte)) - _TERMES_IGNORES):
                termes_total += 1
                if terme.casefold() in source_norm:
                    termes_ok += 1
                else:
                    alertes.append(f"{etiquette} : le terme « {terme} » ne figure pas dans le cours.")
        controles.append(ControleSegment(segment.ordre, justifiee, page, analogie))

    for notion in notions:
        if not _notion_traitee(notion, segments):
            alertes.append(f"Notion du plan non traitée : « {notion} ».")
    if not requis:
        alertes.append("Aucun segment d'explication, d'exemple ou de démonstration : le script est incomplet.")

    part_extraits = justifies / requis if requis else 0.0
    part_nombres = nombres_ok / nombres_total if nombres_total else 1.0
    part_termes = termes_ok / termes_total if termes_total else 1.0
    score = round(100 * (POIDS_EXPLICATION_EXTRAITS * part_extraits + POIDS_EXPLICATION_NOMBRES * part_nombres + POIDS_EXPLICATION_TERMES * part_termes))
    if score < SEUIL_ALERTE_SCORE:
        alertes.insert(0, f"Fidélité faible ({score} / 100) : relis attentivement le script avant de le valider.")
    return ResultatExplication(score=score, alertes=alertes, segments=controles)
