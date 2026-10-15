"""Module Forum (IA) : La Vigie. Propriétaire : membre Forum.

Routes appelées par forum-service via la gateway (/api/ai/forum/**). Sans état : forum-service stocke les vecteurs,
les passages et les réponses. Aucun identifiant d'utilisateur n'entre dans ces routes (schémas extra="forbid").
"""

from fastapi import APIRouter, Depends

from app.core.llm_client import LLMClient, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.forum import nlp, rag
from app.modules.forum.config import ForumAiSettings, get_forum_ai_settings
from app.modules.forum.schemas import (
    IndexerRequest,
    IndexerResponse,
    QuestionsSimilairesRequest,
    QuestionsSimilairesResponse,
    ReformulerRequest,
    ReformulerResponse,
    RepondreGeneralRequest,
    RepondreGeneralResponse,
    RepondreRequest,
    RepondreResponse,
    VectoriserRequest,
    VectoriserResponse,
)

MODULE = "forum"
router = APIRouter(prefix="/api/ai/forum", tags=["forum"])


@router.post("/vectoriser", response_model=ApiResponse[VectoriserResponse])
async def vectoriser(
    body: VectoriserRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[VectoriserResponse]:
    """Vecteurs normalisés (DOCUMENT pour indexer, REQUETE pour chercher)."""
    return ApiResponse.ok(await nlp.vectoriser(body, llm, sanitizer))


@router.post("/indexer", response_model=ApiResponse[IndexerResponse])
async def indexer(
    body: IndexerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[IndexerResponse]:
    """Découpe un cours en passages (~800 caractères, 120 de chevauchement) et les vectorise."""
    return ApiResponse.ok(await nlp.indexer(body, llm, sanitizer, reglages))


@router.post("/reformuler", response_model=ApiResponse[ReformulerResponse])
async def reformuler(
    body: ReformulerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[ReformulerResponse]:
    """Requête technique autonome en français (derja, arabizi, anglais acceptés) ; repli sans LLM."""
    return ApiResponse.ok(await nlp.reformuler(body, llm, sanitizer, reglages))


@router.post("/repondre", response_model=ApiResponse[RepondreResponse])
async def repondre(
    body: RepondreRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[RepondreResponse]:
    """Réponse UNIQUEMENT à partir des passages fournis, citations vérifiées, confiance calculée, abstention."""
    return ApiResponse.ok(await rag.repondre(body, llm, sanitizer, reglages))


@router.post("/repondre-general", response_model=ApiResponse[RepondreGeneralResponse])
async def repondre_general(
    body: RepondreGeneralRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[RepondreGeneralResponse]:
    """Niveau 3, sur demande de l'apprenant uniquement : sans source ni confiance (forum-service en décide)."""
    return ApiResponse.ok(await rag.repondre_general(body, llm, sanitizer, reglages))


@router.post("/questions-similaires", response_model=ApiResponse[QuestionsSimilairesResponse])
async def questions_similaires(
    body: QuestionsSimilairesRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[QuestionsSimilairesResponse]:
    """Contrat 5 d'origine, conservé tel quel : la recherche de similaires est faite par forum-service
    (POST /api/forum/discussions/similaires), qui possède les discussions et leurs vecteurs."""
    question = sanitizer.sanitize(body.question).text
    return ApiResponse.ok(QuestionsSimilairesResponse(question=question, similaires=[], stub=True), "stub")
