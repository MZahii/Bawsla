"""POST /api/ai/cours/resume-local : résumé automatique local (modèle Hugging Face sur CPU, ou phrases clés)."""

from fastapi import APIRouter, Depends

from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.local.resumeur_local import MoteurAbstractif, get_moteur, resumer_local
from app.modules.cours.local.schemas import ResumeLocalRequest, ResumeLocalResponse

router = APIRouter(prefix="/resume-local", tags=["cours"])


def get_moteur_abstractif(reglages: CoursAiSettings = Depends(get_cours_ai_settings)) -> MoteurAbstractif:
    """Dépendance FastAPI (remplacée par un faux moteur dans les tests)."""
    return get_moteur(reglages)


@router.post("", response_model=ApiResponse[ResumeLocalResponse])
async def resume_local(
    body: ResumeLocalRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
    moteur: MoteurAbstractif = Depends(get_moteur_abstractif),
) -> ApiResponse[ResumeLocalResponse]:
    """Résume le cours sans clé API. Modes : auto (reformulé, remplacé par les phrases clés s'il est peu fidèle), abstractif, extractif."""
    return ApiResponse.ok(await resumer_local(body, sanitizer, reglages, moteur))
