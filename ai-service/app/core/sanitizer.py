"""Nettoyage des textes AVANT tout envoi au LLM.

Objectifs : aucune donnée personnelle envoyée au LLM, taille bornée, et signalement
des tentatives d'injection de prompt.

État actuel : implémentation MINIMALE (stub) — masquage email/téléphone par regex,
suppression des caractères de contrôle, troncature. À renforcer par PR relue.
"""

import re
from abc import ABC, abstractmethod

from pydantic import BaseModel

from app.core.config import get_settings

_EMAIL = re.compile(r"[\w.+-]+@[\w-]+\.[\w.-]+")
_PHONE = re.compile(r"(?<!\d)(?:\+?\d[\s.-]?){7,14}\d(?!\d)")  # >= 8 chiffres (TN : 8)
_CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_INJECTION_HINTS = (
    "ignore previous instructions",
    "ignore les instructions",
    "ignore all previous",
    "system prompt",
)


class SanitizedText(BaseModel):
    text: str
    truncated: bool = False
    pii_masked: int = 0
    injection_suspected: bool = False


class Sanitizer(ABC):
    @abstractmethod
    def sanitize(self, text: str) -> SanitizedText:
        """Renvoie le texte nettoyé, utilisable dans un prompt."""


class BasicSanitizer(Sanitizer):
    def __init__(self, max_chars: int) -> None:
        self.max_chars = max_chars

    def sanitize(self, text: str) -> SanitizedText:
        cleaned = _CONTROL.sub("", text or "")
        cleaned, n_email = _EMAIL.subn("[EMAIL]", cleaned)
        cleaned, n_phone = _PHONE.subn("[TELEPHONE]", cleaned)
        truncated = len(cleaned) > self.max_chars
        if truncated:
            cleaned = cleaned[: self.max_chars]
        lowered = cleaned.lower()
        return SanitizedText(
            text=cleaned,
            truncated=truncated,
            pii_masked=n_email + n_phone,
            injection_suspected=any(h in lowered for h in _INJECTION_HINTS),
        )


def get_sanitizer() -> Sanitizer:
    """Dépendance FastAPI : `sanitizer: Sanitizer = Depends(get_sanitizer)`."""
    return BasicSanitizer(get_settings().sanitizer_max_chars)
