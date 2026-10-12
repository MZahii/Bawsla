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
    # Réflexion du modèle (Gemini) : nombre de jetons de « réflexion » autorisés. Bas par défaut : on veut des
    # réponses rapides et peu coûteuses. 0 désactive la réflexion si le modèle l'accepte ; vide = réglage du fournisseur.
    llm_thinking_budget: int | None = 128
    # Modèle de synthèse vocale (Gemini TTS) : LLM_TTS_MODEL. Le fournisseur « gemini » de la chaîne vocale en dépend.
    llm_tts_model: str | None = "gemini-3.8-flash-tts"

    # Fournisseur de SECOURS : vide = aucun ; "openai" = on bascule sur OpenAI quand Gemini répond 429 (quota) ou est
    # indisponible. OPENAI_API_KEY est requise (sinon le secours est ignoré, avec un avertissement dans les logs).
    llm_secours: str = ""
    openai_api_key: SecretStr | None = None
    # Modèle économique (doc OpenAI, developers.openai.com/api/docs/models) : gpt-6-luna, 0,10 $ / 0,50 $ par million de jetons.
    openai_model: str | None = "gpt-6-luna"
    # Effort de réflexion (none, low, medium, high…) : bas pour rester rapide et économique.
    openai_reasoning_effort: str | None = "low"
    openai_tts_model: str = "gpt-4o-mini-tts"

    # Taille max d'un texte envoyé au LLM après nettoyage (caractères).
    sanitizer_max_chars: int = 20_000


@lru_cache
def get_settings() -> Settings:
    return Settings()
