"""ElevenLabs : voix de qualité (modèle multilingue : mêmes voix en français et en anglais)."""

from typing import Any

from pydantic import SecretStr

from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur

FORMAT_SORTIE = "mp3_44100_128"


class ElevenLabsTTS(FournisseurTTS):
    nom = "elevenlabs"

    def __init__(
        self,
        cle: SecretStr | None,
        voix_prof: str | None,
        voix_etudiant: str | None,
        modele: str = "eleven_multilingual_v2",
        client: Any | None = None,
    ) -> None:
        self._cle = cle
        self._voix = {PROFESSEUR: voix_prof, ETUDIANT: voix_etudiant or voix_prof}
        self._modele = modele
        self._client = client  # injecté par les tests

    def disponible(self) -> bool:
        return bool(self._cle and self._cle.get_secret_value()) and bool(self._voix[PROFESSEUR])

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        voix = self._voix.get(locuteur) or self._voix[PROFESSEUR]
        try:
            client = self._client or self._creer_client()
            octets = bytearray()
            async for morceau in client.text_to_speech.convert(
                voice_id=voix, text=texte, model_id=self._modele, output_format=FORMAT_SORTIE
            ):
                octets += morceau
        except Exception as exc:  # noqa: BLE001 - ApiError (401, 402, 429…), réseau, délai
            raise ErreurFournisseurTTS(f"ElevenLabs en échec ({resume_erreur(exc)})") from None
        if not octets:
            raise ErreurFournisseurTTS("ElevenLabs n'a renvoyé aucun son")
        return AudioSynthese(bytes(octets), "audio/mpeg", self.nom)

    def _creer_client(self) -> Any:
        from elevenlabs.client import AsyncElevenLabs

        return AsyncElevenLabs(api_key=self._cle.get_secret_value())  # type: ignore[union-attr]
