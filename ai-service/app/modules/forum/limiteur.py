"""Limite d'appels IA par apprenant sur les routes du Forum.

forum-service relaie le jeton de l'utilisateur : un apprenant peut donc aussi appeler ces routes directement
(via la gateway) et consommer le quota LLM sans passer par les règles du forum. Les rôles ENSEIGNANT et ADMIN
(indexation des cours, tendances) ne sont pas limités. Fenêtre glissante d'une minute, en mémoire (une instance).
"""

import threading
import time
from collections import defaultdict, deque

from fastapi import Depends, HTTPException

from app.core.security import CurrentUser, get_current_user
from app.modules.forum.config import ForumAiSettings, get_forum_ai_settings

FENETRE_SECONDES = 60.0
ROLES_NON_LIMITES = {"ADMIN", "ENSEIGNANT"}


class LimiteurAppels:
    def __init__(self, horloge=time.monotonic):
        self._horloge = horloge
        self._appels: dict[str, deque[float]] = defaultdict(deque)
        self._verrou = threading.Lock()

    def autoriser(self, cle: str, limite: int) -> bool:
        """Enregistre un appel et renvoie False si la limite de la fenêtre est déjà atteinte."""
        if limite <= 0:
            return True
        maintenant = self._horloge()
        with self._verrou:
            appels = self._appels[cle]
            while appels and maintenant - appels[0] >= FENETRE_SECONDES:
                appels.popleft()
            if len(appels) >= limite:
                return False
            appels.append(maintenant)
            return True

    def vider(self) -> None:
        with self._verrou:
            self._appels.clear()


limiteur = LimiteurAppels()


def limiter_appels_ia(
    user: CurrentUser = Depends(get_current_user),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> None:
    if user.role in ROLES_NON_LIMITES:
        return
    if not limiteur.autoriser(str(user.id), reglages.forum_appels_ia_par_minute):
        raise HTTPException(status_code=429, detail="Trop de demandes à l'IA du forum. Réessaie dans une minute.")
