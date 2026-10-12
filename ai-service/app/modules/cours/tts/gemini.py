"""Gemini TTS, par le client LLM du socle (seul core/llm_client.py parle à Gemini). Le WAV est converti en MP3."""

import asyncio

from app.core.llm_client import LLMClient, Replique, RequeteRefuseeError, StubLLMClient
from app.modules.cours.tts import audio_utils
from app.modules.cours.tts.base import (
    ETUDIANT,
    PROFESSEUR,
    AudioSynthese,
    ErreurFournisseurTTS,
    FournisseurTTS,
    SegmentVoix,
    SonScript,
    resume_erreur,
)


# Le modèle de synthèse peut refuser le dialogue à deux voix (le client du socle n'envoie pas `speech_metadata.speaker`).
# Une fois refusé, on ne gaspille plus d'appels (quota) : le dialogue passe directement au fournisseur suivant.
_dialogue_refuse = False


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
        return await self._vers_mp3(audio.data, audio.mime_type)

    async def synthetiser_script(self, segments: list[SegmentVoix], langue: str = "fr", pause_ms: int = 350) -> SonScript:
        """UN seul appel pour tout le script (quota limité) : `synthetiser` pour un seul locuteur, `synthetiser_dialogue` pour deux.

        Le minutage de chaque segment est donc ESTIMÉ : réparti au prorata du nombre de caractères sur la durée réelle de l'audio.
        """
        if not segments:
            raise ErreurFournisseurTTS("Gemini TTS : aucun segment à lire")
        locuteurs = {s.locuteur for s in segments}
        try:
            if len(locuteurs) == 1:
                audio = await self._llm.synthetiser("\n\n".join(s.texte for s in segments), self._voix.get(segments[0].locuteur, self._voix[PROFESSEUR]), langue)
            else:
                global _dialogue_refuse
                if _dialogue_refuse:
                    raise ErreurFournisseurTTS("Gemini TTS : dialogue à deux voix refusé par le modèle (ignoré)")
                repliques = [Replique(locuteur=s.locuteur, texte=s.texte) for s in segments]
                try:
                    audio = await self._llm.synthetiser_dialogue(repliques, self._voix, langue)
                except RequeteRefuseeError:
                    _dialogue_refuse = True
                    raise
        except ErreurFournisseurTTS:
            raise
        except Exception as exc:  # noqa: BLE001 - LLMError (quota, clé, modèle…) ou autre
            raise ErreurFournisseurTTS(f"Gemini TTS en échec ({resume_erreur(exc)})") from None
        son = await self._vers_mp3(audio.data, audio.mime_type)
        duree = audio_utils.duree_ms(son.donnees, son.mime)
        if not duree:
            raise ErreurFournisseurTTS("Gemini TTS : durée de l'audio illisible")
        return SonScript(son, repartir_au_prorata(segments, duree), duree, minutage_estime=True)

    async def _vers_mp3(self, donnees: bytes, mime: str) -> AudioSynthese:
        mp3 = await asyncio.to_thread(audio_utils.wav_vers_mp3, donnees)
        if mp3:
            return AudioSynthese(mp3, "audio/mpeg", self.nom)
        return AudioSynthese(donnees, mime, self.nom)  # conversion impossible : on garde le WAV


def repartir_au_prorata(segments: list[SegmentVoix], duree_ms: int) -> list[tuple[int, int, int]]:
    """Minutage estimé : chaque segment reçoit une part de la durée proportionnelle à son nombre de caractères."""
    total = sum(max(1, len(s.texte)) for s in segments)
    minutage, debut = [], 0.0
    for segment in segments:
        fin = debut + duree_ms * max(1, len(segment.texte)) / total
        minutage.append((segment.ordre, round(debut), round(fin)))
        debut = fin
    return minutage
