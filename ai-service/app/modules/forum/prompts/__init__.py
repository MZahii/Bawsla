"""Prompts versionnés de La Vigie. La version active est PROMPT_FORUM_VERSION (v3 par défaut).

Chaque fichier <tache>_vN.py définit VERSION, SYSTEME (rôle, contexte, tâche, format de sortie, contraintes) et
UTILISATEUR (modèle du message, où le texte de l'utilisateur est placé entre balises de données).
Les tâches sont enregistrées au fil des lots : repondre, repondre_general, guider, reformuler, moderer (v1 à v3),
resumer, ameliorer, nommer_groupes (v1, v2). Les anciennes versions sont gardées pour l'évaluation.
"""

import logging
from dataclasses import dataclass
from string import Template
from types import ModuleType

log = logging.getLogger("bawsla.forum.prompts")

VERSION_PAR_DEFAUT = "v3"


@dataclass(frozen=True)
class Prompt:
    tache: str
    version: str
    systeme: str
    _utilisateur: str

    def utilisateur(self, **valeurs: str) -> str:
        """Remplit le modèle. Les valeurs sont insérées telles quelles (jamais interprétées comme un modèle)."""
        return Template(self._utilisateur).substitute(**valeurs)


_PROMPTS: dict[str, dict[str, Prompt]] = {}


def enregistrer(tache: str, *modules: ModuleType) -> None:
    """Enregistre les versions d'une tâche à partir de modules exposant VERSION, SYSTEME et UTILISATEUR."""
    _PROMPTS[tache] = {m.VERSION: Prompt(tache, m.VERSION, m.SYSTEME, m.UTILISATEUR) for m in modules}


def versions_disponibles(tache: str) -> list[str]:
    return sorted(_PROMPTS.get(tache, {}))


def prompt(tache: str, version: str | None) -> Prompt:
    """Prompt de la version demandée ; une version inconnue retombe sur v3, puis sur la plus récente de la tâche."""
    versions = _PROMPTS[tache]
    if version in versions:
        return versions[version]
    repli = VERSION_PAR_DEFAUT if VERSION_PAR_DEFAUT in versions else max(versions)
    log.warning("Version de prompt %r inconnue pour %s : repli sur %s", version, tache, repli)
    return versions[repli]


# Enregistrement des tâches (en fin de fichier : les modules de prompts n'importent rien d'ici).
from app.modules.forum.prompts import (  # noqa: E402
    guider_v1,
    guider_v2,
    guider_v3,
    moderer_v1,
    moderer_v2,
    moderer_v3,
    reformuler_v1,
    reformuler_v2,
    reformuler_v3,
    repondre_general_v1,
    repondre_general_v2,
    repondre_general_v3,
    repondre_v1,
    repondre_v2,
    repondre_v3,
)

enregistrer("reformuler", reformuler_v1, reformuler_v2, reformuler_v3)
enregistrer("repondre", repondre_v1, repondre_v2, repondre_v3)
enregistrer("guider", guider_v1, guider_v2, guider_v3)
enregistrer("repondre_general", repondre_general_v1, repondre_general_v2, repondre_general_v3)
enregistrer("moderer", moderer_v1, moderer_v2, moderer_v3)
