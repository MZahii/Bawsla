"""Module Quiz (IA). Propriétaire : membre Quiz.

- POST /api/ai/quiz/generer : QCM générés à partir des objectifs pédagogiques (Few-Shot par niveau de Bloom,
  JSON strict, Self-Consistency). Le module ne lit jamais le texte ni le PDF du cours.
- POST /api/ai/quiz/remedier : diagnostic Chain-of-Thought d'une mauvaise réponse et question de rattrapage.
  Appelée par quiz-service pour chaque mauvaise réponse ; aucune donnée de l'apprenant n'est acceptée.
Tous les appels LLM passent par app.core (LLMClient) ; tout texte est nettoyé par le Sanitizer avant.
Pas de try/except ici : les erreurs LLM (413, 429, 502, 503, 504) sont traduites par les gestionnaires du socle.
"""

from fastapi import APIRouter, Depends, HTTPException

from app.core.llm_client import LLMClient, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.quiz.config import QuizAiSettings, get_quiz_ai_settings
from app.modules.quiz.generation import generer_quiz
from app.modules.quiz.remediation import remedier
from app.modules.quiz.schemas import GenererQuizRequest, GenererQuizResponse, RemedierRequest, RemedierResponse

MODULE = "quiz"
ROLES_GENERATION = ("ENSEIGNANT", "ADMIN")
router = APIRouter(prefix="/api/ai/quiz", tags=["quiz"])


@router.post("/generer", response_model=ApiResponse[GenererQuizResponse], response_model_exclude_none=True)
async def generer_qcm(
    body: GenererQuizRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: QuizAiSettings = Depends(get_quiz_ai_settings),
) -> ApiResponse[GenererQuizResponse]:
    if user.role not in ROLES_GENERATION:
        raise HTTPException(status_code=403, detail="Réservé aux formateurs et aux administrateurs")
    resultat = await generer_quiz(llm, sanitizer, body, reglages=reglages)
    return ApiResponse.ok(resultat)


@router.post("/remedier", response_model=ApiResponse[RemedierResponse], response_model_exclude_none=True)
async def remedier_reponse(
    body: RemedierRequest,
    _user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: QuizAiSettings = Depends(get_quiz_ai_settings),
) -> ApiResponse[RemedierResponse]:
    resultat = await remedier(llm, sanitizer, body, reglages=reglages)
    return ApiResponse.ok(resultat)
