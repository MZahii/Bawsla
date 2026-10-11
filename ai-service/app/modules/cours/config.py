"""Réglages propres au module Cours (lus dans l'environnement, comme le socle)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class CoursAiSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), env_file_encoding="utf-8", extra="ignore")

    # Version du prompt de la fiche de révision : v1, v2 ou v3 (v3 = la plus stricte sur la fidélité au texte).
    prompt_fiche_version: str = "v3"
    # Taille d'un morceau envoyé au LLM (caractères, avant nettoyage) et nombre maximal de morceaux par fiche.
    fiche_morceau_chars: int = 12_000
    fiche_morceaux_max: int = 6
    # Synthèse vocale.
    tts_voice: str = "fr-FR-DeniseNeural"
    tts_max_chars: int = 5_000
    tts_timeout_seconds: float = 60.0


@lru_cache
def get_cours_ai_settings() -> CoursAiSettings:
    return CoursAiSettings()
