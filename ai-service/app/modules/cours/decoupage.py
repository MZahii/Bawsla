"""Découpage d'un long texte de cours en morceaux de taille raisonnable pour le LLM.

On coupe de préférence entre deux paragraphes, sinon entre deux phrases, sinon sur un espace.
Le Sanitizer du socle tronque à `sanitizer_max_chars` : chaque morceau est donc nettoyé séparément.
"""

import re

_PARAGRAPHES = re.compile(r"\n\s*\n")
_PHRASES = re.compile(r"(?<=[.!?…])\s+")


def decouper(texte: str, taille: int) -> list[str]:
    """Morceaux de `taille` caractères au plus, dans l'ordre du texte, sans en perdre."""
    texte = (texte or "").strip()
    if not texte:
        return []
    if len(texte) <= taille:
        return [texte]

    morceaux: list[str] = []
    courant = ""
    for bloc in _blocs(texte, taille):
        if courant and len(courant) + 2 + len(bloc) > taille:
            morceaux.append(courant)
            courant = bloc
        else:
            courant = f"{courant}\n\n{bloc}" if courant else bloc
    if courant:
        morceaux.append(courant)
    return morceaux


def _blocs(texte: str, taille: int):
    """Paragraphes ; un paragraphe trop long est coupé en phrases, puis en tranches si nécessaire."""
    for paragraphe in _PARAGRAPHES.split(texte):
        paragraphe = paragraphe.strip()
        if not paragraphe:
            continue
        if len(paragraphe) <= taille:
            yield paragraphe
            continue
        courant = ""
        for phrase in _PHRASES.split(paragraphe):
            for partie in _tranches(phrase, taille):
                if courant and len(courant) + 1 + len(partie) > taille:
                    yield courant
                    courant = partie
                else:
                    courant = f"{courant} {partie}" if courant else partie
        if courant:
            yield courant


def _tranches(phrase: str, taille: int):
    """Une « phrase » plus longue que `taille` (texte sans ponctuation) est coupée sur un espace."""
    while len(phrase) > taille:
        coupe = phrase.rfind(" ", 0, taille)
        coupe = coupe if coupe > taille // 2 else taille
        yield phrase[:coupe].strip()
        phrase = phrase[coupe:].strip()
    if phrase:
        yield phrase
