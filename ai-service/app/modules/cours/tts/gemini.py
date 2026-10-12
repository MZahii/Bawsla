"""Gemini TTS, par le client LLM du socle (seul core/llm_client.py parle à Gemini). Le WAV est converti en MP3."""

import asyncio

from app.core.llm_client import LLMClient, StubLLMClient
from app.modules.cours.tts import audio_utils
from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur


class GeminiTTS(FournisseurTTS):
    nom = "gemini"

    def __init__(self, llm: LLMClient, voix_prof: str, voix_etudiant: str) -> None:
        self._llm = llm
        self._voix = {PROFESSEUR: voix_prof, ETUDIANT: voix_etudiant}

    def disponible(self) -> bool:
        # Avec LLM_PROVIDER=stub, aucun modèle n'est configuré : on ne tente même pas l'appel.
        return not isinstance(self._llm, StubLLMClient)

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        try:
            audio = await self._llm.synthetiser(texte, self._voix.get(locuteur, self._voix[PROFESSEUR]), langue)
        except Exception as exc:  # noqa: BLE001 - LLMError (quota, clé, modèle…) ou autre
            raise ErreurFournisseurTTS(f"Gemini TTS en échec ({resume_erreur(exc)})") from None
        mp3 = await asyncio.to_thread(audio_utils.wav_vers_mp3, audio.data)
        if mp3:
            return AudioSynthese(mp3, "audio/mpeg", self.nom)
        return AudioSynthese(audio.data, audio.mime_type, self.nom)  # conversion impossible : on garde le WAV
