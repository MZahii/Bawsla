"""Cours trop long : quelles pages envoyer au LLM ? On garde en priorité celles qui parlent des notions du plan."""

import re
from dataclasses import dataclass

from app.modules.cours.fidelite import normaliser
from app.modules.cours.explication.schemas import PageCours

_MOTS = re.compile(r"[\w'-]{4,}", re.UNICODE)


@dataclass(frozen=True)
class Selection:
    pages: list[PageCours]
    tronque: bool
    pages_gardees: int
    pages_totales: int


def _score(page_norm: str, notions: list[str]) -> int:
    """Nombre d'occurrences des notions du plan dans la page (titre entier, ou mots significatifs du titre)."""
    total = 0
    for notion in notions:
        titre = normaliser(notion)
        if not titre:
            continue
        total += 3 * page_norm.count(titre)
        mots = set(_MOTS.findall(titre))
        total += sum(1 for m in mots if m in page_norm)
    return total


def selectionner_pages(pages: list[PageCours], notions: list[str], max_chars: int) -> Selection:
    """Pages à envoyer (dans l'ordre du cours) sans dépasser `max_chars` caractères au total.

    Au-delà de la limite : les pages qui contiennent le plus les notions du plan passent d'abord ; à score égal, les premières
    pages. Une page seule plus longue que la limite est coupée.
    """
    non_vides = [p for p in pages if p.texte.strip()]
    total = sum(len(p.texte) for p in non_vides)
    if total <= max_chars:
        return Selection(non_vides, False, len(non_vides), len(non_vides))

    classees = sorted(non_vides, key=lambda p: (-_score(normaliser(p.texte), notions), p.numero))
    gardees: list[PageCours] = []
    reste = max_chars
    for page in classees:
        if reste <= 0:
            break
        if len(page.texte) <= reste:
            gardees.append(page)
            reste -= len(page.texte)
        elif not gardees:  # aucune page ne tient entière : on coupe la plus pertinente
            gardees.append(PageCours(numero=page.numero, texte=page.texte[:reste]))
            reste = 0
    gardees.sort(key=lambda p: p.numero)
    return Selection(gardees, True, len(gardees), len(non_vides))
