"""Module User (IA) — STUB. Propriétaire : membre User.

À venir : ML scikit-learn (clustering, classification, recommandation) à partir de
GET /api/quiz/notions-ratees?etudiantId= . Pas de LLM ici a priori.
"""

from fastapi import APIRouter, Depends

from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.user.schemas import RecommandationsRequest, RecommandationsResponse

MODULE = "user"
router = APIRouter(prefix="/api/ai/user", tags=["user"])


@router.post("/recommandations", response_model=ApiResponse[RecommandationsResponse])
async def recommandations(
    body: RecommandationsRequest,
    user: CurrentUser = Depends(get_current_user),
) -> ApiResponse[RecommandationsResponse]:
    return ApiResponse.ok(
        RecommandationsResponse(etudiant_id=body.etudiant_id, profil="stub", cours_recommandes=[], stub=True),
        "stub",
    )
