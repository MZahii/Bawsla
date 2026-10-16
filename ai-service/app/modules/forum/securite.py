"""Défense anti injection du module Forum (plan §4.5).

Tout texte d'utilisateur ou de cours envoyé au LLM :
1. passe par le `Sanitizer` du socle (emails et téléphones masqués, longueur bornée, injection repérée) ;
2. est placé ENTRE BALISES DE DONNÉES (`<question>…</question>`, `<passage id="1">…</passage>`), le prompt système
   rappelant que leur contenu est une donnée, jamais une instruction ;
3. voit ses balises de données neutralisées : un utilisateur ne peut ni fermer la balise ni en ouvrir une fausse.
"""

import re
from dataclasses import dataclass

from app.core.sanitizer import Sanitizer

BALISES_DONNEES = ("question", "passage", "passages", "historique", "message", "messages", "titre", "contenu",
                   "titres", "etiquettes", "fil", "groupe")
_BALISE = re.compile(r"<\s*/?\s*(" + "|".join(BALISES_DONNEES) + r")\b[^>]*>", re.IGNORECASE)
BALISE_RETIREE = "[balise retirée]"


@dataclass(frozen=True)
class TexteSur:
    texte: str
    donnees_masquees: int = 0
    injection_suspectee: bool = False


def neutraliser(texte: str) -> str:
    """Remplace toute balise de données (ouvrante ou fermante) présente dans le texte."""
    return _BALISE.sub(BALISE_RETIREE, texte or "")


def preparer(sanitizer: Sanitizer, texte: str) -> TexteSur:
    """Nettoie (Sanitizer du socle) puis neutralise les balises : texte prêt à être encadré dans un prompt."""
    propre = sanitizer.sanitize(texte or "")
    neutre = neutraliser(propre.text)
    return TexteSur(neutre, propre.pii_masked, propre.injection_suspected or neutre != propre.text)


def encadrer(balise: str, texte: str, **attributs: str | int) -> str:
    """`<balise attr="v">texte</balise>` ; le texte doit déjà avoir été préparé."""
    attrs = "".join(f' {cle}="{valeur}"' for cle, valeur in attributs.items())
    return f"<{balise}{attrs}>\n{texte}\n</{balise}>"
