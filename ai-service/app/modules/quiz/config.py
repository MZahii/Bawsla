"""Réglages propres au module Quiz (lus dans l'environnement, comme le socle)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class QuizAiSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), env_file_encoding="utf-8", extra="ignore")

    # Version des prompts : v3 = anti-ambiguïté et rattrapage ciblé ; v2 = échelle de difficulté à 5 niveaux ;
    # v1 et v2 restent disponibles pour comparaison.
    prompt_quiz_version: str = "v3"
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
    # Remédiation (IA 2) : température du diagnostic, et vérifications de la question de rattrapage
    # (une seule : une remédiation est lancée pour chaque mauvaise réponse, le quota compte).
    quiz_temperature_remediation: float = 0.3
    quiz_nb_verifications_remediation: int = 1


@lru_cache
def get_quiz_ai_settings() -> QuizAiSettings:
    return QuizAiSettings()
