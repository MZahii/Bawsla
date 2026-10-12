"""Détection de la langue d'un cours (fr, en ou autre) sans réseau ni bibliothèque.

Méthode : part des mots vides (« le », « de », « the », « of »…) de chaque langue parmi les mots du texte. Pourquoi pas
langdetect : il est probabiliste (résultats variables sans graine), se trompe sur un cours mêlé de code SQL ou de formules,
et ajoute une dépendance alors que seules deux langues nous intéressent. Les mots vides sont très fréquents (30 à 45 % d'un
texte courant) et les deux listes ne partagent presque rien, ce qui donne un verdict stable même sur un diaporama.
"""

import re

FR = "fr"
EN = "en"
AUTRE = "autre"

MOTS_VIDES_FR = frozenset(
    """le la les un une des du de d l au aux et ou mais donc or ni car que qui quoi dont où ce cet cette ces se sa son ses
    leur leurs mon ma mes ton ta tes notre nos votre vos il elle ils elles nous vous je tu on y est sont était être été
    sera ont avait avoir fait faire peut peuvent pour par sur sous dans avec sans entre vers chez comme plus moins très
    aussi alors ainsi si ne pas jamais rien tout tous toute toutes autre autres même cela ceci ça lors puis après avant
    pendant depuis selon dont lorsque quand""".split()
)
MOTS_VIDES_EN = frozenset(
    """the a an and or but nor so yet that which who whom whose this these those it its he she they them their we our you
    your i my me his her is are was were be been being am do does did have has had having will would can could should may
    might must shall of in on at by for with without from to into onto over under between about as than then there here
    when where while because if not no only also such each every any all some other more most many much very just
    which what how why""".split()
)
# Quelques mots vides d'autres langues proches (espagnol, allemand, italien, portugais) : un texte qui s'y reconnaît mieux
# que dans le français ou l'anglais est « autre » (« de », « la », « que » existent en français ET en espagnol).
MOTS_VIDES_AUTRES = frozenset(
    """el los las una unos unas del al para por con como pero más muy esta este esto sus nuestro está están
    der die das und ist nicht ein eine einer mit von zu den dem auch auf für wird sind oder aber wie
    il lo gli di che è non per sono come più anche questo questa della delle degli nel nella
    os um uma não mais muito também ele ela eles elas seu sua nos às""".split()
)
# Mots présents dans les deux listes (« a », « on », « as »…) : ils ne départagent pas, on les retire des deux.
_COMMUNS = MOTS_VIDES_FR & MOTS_VIDES_EN
_FR = MOTS_VIDES_FR - _COMMUNS
_EN = MOTS_VIDES_EN - _COMMUNS
_AUTRES = MOTS_VIDES_AUTRES - MOTS_VIDES_FR - MOTS_VIDES_EN

_MOTS = re.compile(r"[a-zàâäçéèêëîïôöùûüÿœæ]+(?:['’][a-zàâäçéèêëîïôöùûüÿœæ]+)?", re.IGNORECASE)
_ACCENTS = re.compile(r"[àâçéèêëîïôùûœ]", re.IGNORECASE)

#: Nombre minimal de mots pour se prononcer.
MOTS_MIN = 25
#: Part minimale de mots vides de la langue gagnante, et avance minimale sur l'autre langue (x1,6).
PART_MIN = 0.08
AVANCE_MIN = 1.6


def _mots(texte: str) -> list[str]:
    # « l'enregistrement » compte pour « l » et « enregistrement » : on garde le mot entier et son article.
    mots: list[str] = []
    for brut in _MOTS.findall(texte.lower()):
        mots.extend(brut.replace("’", "'").split("'"))
    return [m for m in mots if m]


def _sans_code(texte: str) -> str:
    """Retire les lignes de code (SQL, Java…) : leurs mots-clés anglais (from, in, is, and…) fausseraient la mesure."""
    return "\n".join(ligne for ligne in texte.splitlines() if not re.search(r"[;{}]|==|=>|\(\)", ligne))


def mesurer(texte: str) -> tuple[float, float, float, int]:
    """(part fr, part en, part autres langues, nombre de mots) : parts de mots vides propres à chaque langue."""
    texte = _sans_code(texte)
    mots = _mots(texte)
    if not mots:
        return 0.0, 0.0, 0.0, 0
    fr = sum(1 for m in mots if m in _FR) / len(mots)
    en = sum(1 for m in mots if m in _EN) / len(mots)
    autres = sum(1 for m in mots if m in _AUTRES) / len(mots)
    accents = min(0.06, len(_ACCENTS.findall(texte)) / max(1, len(texte)))
    return fr + accents, en, autres, len(mots)


def detecter_langue(texte: str, mots_min: int = MOTS_MIN) -> str:
    """"fr", "en" ou "autre" (texte trop court, langue non prise en charge, ou mélange sans langue dominante)."""
    fr, en, autres, n = mesurer(texte)
    if n < mots_min:
        return AUTRE
    if fr >= PART_MIN and fr >= en * AVANCE_MIN and fr >= autres * AVANCE_MIN:
        return FR
    if en >= PART_MIN and en >= fr * AVANCE_MIN and en >= autres * AVANCE_MIN:
        return EN
    return AUTRE
