"""Réglages propres au module Cours (lus dans l'environnement, comme le socle)."""

from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class CoursAiSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), env_file_encoding="utf-8", extra="ignore")

    # Version du prompt de la fiche de révision : v1, v2 ou v3 (v3 = la plus stricte sur la fidélité au texte).
    prompt_fiche_version: str = "v3"
    # Taille d'un morceau envoyé au LLM (caractères, avant nettoyage) et nombre maximal de morceaux par fiche.
    fiche_morceau_chars: int = 12_000
    fiche_morceaux_max: int = 6

    # ---- Synthèse vocale : chaîne de secours, essayée dans l'ordre. Noms : elevenlabs, gemini, gtts, edge.
    # Un fournisseur sans clé (ou non configuré) est ignoré ; en cas d'échec on passe au suivant.
    tts_fournisseurs: str = "elevenlabs,gemini,gtts,edge"
    tts_max_chars: int = 5_000
    # Délai maximal pour UN fournisseur (au-delà, on passe au suivant).
    tts_timeout_seconds: float = 60.0

    # ElevenLabs : la même voix sert pour le français et l'anglais (modèle multilingue).
    elevenlabs_api_key: SecretStr | None = None
    elevenlabs_voice_prof: str | None = None
    elevenlabs_voice_etudiant: str | None = None
    elevenlabs_model: str = "eleven_multilingual_v2"

    # Voix prédéfinies de Gemini TTS (professeur, étudiant des dialogues).
    gemini_tts_voice_prof: str = "Kore"
    gemini_tts_voice_etudiant: str = "Puck"

    def noms_fournisseurs(self) -> list[str]:
        """Noms de la chaîne dans l'ordre configuré, sans doublon ni espace."""
        return list(dict.fromkeys(n.strip().lower() for n in self.tts_fournisseurs.split(",") if n.strip()))


@lru_cache
def get_cours_ai_settings() -> CoursAiSettings:
    return CoursAiSettings()
