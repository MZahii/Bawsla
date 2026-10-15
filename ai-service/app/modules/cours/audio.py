"""Préparation du texte à lire et erreurs de la synthèse vocale.

La synthèse elle-même est faite par la chaîne de fournisseurs (voir tts/chaine.py : ElevenLabs, Gemini, gTTS, edge-tts).
Le texte envoyé est déjà nettoyé par le Sanitizer (emails et téléphones masqués) et ne contient aucune donnée
sur l'utilisateur.
"""

import re

from app.core.errors import LLMError

_MARKDOWN = re.compile(r"[*_#`>|~]+")
_ESPACES = re.compile(r"\s+")
_MASQUES = re.compile(r"\[(EMAIL|TELEPHONE)\]")


class TtsError(LLMError):
    """Aucun fournisseur n'a pu produire l'audio. Traduit par le handler d'erreurs du socle."""

    status_code = 503
    code = "TTS_UNAVAILABLE"


class TexteVideError(LLMError):
    """Rien à lire après nettoyage du texte."""

    status_code = 400
    code = "BAD_REQUEST"


def preparer(texte: str, max_chars: int) -> str:
    """Texte lisible à voix haute : sans symboles Markdown ni marqueurs de masquage, coupé à une fin de phrase."""
    propre = _MASQUES.sub("", texte)
    propre = _MARKDOWN.sub(" ", propre)
    propre = _ESPACES.sub(" ", propre).strip()
    if len(propre) <= max_chars:
        return propre
    coupe = propre.rfind(".", 0, max_chars)
    return propre[: coupe + 1] if coupe > max_chars // 2 else propre[:max_chars]
