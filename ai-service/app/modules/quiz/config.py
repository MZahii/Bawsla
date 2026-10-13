"""Réglages propres au module Quiz (lus dans l'environnement, comme le socle)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class QuizAiSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), env_file_encoding="utf-8", extra="ignore")

    # Version des prompts de génération et de vérification (v1 seulement pour l'instant).
    prompt_quiz_version: str = "v1"
    # Nombre maximal de questions demandées au LLM en un seul appel (le lot est encore réduit si le budget l'exige).
    quiz_lot_max: int = 5
    # Budget de jetons d'un appel (prompt + sortie) ; 0 = celui du secours Groq (GROQ_TOKENS_PAR_MINUTE).
    quiz_budget_tokens: int = 0
    # Température de la génération.
    quiz_temperature_generation: float = 0.4
    # Self-Consistency : nombre de vérifications indépendantes (une seule divergence suffit à rejeter)
    # et leur température (> 0 pour que les vérifications ne soient pas des copies l'une de l'autre).
    quiz_nb_verifications: int = 2
    quiz_temperature_verification: float = 0.7


@lru_cache
def get_quiz_ai_settings() -> QuizAiSettings:
    return QuizAiSettings()
