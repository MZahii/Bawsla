"""Configuration lue depuis l'environnement (ou le .env à la racine du dépôt).

Les secrets (LLM_API_KEY) ne doivent JAMAIS apparaître dans le code ni dans les logs.
"""

from functools import lru_cache

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=("../.env", ".env"),  # racine du dépôt, puis ai-service/
        env_file_encoding="utf-8",
        extra="ignore",
    )

    llm_provider: str = "stub"
    llm_api_key: SecretStr | None = None
    llm_model: str | None = None
    llm_timeout_seconds: float = 30.0

    # Taille max d'un texte envoyé au LLM après nettoyage (caractères).
    sanitizer_max_chars: int = 20_000


@lru_cache
def get_settings() -> Settings:
    return Settings()
