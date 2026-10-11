"""Module Cours (IA). Propriétaire : membre Cours.

- POST /api/ai/cours/resume    : résumé simple (stub historique, voir CONTRATS_API.md)
- POST /api/ai/cours/fiche     : fiche de révision (résumé, mots-clés, notions avec extrait source, score de fidélité)
- POST /api/ai/cours/fidelite  : recalcule le score d'une fiche corrigée à la main (sans LLM)
- POST /api/ai/cours/audio     : audio MP3 d'un texte (edge-tts)
Tous les appels LLM passent par app.core (LLMClient) ; tout texte est nettoyé par le Sanitizer avant.
"""

from fastapi import APIRouter, Depends
from fastapi.responses import Response

from app.core.config import get_settings
from app.core.llm_client import LLMClient, LLMMessage, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours import audio, fidelite
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.fiche import generer_fiche
from app.modules.cours.schemas import (
    AudioRequest,
    FicheRequest,
    FicheResponse,
    FideliteRequest,
    FideliteResponse,
    NotionJustifiee,
    ResumeRequest,
    ResumeResponse,
)

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
    return ApiResponse.ok(
        ResumeResponse(cours_id=body.cours_id, resume=result.text, stub=result.stub), "stub" if result.stub else None
    )


@router.post("/fiche", response_model=ApiResponse[FicheResponse])
async def fiche_de_revision(
    body: FicheRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_client),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> ApiResponse[FicheResponse]:
    fiche = await generer_fiche(
        llm, sanitizer, body.texte, body.niveau, reglages=reglages, modele=get_settings().llm_model or "stub"
    )
    return ApiResponse.ok(fiche)


@router.post("/fidelite", response_model=ApiResponse[FideliteResponse])
async def controle_de_fidelite(
    body: FideliteRequest,
    user: CurrentUser = Depends(get_current_user),
) -> ApiResponse[FideliteResponse]:
    """Aucun appel au LLM : comparaison du texte de la fiche avec celui du cours."""
    f = body.fiche
    resultat = fidelite.evaluer(f.resume, f.mots_cles, f.notions, body.texte)
    return ApiResponse.ok(
        FideliteResponse(
            score_fidelite=resultat.score,
            alertes=resultat.alertes,
            notions=[NotionJustifiee(titre=n.titre, justifiee=n.justifiee) for n in resultat.notions],
        )
    )


@router.post("/audio", response_class=Response, responses={200: {"content": {"audio/mpeg": {}}}})
async def audio_du_texte(
    body: AudioRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> Response:
    texte = audio.preparer(sanitizer.sanitize(body.texte).text, reglages.tts_max_chars)
    if not texte:
        raise audio.TexteVideError("Il n'y a aucun texte à lire")
    mp3 = await audio.synthetiser(texte, reglages.tts_voice, reglages.tts_timeout_seconds)
    return Response(content=mp3, media_type="audio/mpeg")
