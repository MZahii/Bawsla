"""Clients LLM du module Cours à délai propre, construits SANS modifier le socle (app/core).

- Fiche de révision et lecture d'images ont chacune leur délai par appel (CoursAiSettings), distinct de LLM_TIMEOUT_SECONDS.
- Sur 504 (DEADLINE_EXCEEDED) ou délai dépassé : AUCUNE relance, le routage bascule directement sur le secours.
  Les autres erreurs 5xx (500, 502, 503) ne sont relancées qu'UNE fois.
- Tout passe toujours par les classes du socle (`GeminiLLMClient`, `LLMRoutage`, clients OpenAI/Groq).
"""

import asyncio
import logging

from app.core.config import Settings
from app.core.errors import LLMError, LLMTimeoutError
from app.core.llm_client import (
    GeminiLLMClient,
    LLMClient,
    LLMRoutage,
    build_groq_client,
    build_llm_client,
    build_openai_client,
)

log = logging.getLogger("bawsla.cours.llm")

MESSAGE_TROP_LENT = "L'IA met trop de temps à répondre, réessaie dans un instant"


class GeminiSans504(GeminiLLMClient):
    """Gemini : pas de relance sur 504 ni sur un délai dépassé (le secours prend le relais), une seule relance sur les autres 5xx."""

    MAX_TENTATIVES = 2

    async def _appeler(self, contents, config, module, modele=None):
        for tentative in range(1, self.MAX_TENTATIVES + 1):
            try:
                return await asyncio.wait_for(
                    self._client.aio.models.generate_content(model=modele or self._model, contents=contents, config=config),
                    timeout=self._timeout,
                )
            except LLMError:
                raise
            except Exception as exc:  # noqa: BLE001 - tout est converti en LLMError
                erreur = self._convertir(exc, module)
                code = getattr(exc, "code", None)
                if code == 504:  # DEADLINE_EXCEEDED côté Gemini : même traitement qu'un délai dépassé
                    log.warning("Gemini: 504 DEADLINE_EXCEEDED (module=%s), pas de relance", module)
                    raise LLMTimeoutError("Le fournisseur LLM n'a pas répondu à temps") from None
                relancable = code in (500, 502, 503) and not isinstance(erreur, LLMTimeoutError)
                if relancable and tentative < self.MAX_TENTATIVES:
                    log.warning("Gemini: erreur passagère %s (module=%s), une relance", code, module)
                    await asyncio.sleep(self._attente * tentative)
                    continue
                raise erreur from None
        raise LLMError("Fournisseur LLM indisponible")  # pragma: no cover


def _secours(reglage: Settings) -> LLMClient | None:
    nom = reglage.llm_secours.strip().lower()
    if nom == "openai":
        return build_openai_client(reglage)
    if nom == "groq":
        return build_groq_client(reglage)
    return None


def construire_client(settings: Settings, delai: float, *, avec_secours: bool = True) -> LLMClient:
    """Client Gemini (puis secours éventuel) dont chaque appel a `delai` secondes. Autres fournisseurs : fabrique du socle."""
    reglage = settings.model_copy(update={"llm_timeout_seconds": delai})
    cle = reglage.llm_api_key.get_secret_value() if reglage.llm_api_key else ""
    if reglage.llm_provider != "gemini" or not cle or not reglage.llm_model:
        return build_llm_client(reglage)  # stub, ou erreur claire du socle si la clé manque
    gemini = GeminiSans504(
        cle, reglage.llm_model, timeout_seconds=delai, thinking_budget=reglage.llm_thinking_budget, tts_model=reglage.llm_tts_model
    )
    secours = _secours(reglage) if avec_secours else None
    return LLMRoutage(gemini, secours) if secours else gemini


_dedies: dict[tuple, LLMClient] = {}


def client_dedie(llm: LLMClient, settings: Settings, delai: float, *, avec_secours: bool = True) -> LLMClient:
    """Version à délai propre du client commun (un seul exemplaire par réglage). Un client qui n'est ni Gemini ni ce routage
    (stub, faux client des tests) est renvoyé tel quel."""
    if type(llm) not in (GeminiLLMClient, LLMRoutage):
        return llm
    cle = (settings.llm_model, settings.llm_secours, delai, avec_secours)
    if cle not in _dedies:
        _dedies[cle] = construire_client(settings, delai, avec_secours=avec_secours)
    return _dedies[cle]


def principal_seul(llm: LLMClient) -> LLMClient:
    """Le fournisseur principal sans son secours (lecture d'images : seul Gemini lit les images)."""
    return llm.principal if isinstance(llm, LLMRoutage) else llm
