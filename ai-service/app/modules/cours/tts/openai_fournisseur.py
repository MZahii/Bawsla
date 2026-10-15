"""OpenAI TTS (`gpt-4o-mini-tts`) par le client du socle. Optionnel : actif seulement s'il est cité dans TTS_FOURNISSEURS."""

from app.core.llm_client import OpenAILLMClient
from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur


class OpenAITTS(FournisseurTTS):
    nom = "openai"

    def __init__(self, client: OpenAILLMClient | None, voix_prof: str, voix_etudiant: str) -> None:
        self._client = client
        self._voix = {PROFESSEUR: voix_prof, ETUDIANT: voix_etudiant}

    def disponible(self) -> bool:
        # Sans OPENAI_API_KEY (ou OPENAI_MODEL), le client n'existe pas : le fournisseur est ignoré.
        return self._client is not None

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        if self._client is None:
            raise ErreurFournisseurTTS("OpenAI TTS : OPENAI_API_KEY absente")
        try:
            son = await self._client.synthetiser(texte, self._voix.get(locuteur, self._voix[PROFESSEUR]), langue)
        except Exception as exc:  # noqa: BLE001 - LLMError (quota, clé, modèle…) ou autre
            raise ErreurFournisseurTTS(f"OpenAI TTS en échec ({resume_erreur(exc)})") from None
        return AudioSynthese(son.data, "audio/mpeg", self.nom)
