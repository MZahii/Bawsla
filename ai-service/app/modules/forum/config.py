"""Réglages propres au module Forum (lus dans l'environnement, comme le socle)."""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class ForumAiSettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=("../.env", ".env"), env_file_encoding="utf-8", extra="ignore")

    # Version des prompts de La Vigie (v1, v2, v3) ; une version inconnue retombe sur v3.
    prompt_forum_version: str = "v3"
    # Découpage des cours pour le RAG : taille visée d'un passage et chevauchement, en caractères.
    forum_taille_passage: int = 800
    forum_chevauchement: int = 120
    # Vectorisation : dimension demandée au fournisseur (Gemini accepte output_dimensionality).
    forum_dimension_embedding: int = 768
    # Vérification des citations (rapidfuzz, sans LLM) : score minimal pour qu'une citation compte.
    forum_seuil_citation: int = 90
    # Appels IA par apprenant et par minute sur les routes du forum (0 = sans limite). Un message publié en
    # déclenche 4 ou 5 (modération, reformulation, vectorisation, réponse) : 40 laisse un usage normal passer.
    forum_appels_ia_par_minute: int = 40


@lru_cache
def get_forum_ai_settings() -> ForumAiSettings:
    return ForumAiSettings()
