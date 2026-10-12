"""Fournisseur LLM réellement utilisé pour la requête en cours, à enregistrer avec le résultat (suivi, journal des prompts)."""

from app.core.config import get_settings
from app.core.llm_client import fournisseur_courant


def fournisseur_utilise() -> str:
    """"openai" si le secours a pris le relais ; sinon le fournisseur configuré (LLM_PROVIDER)."""
    return fournisseur_courant() or get_settings().llm_provider
