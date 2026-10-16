"""Découpe le cours en unités (phrases ou puces) avec leur page : base de l'extractif et du garde-fou de fidélité."""

import re
from dataclasses import dataclass

from app.modules.cours.explication.schemas import PageCours

_PUCE = re.compile(r"^\s*(?:[•●▪■◦○\-–—*·]|\d{1,2}[.)]|[a-z][.)])\s+", re.IGNORECASE)
_FIN_PHRASE = re.compile(r"(?<=[.!?…])\s+(?=[A-ZÀ-ÝŒ0-9«\"(\[])")
_LIEN = re.compile(r"https?://\S+|www\.\S+")
_MOTS = re.compile(r"[^\W\d_]+", re.UNICODE)

#: Une unité utile fait de 5 à 60 mots (les titres et les phrases interminables ne résument rien).
MOTS_UNITE_MIN, MOTS_UNITE_MAX = 5, 60


@dataclass(frozen=True)
class Unite:
    texte: str
    page: int


def _ressemble_a_du_code(ligne: str) -> bool:
    symboles = sum(ligne.count(c) for c in "{}[]();=<>_/\\|$#")
    return symboles / max(1, len(ligne)) > 0.12 or ligne.rstrip().endswith((";", "{", "}"))


def _propre(unite: str) -> str:
    return re.sub(r"\s+", " ", _LIEN.sub("", unite)).strip(" \t-–—•")


def _blocs(texte: str) -> list[str]:
    """Regroupe les lignes d'une page : une puce ou une fin de phrase termine un bloc, les retours à la ligne d'un paragraphe sont recollés."""
    blocs: list[str] = []
    courant: list[str] = []

    def vider() -> None:
        if courant:
            blocs.append(" ".join(courant))
            courant.clear()

    for ligne in texte.splitlines():
        ligne = ligne.strip()
        if not ligne or ligne.isdigit() or _ressemble_a_du_code(ligne):  # ligne vide, numéro de page ou code : coupe le bloc
            vider()
            continue
        if _PUCE.match(ligne):
            vider()
        courant.append(_PUCE.sub("", ligne, count=1))
        if re.search(r"[.!?…:]$", ligne) or len(" ".join(courant).split()) >= MOTS_UNITE_MAX:
            vider()
    vider()
    return blocs


def decouper_en_unites(pages: list[PageCours]) -> list[Unite]:
    """Phrases et puces du cours, dans l'ordre, sans code, sans liens, ni doublon, de 5 à 60 mots."""
    unites: list[Unite] = []
    vues: set[str] = set()
    for page in pages:
        for bloc in _blocs(page.texte):
            for morceau in _FIN_PHRASE.split(bloc):
                morceau = _propre(morceau)
                n = len(_MOTS.findall(morceau))
                if not (MOTS_UNITE_MIN <= n <= MOTS_UNITE_MAX) or _ressemble_a_du_code(morceau):
                    continue
                cle = re.sub(r"[\W_]+$", "", morceau.lower())
                if cle in vues:
                    continue
                vues.add(cle)
                unites.append(Unite(morceau, page.numero))
    return unites


def phrases_du_texte(texte: str) -> list[str]:
    """Phrases d'un résumé généré (sans filtre de longueur : une phrase courte reste une phrase)."""
    propre = re.sub(r"\s+", " ", texte).strip()
    return [p.strip() for p in _FIN_PHRASE.split(propre) if p.strip()]
