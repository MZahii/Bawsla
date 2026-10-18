"""Prompts versionnés du module Quiz. La version active est PROMPT_QUIZ_VERSION (v3 par défaut).

Chaque fichier generation_vN.py / verification_vN.py / remediation_vN.py définit VERSION, SYSTEME (consignes) et UTILISATEUR (modèle du message).
Garder les anciennes versions : elles servent de comparaison dans prompts_journal/quiz/.
"""

import logging
from dataclasses import dataclass
from string import Template

from app.modules.quiz.prompts import (
    generation_v1,
    generation_v2,
    generation_v3,
    remediation_v1,
    remediation_v3,
    verification_v1,
    verification_v3,
)

log = logging.getLogger("bawsla.quiz.prompts")

VERSION_PAR_DEFAUT = "v3"


@dataclass(frozen=True)
class Prompt:
    version: str
    systeme: str
    _utilisateur: str

    def utilisateur(self, **valeurs: str) -> str:
        """Remplit le modèle. Les valeurs sont insérées telles quelles (jamais interprétées comme un modèle)."""
        return Template(self._utilisateur).substitute(**valeurs)


def _prompt(module) -> Prompt:
    return Prompt(module.VERSION, module.SYSTEME, module.UTILISATEUR)


_GENERATIONS = {m.VERSION: _prompt(m) for m in (generation_v1, generation_v2, generation_v3)}
# v2 ne changeait que la génération (échelle de difficulté) : vérification et remédiation y restent celles de v1.
# v3 fait évoluer les trois prompts (anti-ambiguïté, résolution avant le choix, rattrapage ciblé).
_VERIFICATIONS = {"v1": _prompt(verification_v1), "v2": _prompt(verification_v1), "v3": _prompt(verification_v3)}
_REMEDIATIONS = {"v1": _prompt(remediation_v1), "v2": _prompt(remediation_v1), "v3": _prompt(remediation_v3)}


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


def prompt_remediation(version: str | None) -> Prompt:
    return _choisir(_REMEDIATIONS, version)
