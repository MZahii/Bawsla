"""Prompts versionnés du module Quiz. La version active est PROMPT_QUIZ_VERSION (v1 par défaut).

Chaque fichier generation_vN.py / verification_vN.py définit VERSION, SYSTEME (consignes) et UTILISATEUR (modèle du message).
Garder les anciennes versions : elles servent de comparaison dans prompts_journal/quiz/.
"""

import logging
from dataclasses import dataclass
from string import Template

from app.modules.quiz.prompts import generation_v1, verification_v1

log = logging.getLogger("bawsla.quiz.prompts")

VERSION_PAR_DEFAUT = "v1"


@dataclass(frozen=True)
class Prompt:
    version: str
    systeme: str
    _utilisateur: str

    def utilisateur(self, **valeurs: str) -> str:
        """Remplit le modèle. Les valeurs sont insérées telles quelles (jamais interprétées comme un modèle)."""
        return Template(self._utilisateur).substitute(**valeurs)


_GENERATIONS = {m.VERSION: Prompt(m.VERSION, m.SYSTEME, m.UTILISATEUR) for m in (generation_v1,)}
_VERIFICATIONS = {m.VERSION: Prompt(m.VERSION, m.SYSTEME, m.UTILISATEUR) for m in (verification_v1,)}


def versions_disponibles() -> list[str]:
    return sorted(_GENERATIONS)


def _choisir(prompts: dict[str, Prompt], version: str | None) -> Prompt:
    if version in prompts:
        return prompts[version]
    log.warning("Version de prompt inconnue %r : repli sur %s", version, VERSION_PAR_DEFAUT)
    return prompts[VERSION_PAR_DEFAUT]


def prompt_generation(version: str | None) -> Prompt:
    """Prompt de génération de la version demandée ; une version inconnue retombe sur la version par défaut."""
    return _choisir(_GENERATIONS, version)


def prompt_verification(version: str | None) -> Prompt:
    return _choisir(_VERIFICATIONS, version)
