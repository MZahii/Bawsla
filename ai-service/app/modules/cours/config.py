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

    # ---- Explication orale (script d'une vidéo de cours)
    # Le script prend souvent plus de 60 s : le client LLM dédié à cet appel a son propre délai (LLM_TIMEOUT_SECONDS ne change pas).
    explication_timeout_seconds: float = 150.0
    # Texte du cours envoyé au LLM (caractères) : au-delà, on garde en priorité les pages qui parlent des notions du plan.
    explication_texte_max_chars: int = 60_000
    # En dessous, le cours est jugé vide (PDF scanné, chapitres sans contenu) : erreur claire, aucun appel au LLM.
    explication_texte_min_chars: int = 200
    # Pause entre deux segments dans l'audio assemblé.
    explication_pause_ms: int = 350

    # ---- Synthèse vocale : chaîne de secours, essayée dans l'ordre. Noms : elevenlabs, gemini, openai (optionnel), gtts, edge.
    # Un fournisseur sans clé (ou non configuré) est ignoré ; en cas d'échec on passe au suivant.
    tts_fournisseurs: str = "elevenlabs,gemini,gtts,edge"
    tts_max_chars: int = 5_000
    # Délai maximal pour UN fournisseur (au-delà, on passe au suivant).
    tts_timeout_seconds: float = 60.0
    # Délai maximal pour mettre en voix UN script complet avec UN fournisseur (un appel par segment pour la plupart).
    tts_script_timeout_seconds: float = 180.0

    # ElevenLabs : la même voix sert pour le français et l'anglais (modèle multilingue).
    elevenlabs_api_key: SecretStr | None = None
    elevenlabs_voice_prof: str | None = None
    elevenlabs_voice_etudiant: str | None = None
    elevenlabs_model: str = "eleven_multilingual_v2"

    # Voix prédéfinies de Gemini TTS (professeur, étudiant des dialogues).
    gemini_tts_voice_prof: str = "Kore"
    gemini_tts_voice_etudiant: str = "Puck"

    # Voix OpenAI TTS (professeur, étudiant des dialogues) ; le modèle vient de OPENAI_TTS_MODEL (socle).
    openai_tts_voice_prof: str = "marin"
    openai_tts_voice_etudiant: str = "cedar"

    # ---- Délais de la fiche de révision (démo : réponse en quelques secondes, jamais de blocage de plusieurs minutes)
    # Délai de CHAQUE appel au LLM. Sur 504 ou délai dépassé : pas de relance, bascule directe sur le secours.
    fiche_timeout_seconds: float = 120.0
    # Budget total d'une fiche (tous les appels, secours compris) : au-delà, erreur « l'IA met trop de temps à répondre ».
    fiche_budget_seconds: float = 300.0

    # ---- Lecture d'images par vision (Gemini seul : le secours ne lit pas les images)
    vision_timeout_seconds: float = 90.0
    vision_image_max_octets: int = 8 * 1024 * 1024
    # Lecture d'images LOCALE de secours (Florence-2 sur CPU), quand Gemini est en quota, en délai dépassé ou indisponible.
    # Dépendances : requirements-local.txt. Interrupteur : false = le message « Lecture des images indisponible » comme avant.
    vision_locale_active: bool = False
    # Seuls les modèles de vision/local.py (révision épinglée) sont acceptés : microsoft/Florence-2-base ou microsoft/Florence-2-large.
    vision_locale_modele: str = "microsoft/Florence-2-base"
    # Pour tester : saute Gemini et lit directement en local.
    vision_forcer_local: bool = False
    # Délai d'une page lue en local (le premier appel charge le modèle).
    vision_locale_timeout_seconds: float = 120.0

    # ---- Résumé automatique LOCAL (modèles Hugging Face sur CPU, sans clé API). Dépendances : requirements-local.txt.
    # Interrupteur : false = la route répond 503 « Résumé local indisponible sur ce serveur ».
    resume_local_actif: bool = False
    resume_local_modele_fr: str = "moussaKam/barthez-orangesum-abstract"
    resume_local_modele_en: str = "sshleifer/distilbart-cnn-12-6"
    # Sous ce score (0-100), le résumé reformulé est jugé peu fidèle et remplacé par le résumé extractif (mode auto).
    resume_local_seuil: int = 50
    # Similarité minimale (0-100) pour qu'une phrase du résumé soit « justifiée » par une phrase du cours.
    resume_local_seuil_phrase: int = 55
    # Un modèle inactif depuis ce nombre de secondes est déchargé de la mémoire (0 = jamais).
    resume_local_liberer_apres_secondes: int = 600
    # Nombre maximal de morceaux de cours résumés (au-delà : alerte « cours tronqué »).
    resume_local_morceaux_max: int = 12

    def noms_fournisseurs(self) -> list[str]:
        """Noms de la chaîne dans l'ordre configuré, sans doublon ni espace."""
        return list(dict.fromkeys(n.strip().lower() for n in self.tts_fournisseurs.split(",") if n.strip()))


@lru_cache
def get_cours_ai_settings() -> CoursAiSettings:
    return CoursAiSettings()
