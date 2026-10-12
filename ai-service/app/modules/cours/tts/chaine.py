"""Chaîne de secours de la synthèse vocale : on essaie les fournisseurs dans l'ordre configuré (TTS_FOURNISSEURS)."""

import asyncio
import logging

from fastapi import Depends

from app.core.llm_client import LLMClient, get_llm_client
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.tts.base import PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur
from app.modules.cours.tts.edge import EdgeTTSFournisseur
from app.modules.cours.tts.elevenlabs import ElevenLabsTTS
from app.modules.cours.tts.gemini import GeminiTTS
from app.modules.cours.tts.gtts_fournisseur import GTTSFournisseur

log = logging.getLogger("bawsla.cours.tts")


class TtsIndisponibleError(Exception):
    """Aucun fournisseur n'a pu produire l'audio (le routeur la traduit en erreur 503 TTS_UNAVAILABLE)."""


class ChaineTTS:
    def __init__(self, fournisseurs: list[FournisseurTTS], delai_secondes: float = 60.0) -> None:
        self._fournisseurs = fournisseurs
        self._delai = delai_secondes

    @property
    def noms(self) -> list[str]:
        """Fournisseurs réellement utilisables, dans l'ordre d'essai."""
        return [f.nom for f in self._fournisseurs if f.disponible()]

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        """Audio du texte avec le premier fournisseur qui réussit. Les échecs sont journalisés sans clé ni texte."""
        for fournisseur in self._fournisseurs:
            if not fournisseur.disponible():
                log.info("TTS: %s ignoré (non configuré)", fournisseur.nom)
                continue
            try:
                audio = await asyncio.wait_for(fournisseur.synthetiser(texte, langue, locuteur), timeout=self._delai)
            except TimeoutError:
                log.warning("TTS: %s a dépassé le délai, fournisseur suivant", fournisseur.nom)
            except Exception as exc:  # noqa: BLE001 - un fournisseur ne doit jamais faire échouer toute la chaîne
                # ErreurFournisseurTTS a un message sûr (type et code HTTP seulement) ; le reste est résumé.
                raison = str(exc) if isinstance(exc, ErreurFournisseurTTS) else resume_erreur(exc)
                log.warning("TTS: %s en échec (%s), fournisseur suivant", fournisseur.nom, raison)
            else:
                log.info("TTS: audio produit par %s (%d octets)", audio.fournisseur, len(audio.donnees))
                return audio
        raise TtsIndisponibleError("Aucun fournisseur de synthèse vocale n'est disponible")


def construire_chaine(reglages: CoursAiSettings, llm: LLMClient) -> ChaineTTS:
    """Fournisseurs dans l'ordre de TTS_FOURNISSEURS ; un nom inconnu est ignoré."""
    fabriques = {
        "elevenlabs": lambda: ElevenLabsTTS(
            reglages.elevenlabs_api_key, reglages.elevenlabs_voice_prof, reglages.elevenlabs_voice_etudiant, reglages.elevenlabs_model
        ),
        "gemini": lambda: GeminiTTS(llm, reglages.gemini_tts_voice_prof, reglages.gemini_tts_voice_etudiant),
        "gtts": GTTSFournisseur,
        "edge": EdgeTTSFournisseur,
    }
    fournisseurs: list[FournisseurTTS] = []
    for nom in reglages.noms_fournisseurs():
        if nom in fabriques:
            fournisseurs.append(fabriques[nom]())
        else:
            log.warning("TTS_FOURNISSEURS: fournisseur inconnu %r ignoré", nom)
    return ChaineTTS(fournisseurs, reglages.tts_timeout_seconds)


def get_chaine_tts(
    reglages: CoursAiSettings = Depends(get_cours_ai_settings), llm: LLMClient = Depends(get_llm_client)
) -> ChaineTTS:
    """Dépendance FastAPI : `chaine: ChaineTTS = Depends(get_chaine_tts)`."""
    return construire_chaine(reglages, llm)
