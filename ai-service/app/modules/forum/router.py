"""Module Forum (IA) : La Vigie. Propriétaire : membre Forum.

Routes appelées par forum-service via la gateway (/api/ai/forum/**). Sans état : forum-service stocke les vecteurs,
les passages et les réponses. Aucun identifiant d'utilisateur n'entre dans ces routes (schémas extra="forbid").
"""

from fastapi import APIRouter, Depends

from app.core.llm_client import LLMClient, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.forum import aides, moderation, nlp, rag
from app.modules.forum.config import ForumAiSettings, get_forum_ai_settings
from app.modules.forum.limiteur import limiter_appels_ia
from app.modules.forum.schemas import (
    AmeliorerRequest,
    AmeliorerResponse,
    IndexerRequest,
    IndexerResponse,
    ModererRequest,
    ModererResponse,
    NommerGroupesRequest,
    NommerGroupesResponse,
    QuestionsSimilairesRequest,
    QuestionsSimilairesResponse,
    ReformulerRequest,
    ReformulerResponse,
    RepondreGeneralRequest,
    RepondreGeneralResponse,
    RepondreRequest,
    RepondreResponse,
    ResumerRequest,
    ResumerResponse,
    VectoriserRequest,
    VectoriserResponse,
)

MODULE = "forum"
router = APIRouter(prefix="/api/ai/forum", tags=["forum"])
# Routes qui consomment le LLM : limitées par apprenant (voir limiteur.py).
LIMITE_IA = [Depends(limiter_appels_ia)]


@router.post("/vectoriser", response_model=ApiResponse[VectoriserResponse], dependencies=LIMITE_IA)
async def vectoriser(
    body: VectoriserRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
) -> ApiResponse[VectoriserResponse]:
    """Vecteurs normalisés (DOCUMENT pour indexer, REQUETE pour chercher)."""
    return ApiResponse.ok(await nlp.vectoriser(body, llm, sanitizer))


@router.post("/indexer", response_model=ApiResponse[IndexerResponse], dependencies=LIMITE_IA)
async def indexer(
    body: IndexerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[IndexerResponse]:
    """Découpe un cours en passages (~800 caractères, 120 de chevauchement) et les vectorise."""
    return ApiResponse.ok(await nlp.indexer(body, llm, sanitizer, reglages))


@router.post("/reformuler", response_model=ApiResponse[ReformulerResponse], dependencies=LIMITE_IA)
async def reformuler(
    body: ReformulerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[ReformulerResponse]:
    """Requête technique autonome en français (derja, arabizi, anglais acceptés) ; repli sans LLM."""
    return ApiResponse.ok(await nlp.reformuler(body, llm, sanitizer, reglages))


@router.post("/repondre", response_model=ApiResponse[RepondreResponse], dependencies=LIMITE_IA)
async def repondre(
    body: RepondreRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[RepondreResponse]:
    """Réponse UNIQUEMENT à partir des passages fournis, citations vérifiées, confiance calculée, abstention."""
    return ApiResponse.ok(await rag.repondre(body, llm, sanitizer, reglages))


@router.post("/repondre-general", response_model=ApiResponse[RepondreGeneralResponse], dependencies=LIMITE_IA)
async def repondre_general(
    body: RepondreGeneralRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[RepondreGeneralResponse]:
    """Niveau 3, sur demande de l'apprenant uniquement : sans source ni confiance (forum-service en décide)."""
    return ApiResponse.ok(await rag.repondre_general(body, llm, sanitizer, reglages))


@router.post("/moderer", response_model=ApiResponse[ModererResponse], dependencies=LIMITE_IA)
async def moderer(
    body: ModererRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[ModererResponse]:
    """Catégorie, score et raison : règles sans LLM d'abord, puis LLM ; accepte le message si le LLM est absent."""
    return ApiResponse.ok(await moderation.moderer(body, llm, sanitizer, reglages))


@router.post("/resumer", response_model=ApiResponse[ResumerResponse], dependencies=LIMITE_IA)
async def resumer(
    body: ResumerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[ResumerResponse]:
    """« En bref » d'un fil long : 3 à 5 puces (problème, cause, solution retenue), à partir du fil seulement."""
    return ApiResponse.ok(await aides.resumer(body, llm, sanitizer, reglages))


@router.post("/ameliorer", response_model=ApiResponse[AmeliorerResponse], dependencies=LIMITE_IA)
async def ameliorer(
    body: AmeliorerRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[AmeliorerResponse]:
    """« Améliorer ma question » : titre plus clair, ce qui manque, étiquettes existantes ; règles seules sans LLM."""
    return ApiResponse.ok(await aides.ameliorer(body, llm, sanitizer, reglages))


@router.post("/nommer-groupes", response_model=ApiResponse[NommerGroupesResponse], dependencies=LIMITE_IA)
async def nommer_groupes(
    body: NommerGroupesRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: ForumAiSettings = Depends(get_forum_ai_settings),
) -> ApiResponse[NommerGroupesResponse]:
    """Nom de chaque thème de l'onglet Tendances (groupes calculés par forum-service) ; mots fréquents sans LLM."""
    return ApiResponse.ok(await aides.nommer_groupes(body, llm, sanitizer, reglages))


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
