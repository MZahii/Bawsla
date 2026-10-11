"""Synthèse vocale d'un texte (edge-tts, voix fr-FR-DeniseNeural) : audio MP3 d'une fiche de révision.

edge-tts n'est PAS un LLM : c'est un service en ligne de Microsoft, sans clé. Le texte envoyé est déjà nettoyé
par le Sanitizer (emails et téléphones masqués) et ne contient aucune donnée sur l'utilisateur.
"""

import asyncio
import logging
import re

import edge_tts

from app.core.errors import LLMError

log = logging.getLogger("bawsla.cours.audio")

_MARKDOWN = re.compile(r"[*_#`>|~]+")
_ESPACES = re.compile(r"\s+")
_MASQUES = re.compile(r"\[(EMAIL|TELEPHONE)\]")


class TtsError(LLMError):
    """Le service de synthèse vocale n'a pas pu produire d'audio. Traduit par le handler d'erreurs du socle."""

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


async def synthetiser(texte: str, voix: str, timeout_seconds: float) -> bytes:
    """MP3 du texte. Lève TtsError si le service est injoignable, trop lent ou ne renvoie rien."""

    async def _flux() -> bytes:
        morceaux = bytearray()
        async for element in edge_tts.Communicate(texte, voix).stream():
            if element.get("type") == "audio":
                morceaux += element["data"]
        return bytes(morceaux)

    try:
        audio = await asyncio.wait_for(_flux(), timeout=timeout_seconds)
    except TimeoutError:
        log.warning("edge-tts: délai dépassé")
        raise TtsError("La synthèse vocale a pris trop de temps") from None
    except Exception as exc:  # noqa: BLE001 - réseau, service Microsoft, texte non lisible…
        log.warning("edge-tts: échec (%s)", type(exc).__name__)
        raise TtsError("La synthèse vocale est indisponible pour le moment") from None
    if not audio:
        raise TtsError("La synthèse vocale n'a produit aucun son")
    return audio
