"""POST /api/ai/cours/vision : transcription d'une page (image) par Gemini. Le secours (Groq) ne lit pas les images : jamais utilisé ici.
Si Gemini est indisponible, secours LOCAL optionnel (Florence-2, VISION_LOCALE_ACTIVE)."""

from fastapi import APIRouter, Depends

from app.core.config import Settings, get_settings
from app.core.llm_client import LLMClient, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours.clients_llm import client_dedie, principal_seul
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.vision.local import MoteurVision, get_moteur_vision
from app.modules.cours.vision.schemas import VisionRequest, VisionResponse
from app.modules.cours.vision.service import lire_page

router = APIRouter(prefix="/vision", tags=["cours"])


def get_llm_vision(
    llm: LLMClient = Depends(get_llm_client),
    settings: Settings = Depends(get_settings),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> LLMClient:
    """Client de la lecture d'images : Gemini seul (sans secours), avec son délai propre (VISION_TIMEOUT_SECONDS, 90 s)."""
    return principal_seul(client_dedie(llm, settings, reglages.vision_timeout_seconds, avec_secours=False))


def get_moteur_local(reglages: CoursAiSettings = Depends(get_cours_ai_settings)) -> MoteurVision:
    """Moteur de lecture locale (Florence-2) : rien n'est chargé tant qu'une page n'en a pas besoin."""
    return get_moteur_vision(reglages)


@router.post("", response_model=ApiResponse[VisionResponse])
async def lire_une_page(
    body: VisionRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_vision),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
    settings: Settings = Depends(get_settings),
    moteur_local: MoteurVision = Depends(get_moteur_local),
) -> ApiResponse[VisionResponse]:
    """Transcrit fidèlement le texte d'une page (scan, photo). Texte nettoyé par le Sanitizer ; aucune donnée sur l'utilisateur envoyée."""
    return ApiResponse.ok(await lire_page(llm, sanitizer, body, reglages, settings.llm_model or "stub", moteur_local))
