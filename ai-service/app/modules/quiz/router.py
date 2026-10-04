"""Module Quiz (IA) — STUB. Propriétaire : membre Quiz.

À venir : génération de QCM par prompt engineering avancé, remédiation.
"""

from fastapi import APIRouter, Depends

from app.core.llm_client import LLMClient, LLMMessage, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.quiz.schemas import GenererQuizRequest, GenererQuizResponse, QuestionQcm

MODULE = "quiz"
router = APIRouter(prefix="/api/ai/quiz", tags=["quiz"])


@router.post("/generer", response_model=ApiResponse[GenererQuizResponse])
async def generer_qcm(
    body: GenererQuizRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[GenererQuizResponse]:
    texte = sanitizer.sanitize(body.texte or "").text
    result = await llm.complete(
        [
            LLMMessage(role="system", content="Tu génères des QCM pédagogiques."),
            LLMMessage(role="user", content=texte),
        ],
        module=MODULE,
    )
    question = QuestionQcm(
        enonce=result.text,
        choix=["Choix A (stub)", "Choix B (stub)", "Choix C (stub)", "Choix D (stub)"],
        bonne_reponse=0,
        notion="stub",
    )
    return ApiResponse.ok(GenererQuizResponse(cours_id=body.cours_id, questions=[question], stub=True), "stub")
