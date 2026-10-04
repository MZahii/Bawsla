"""Module Cours (IA) — STUB. Propriétaire : membre Cours.

À venir : extraction PDF, résumé, synthèse vocale.
"""

from fastapi import APIRouter, Depends

from app.core.llm_client import LLMClient, LLMMessage, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours.schemas import ResumeRequest, ResumeResponse

MODULE = "cours"
router = APIRouter(prefix="/api/ai/cours", tags=["cours"])


@router.post("/resume", response_model=ApiResponse[ResumeResponse])
async def resumer_cours(
    body: ResumeRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[ResumeResponse]:
    texte = sanitizer.sanitize(body.texte or "").text
    result = await llm.complete(
        [
            LLMMessage(role="system", content="Tu résumes un cours de façon claire et structurée."),
            LLMMessage(role="user", content=texte),
        ],
        module=MODULE,
    )
    return ApiResponse.ok(ResumeResponse(cours_id=body.cours_id, resume=result.text, stub=True), "stub")
