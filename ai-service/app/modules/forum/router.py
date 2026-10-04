"""Module Forum (IA) — STUB. Propriétaire : membre Forum.

À venir : embeddings, questions similaires, chatbot RAG (texte via GET /api/cours/{id}/texte).
"""

from fastapi import APIRouter, Depends

from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.forum.schemas import QuestionsSimilairesRequest, QuestionsSimilairesResponse

MODULE = "forum"
router = APIRouter(prefix="/api/ai/forum", tags=["forum"])


@router.post("/questions-similaires", response_model=ApiResponse[QuestionsSimilairesResponse])
async def questions_similaires(
    body: QuestionsSimilairesRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[QuestionsSimilairesResponse]:
    question = sanitizer.sanitize(body.question).text
    return ApiResponse.ok(
        QuestionsSimilairesResponse(question=question, similaires=[], stub=True),
        "stub",
    )
