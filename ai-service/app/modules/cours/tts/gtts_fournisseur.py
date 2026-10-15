"""gTTS : voix de Google Traduction (sans clé, qualité moyenne, une seule voix par langue et par accent)."""

import asyncio
import io

from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur

# Un accent différent par locuteur, faute de voix distinctes.
ACCENTS = {
    "fr": {PROFESSEUR: "fr", ETUDIANT: "ca"},
    "en": {PROFESSEUR: "com", ETUDIANT: "co.uk"},
}


class GTTSFournisseur(FournisseurTTS):
    nom = "gtts"

    def disponible(self) -> bool:
        return True

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        accents = ACCENTS.get(langue, ACCENTS["fr"])
        try:
            octets = await asyncio.to_thread(self._appeler, texte, langue, accents.get(locuteur, accents[PROFESSEUR]))
        except Exception as exc:  # noqa: BLE001 - gTTSError, réseau, 429 de Google
            raise ErreurFournisseurTTS(f"gTTS en échec ({resume_erreur(exc)})") from None
        if not octets:
            raise ErreurFournisseurTTS("gTTS n'a renvoyé aucun son")
        return AudioSynthese(octets, "audio/mpeg", self.nom)

    @staticmethod
    def _appeler(texte: str, langue: str, accent: str) -> bytes:
        from gtts import gTTS

        tampon = io.BytesIO()
        gTTS(text=texte, lang=langue, tld=accent).write_to_fp(tampon)
        return tampon.getvalue()
