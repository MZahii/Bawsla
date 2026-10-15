"""Module Cours (IA). Propriétaire : membre Cours.

- POST /api/ai/cours/resume    : résumé simple (stub historique, voir CONTRATS_API.md)
- POST /api/ai/cours/fiche     : fiche de révision (résumé, mots-clés, notions avec extrait source, score de fidélité)
- POST /api/ai/cours/fidelite  : recalcule le score d'une fiche corrigée à la main (sans LLM)
- POST /api/ai/cours/explication (+ /traduire, /fidelite, /audio) : l'IA réexplique le cours à l'oral (voir explication/)
- POST /api/ai/cours/resume-local : résumé automatique local, sans clé API (modèle Hugging Face ou phrases clés, voir local/)
- POST /api/ai/cours/vision    : transcription d'une page (scan, photo) par Gemini multimodal ; le secours ne lit pas les images
- POST /api/ai/cours/audio     : audio d'un texte, par la chaîne de fournisseurs vocaux (ElevenLabs, Gemini, gTTS, edge-tts)
Tous les appels LLM passent par app.core (LLMClient) ; tout texte est nettoyé par le Sanitizer avant.
"""

import asyncio

from fastapi import APIRouter, Depends
from fastapi.responses import Response

from app.core.config import Settings, get_settings
from app.core.errors import LLMTimeoutError
from app.core.llm_client import LLMClient, LLMMessage, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours import audio, fidelite
from app.modules.cours.tts import ChaineTTS, TtsIndisponibleError, audio_utils, get_chaine_tts
from app.modules.cours.clients_llm import MESSAGE_TROP_LENT, client_dedie
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.explication.routes import router as explication_router
from app.modules.cours.fiche import generer_fiche
from app.modules.cours.local.routes import router as resume_local_router
from app.modules.cours.vision.routes import router as vision_router
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
router.include_router(explication_router)
router.include_router(resume_local_router)
router.include_router(vision_router)


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


def get_llm_fiche(
    llm: LLMClient = Depends(get_llm_client),
    settings: Settings = Depends(get_settings),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> LLMClient:
    """Client LLM de la fiche : délai propre par appel (FICHE_TIMEOUT_SECONDS, 120 s), sans toucher à LLM_TIMEOUT_SECONDS ni au socle."""
    return client_dedie(llm, settings, reglages.fiche_timeout_seconds)


@router.post("/fiche", response_model=ApiResponse[FicheResponse])
async def fiche_de_revision(
    body: FicheRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_fiche),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> ApiResponse[FicheResponse]:
    """Fiche de révision. Budget total de FICHE_BUDGET_SECONDS (300 s) : au-delà, 504 « l'IA met trop de temps à répondre »."""
    try:
        fiche = await asyncio.wait_for(
            generer_fiche(llm, sanitizer, body.texte, body.niveau, reglages=reglages, modele=get_settings().llm_model or "stub"),
            timeout=reglages.fiche_budget_seconds,
        )
    except (TimeoutError, LLMTimeoutError):
        # Délai d'un appel dépassé chez tous les fournisseurs, ou budget global épuisé : un seul message, un seul code (LLM_TIMEOUT, 504).
        raise LLMTimeoutError(MESSAGE_TROP_LENT) from None
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


@router.post("/audio", response_class=Response, responses={200: {"content": {"audio/mpeg": {}, "audio/wav": {}}}})
async def audio_du_texte(
    body: AudioRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
    chaine: ChaineTTS = Depends(get_chaine_tts),
) -> Response:
    """Audio du texte. Le type MIME est celui du fournisseur utilisé, que l'en-tête X-TTS-Fournisseur indique."""
    texte = audio.preparer(sanitizer.sanitize(body.texte).text, reglages.tts_max_chars)
    if not texte:
        raise audio.TexteVideError("Il n'y a aucun texte à lire")
    try:
        son = await chaine.synthetiser(texte, body.langue.value)
    except TtsIndisponibleError:
        raise audio.TtsError("La synthèse vocale est indisponible pour le moment") from None
    entetes = {"X-TTS-Fournisseur": son.fournisseur}
    duree = audio_utils.duree_ms(son.donnees, son.mime)
    if duree:
        entetes["X-TTS-Duree-Ms"] = str(duree)  # durée réelle de l'audio, pour l'afficher sans le décoder
    return Response(content=son.donnees, media_type=son.mime, headers=entetes)
