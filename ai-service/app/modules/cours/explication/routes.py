"""Routes de l'explication orale : /api/ai/cours/explication (script), /traduire, /fidelite et /audio."""

import base64

from fastapi import APIRouter, Depends

from app.core.config import Settings, get_settings
from app.core.llm_client import GeminiLLMClient, LLMClient, LLMRoutage, build_llm_client, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.cours import audio, fidelite
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.explication.generation import (
    construire_segments,
    generer_explication,
    nettoyer_pages,
    verifier_segments,
)
from app.modules.cours.explication.schemas import (
    AudioExplicationRequest,
    AudioExplicationResponse,
    ExplicationRequest,
    ExplicationResponse,
    FideliteExplicationRequest,
    FideliteExplicationResponse,
    Locuteur,
    MinutageSegment,
    Mode,
    TraductionRequest,
)
from app.modules.cours.explication.traduction import traduire_explication
from app.modules.cours.tts import ETUDIANT, PROFESSEUR, ChaineTTS, SegmentVoix, TtsIndisponibleError, get_chaine_tts

router = APIRouter(prefix="/explication", tags=["cours"])

_clients_longs: dict[tuple[str | None, float], LLMClient] = {}


def get_llm_explication(
    llm: LLMClient = Depends(get_llm_client),
    settings: Settings = Depends(get_settings),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
) -> LLMClient:
    """Client LLM pour les appels longs du script : même fournisseur et même clé, mais un délai de 150 s à lui.

    Le client commun (LLM_TIMEOUT_SECONDS) reste inchangé pour tous les autres appels. Ce client est construit avec la
    fabrique du socle (`build_llm_client`) : aucun code de core/ n'est modifié et tout passe toujours par lui.
    Le routage Gemini -> OpenAI (LLM_SECOURS) est reconstruit de la même façon, avec le même délai pour les deux fournisseurs.
    Un client qui n'est ni Gemini ni ce routage (stub, simulé dans les tests) est renvoyé tel quel.
    """
    if type(llm) not in (GeminiLLMClient, LLMRoutage):
        return llm
    cle = (settings.llm_model, settings.llm_secours, reglages.explication_timeout_seconds)
    if cle not in _clients_longs:
        _clients_longs[cle] = build_llm_client(settings.model_copy(update={"llm_timeout_seconds": reglages.explication_timeout_seconds}))
    return _clients_longs[cle]


@router.post("", response_model=ApiResponse[ExplicationResponse])
async def expliquer_cours(
    body: ExplicationRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_explication),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
    settings: Settings = Depends(get_settings),
) -> ApiResponse[ExplicationResponse]:
    """Réexplique le cours à l'oral (script en français). L'anglais passe par /traduire, à partir du français validé."""
    return ApiResponse.ok(await generer_explication(llm, sanitizer, body, reglages, settings.llm_model or "stub"))


@router.post("/traduire", response_model=ApiResponse[ExplicationResponse])
async def traduire(
    body: TraductionRequest,
    user: CurrentUser = Depends(get_current_user),
    llm: LLMClient = Depends(get_llm_explication),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    settings: Settings = Depends(get_settings),
) -> ApiResponse[ExplicationResponse]:
    return ApiResponse.ok(await traduire_explication(llm, sanitizer, body, settings.llm_model or "stub"))


@router.post("/fidelite", response_model=ApiResponse[FideliteExplicationResponse])
async def controle_de_fidelite(
    body: FideliteExplicationRequest, user: CurrentUser = Depends(get_current_user), sanitizer: Sanitizer = Depends(get_sanitizer)
) -> ApiResponse[FideliteExplicationResponse]:
    """Recalcule le score d'un script corrigé à la main. Aucun appel au LLM."""
    pages = nettoyer_pages(body.pages, sanitizer)
    segments = sorted(body.segments, key=lambda s: s.ordre)
    controle = verifier_segments(segments, pages, [n.strip() for n in body.notions if n.strip()])
    return ApiResponse.ok(
        FideliteExplicationResponse(
            score_fidelite=controle.score, alertes=controle.alertes, segments=construire_segments(segments, controle),
            nb_mots=fidelite.compter_mots(segments), duree_estimee_secondes=fidelite.duree_estimee_secondes(segments),
        )
    )


@router.post("/audio", response_model=ApiResponse[AudioExplicationResponse])
async def audio_explication(
    body: AudioExplicationRequest,
    user: CurrentUser = Depends(get_current_user),
    sanitizer: Sanitizer = Depends(get_sanitizer),
    reglages: CoursAiSettings = Depends(get_cours_ai_settings),
    chaine: ChaineTTS = Depends(get_chaine_tts),
) -> ApiResponse[AudioExplicationResponse]:
    """Met le script en voix : une voix par locuteur (professeur, étudiant), tous les segments assemblés en un seul MP3."""
    ordonnes = sorted(body.segments, key=lambda s: s.ordre)
    voix: list[SegmentVoix] = []
    for s in ordonnes:
        texte = audio.preparer(sanitizer.sanitize(s.texte).text, reglages.tts_max_chars)
        if texte:  # un segment vide après nettoyage n'est pas lu
            locuteur = ETUDIANT if body.mode == Mode.DIALOGUE and s.locuteur == Locuteur.ETUDIANT else PROFESSEUR
            voix.append(SegmentVoix(s.ordre, locuteur, texte))
    if not voix:
        raise audio.TexteVideError("Il n'y a aucun texte à lire")
    try:
        son = await chaine.synthetiser_script(voix, body.langue.value, reglages.explication_pause_ms)
    except TtsIndisponibleError:
        raise audio.TtsError("La synthèse vocale est indisponible pour le moment") from None

    minutage = {ordre: (debut, fin) for ordre, debut, fin in son.minutage}
    segments, fin_precedente = [], 0
    for s in ordonnes:  # un segment non lu garde une durée nulle à la fin du précédent
        debut, fin = minutage.get(s.ordre, (fin_precedente, fin_precedente))
        segments.append(MinutageSegment(ordre=s.ordre, debut_ms=debut, fin_ms=fin))
        fin_precedente = fin
    return ApiResponse.ok(
        AudioExplicationResponse(
            fournisseur=son.audio.fournisseur, mime=son.audio.mime, audio_base64=base64.b64encode(son.audio.donnees).decode("ascii"),
            duree_ms=son.duree_ms, minutage_estime=son.minutage_estime, segments=segments,
        )
    )
