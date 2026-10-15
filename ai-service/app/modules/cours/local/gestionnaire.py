"""Gestionnaire commun des modèles Hugging Face locaux du module Cours (résumé reformulé et lecture d'images).

- UN SEUL modèle en mémoire à la fois dans tout le processus : charger celui d'un autre usage décharge le précédent.
- UN seul thread d'inférence : chargements et inférences passent l'un après l'autre. Un résumé et une lecture d'image demandés en même
  temps s'exécutent à la suite, dans l'ordre d'arrivée (aucun n'est perdu, l'un attend l'autre).
- Un modèle inactif depuis `delai` secondes est déchargé (minuteur démon).
"""

import asyncio
import gc
import logging
import threading
import time
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor
from typing import Any, TypeVar

log = logging.getLogger("bawsla.cours.local")

T = TypeVar("T")


class GestionnaireModele:
    def __init__(self) -> None:
        self._executeur: ThreadPoolExecutor | None = None
        self.verrou = threading.Lock()
        #: (clé du modèle, objet 1, objet 2) : (nom, tokenizer, modèle) pour le résumé, (nom, processeur, modèle) pour la vision.
        self.charge: tuple[str, Any, Any] | None = None
        self.derniere_utilisation = 0.0
        self._etiquette = "Modèle local"
        self._minuteur: threading.Timer | None = None

    @property
    def modele_charge(self) -> str | None:
        return self.charge[0] if self.charge else None

    async def executer(self, fonction: Callable[..., T], *args: Any) -> T:
        """Exécute `fonction` sur l'unique thread d'inférence, sans bloquer la boucle FastAPI."""
        if self._executeur is None:
            self._executeur = ThreadPoolExecutor(max_workers=1, thread_name_prefix="modele-local")
        return await asyncio.get_running_loop().run_in_executor(self._executeur, fonction, *args)

    def obtenir(self, nom: str, fabrique: Callable[[], tuple[Any, Any]], etiquette: str) -> tuple[Any, Any]:
        """Objets du modèle `nom` ; décharge un autre modèle d'abord (jamais deux en mémoire). `fabrique` charge le modèle voulu."""
        with self.verrou:
            if self.charge and self.charge[0] == nom:
                return self.charge[1], self.charge[2]
            if self.charge:
                log.info("%s : déchargement de %s avant de charger %s", etiquette, self.charge[0], nom)
                self._decharger()
            debut = time.monotonic()
            premier, second = fabrique()
            self.charge = (nom, premier, second)
            self._etiquette = etiquette
            log.info("%s : modèle %s chargé en %.1f s", etiquette, nom, time.monotonic() - debut)
            return premier, second

    def utilise(self, delai: int) -> None:
        """À appeler après chaque inférence : date l'usage et arme le déchargement après inactivité."""
        self.derniere_utilisation = time.monotonic()
        self._armer(delai)

    def _decharger(self) -> None:
        self.charge = None
        gc.collect()

    def _armer(self, delai: int) -> None:
        if delai <= 0:
            return
        if self._minuteur:
            self._minuteur.cancel()
        self._minuteur = threading.Timer(delai, self.liberer_si_inactif, args=(delai,))
        self._minuteur.daemon = True
        self._minuteur.start()

    def liberer_si_inactif(self, delai: int, maintenant: float | None = None) -> bool:
        """Décharge le modèle s'il est inactif depuis assez longtemps. Renvoie True si la mémoire a été libérée."""
        with self.verrou:
            if not self.charge or delai <= 0:
                return False
            ecoule = (maintenant if maintenant is not None else time.monotonic()) - self.derniere_utilisation
            if ecoule < delai:
                return False
            log.info("%s : modèle %s inactif depuis %.0f s, déchargé", self._etiquette, self.charge[0], ecoule)
            self._decharger()
            return True


_gestionnaire: GestionnaireModele | None = None


def get_gestionnaire() -> GestionnaireModele:
    """Gestionnaire unique du processus, partagé par le résumé local et la vision locale."""
    global _gestionnaire
    if _gestionnaire is None:
        _gestionnaire = GestionnaireModele()
    return _gestionnaire
