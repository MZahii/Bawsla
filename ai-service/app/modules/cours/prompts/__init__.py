"""Prompts versionnés du module Cours. La version active est PROMPT_FICHE_VERSION (v3 par défaut).

Chaque fichier fiche_vN.py définit VERSION, SYSTEME (consignes) et UTILISATEUR (modèle du message, avec $niveau et $texte).
Garder les anciennes versions : elles servent de comparaison dans prompts_journal/cours/.
"""

import logging
from dataclasses import dataclass
from string import Template

from app.modules.cours.prompts import fiche_v1, fiche_v2, fiche_v3, synthese_v1

log = logging.getLogger("bawsla.cours.prompts")

VERSION_PAR_DEFAUT = "v3"


@dataclass(frozen=True)
class Prompt:
    version: str
    systeme: str
    _utilisateur: str

    def utilisateur(self, **valeurs: str) -> str:
        """Remplit le modèle. Les valeurs sont insérées telles quelles (jamais interprétées comme un modèle)."""
        return Template(self._utilisateur).substitute(**valeurs)


_FICHES = {m.VERSION: Prompt(m.VERSION, m.SYSTEME, m.UTILISATEUR) for m in (fiche_v1, fiche_v2, fiche_v3)}
SYNTHESE = Prompt(synthese_v1.VERSION, synthese_v1.SYSTEME, synthese_v1.UTILISATEUR)


def versions_disponibles() -> list[str]:
    return sorted(_FICHES)


def prompt_fiche(version: str | None) -> Prompt:
    """Prompt de la version demandée ; une version inconnue retombe sur la version par défaut."""
    if version in _FICHES:
        return _FICHES[version]
    log.warning("Version de prompt inconnue %r : repli sur %s", version, VERSION_PAR_DEFAUT)
    return _FICHES[VERSION_PAR_DEFAUT]
