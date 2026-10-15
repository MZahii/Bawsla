"""Découpage d'un cours en passages pour le RAG (plan §4.2) : ~800 caractères, 120 de chevauchement.

On respecte les paragraphes puis les phrases ; une phrase trop longue est coupée entre deux mots.
Chaque passage garde son chapitre, sa page et le titre de section le plus proche (ligne « # Titre » en Markdown,
ou titre du chapitre). Aucun appel au LLM ici.
"""

import re
from dataclasses import dataclass

_PARAGRAPHES = re.compile(r"\n\s*\n")
_PHRASES = re.compile(r"(?<=[.!?…;:])\s+(?=\S)")
_TITRE_MD = re.compile(r"^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$")
_ESPACES = re.compile(r"[ \t]+")


@dataclass(frozen=True)
class Source:
    """Un morceau de cours à découper : une page du PDF ou un chapitre."""

    texte: str
    chapitre_id: int | None = None
    page: int | None = None
    titre: str | None = None


@dataclass(frozen=True)
class Passage:
    chapitre_id: int | None
    page: int | None
    ordre: int
    titre_section: str | None
    texte: str


def decouper(sources: list[Source], taille: int = 800, chevauchement: int = 120) -> list[Passage]:
    """Passages de toutes les sources, dans l'ordre, numérotés à partir de 0."""
    passages: list[Passage] = []
    for source in sources:
        for titre, texte in _sections(source):
            for morceau in _assembler(_unites(texte, taille), taille, chevauchement):
                passages.append(Passage(source.chapitre_id, source.page, len(passages), titre, morceau))
    return passages


def _sections(source: Source) -> list[tuple[str | None, str]]:
    """Découpe une source en sections selon ses titres Markdown ; le texte avant le premier titre garde le titre
    de la source (titre du chapitre, ou aucun pour une page)."""
    sections: list[tuple[str | None, list[str]]] = [(source.titre, [])]
    for ligne in (source.texte or "").splitlines():
        trouve = _TITRE_MD.match(ligne)
        if trouve:
            sections.append((trouve.group(1).strip()[:200], []))
        else:
            sections[-1][1].append(ligne)
    return [(titre, "\n".join(lignes)) for titre, lignes in sections if "".join(lignes).strip()]


def _unites(texte: str, taille: int) -> list[str]:
    """Phrases du texte, paragraphe par paragraphe ; une marque de paragraphe sépare les blocs."""
    unites: list[str] = []
    for paragraphe in _PARAGRAPHES.split(texte):
        propre = _ESPACES.sub(" ", paragraphe.replace("\n", " ")).strip()
        if not propre:
            continue
        for phrase in _PHRASES.split(propre):
            unites.extend(_couper_long(phrase, taille))
        unites.append("\n\n")
    return unites


def _couper_long(phrase: str, taille: int) -> list[str]:
    if len(phrase) <= taille:
        return [phrase]
    morceaux, courant = [], ""
    for mot in phrase.split(" "):
        if courant and len(courant) + 1 + len(mot) > taille:
            morceaux.append(courant)
            courant = mot
        else:
            courant = f"{courant} {mot}" if courant else mot
        while len(courant) > taille:  # un « mot » plus long que la taille (URL, code) : coupe franche
            morceaux.append(courant[:taille])
            courant = courant[taille:]
    if courant:
        morceaux.append(courant)
    return morceaux


def _assembler(unites: list[str], taille: int, chevauchement: int) -> list[str]:
    """Regroupe les phrases jusqu'à `taille` ; chaque passage reprend les dernières phrases du précédent
    (au plus `chevauchement` caractères) pour ne pas couper une idée en deux."""
    passages: list[str] = []
    courant: list[str] = []
    nouvelles = 0  # phrases ajoutées depuis le dernier passage (hors chevauchement repris)

    for unite in unites:
        if unite == "\n\n":
            if courant and courant[-1] != "\n\n":
                courant.append(unite)
            continue
        if nouvelles and len(_joindre(courant + [unite])) > taille:
            passages.append(_joindre(courant))
            courant = _queue(courant, chevauchement)
            nouvelles = 0
        courant.append(unite)
        nouvelles += 1
    if nouvelles:
        passages.append(_joindre(courant))
    return passages


def _queue(unites: list[str], chevauchement: int) -> list[str]:
    """Dernières phrases (au plus `chevauchement` caractères) à reprendre au début du passage suivant."""
    queue: list[str] = []
    total = 0
    for unite in reversed([u for u in unites if u != "\n\n"]):
        if total + len(unite) > chevauchement:
            break
        queue.insert(0, unite)
        total += len(unite) + 1
    return queue


def _joindre(unites: list[str]) -> str:
    texte = ""
    for u in unites:
        if u == "\n\n":
            texte = texte.rstrip() + "\n\n"
        else:
            texte += ("" if not texte or texte.endswith("\n\n") else " ") + u
    return texte.strip()
