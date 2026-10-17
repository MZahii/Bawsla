"""Module User (IA). Propriétaire : membre User. Machine Learning classique, et un LLM qui en explique les résultats.

- POST /api/ai/user/profil : groupe K-Means de chaque apprenant (débutant en difficulté, régulier, décrocheur…).
- POST /api/ai/user/risque : risque d'échec (Random Forest), niveau et raisons. Formateur ou admin uniquement :
  le risque n'est jamais montré à l'apprenant (DESIGN.md).
- POST /api/ai/user/recommandations : cours recommandés (filtrage collaboratif hybride) avec une raison chacun.
- GET  /api/ai/user/metriques : scores des modèles pour le tableau de bord admin.
- POST /api/ai/user/entrainer : nouvel entraînement (admin).

IA générative (LLM du socle, prompts versionnés dans llm/prompts.py) : le ML décide, le LLM explique.
- POST /api/ai/user/conseil : conseil personnalisé de l'apprenant (sans jamais parler de risque).
- POST /api/ai/user/explication-risque : explication du risque et actions, pour le formateur ou l'admin.
- POST /api/ai/user/plan : plan d'étude des 7 prochains jours, dans le temps disponible de l'apprenant.
- POST /api/ai/user/rapport-cohorte : rapport sur des statistiques agrégées de la classe (formateur, admin).
- POST /api/ai/user/domaines : domaines et objectif du parcours depuis une phrase libre.

Les entrées ne sont que des nombres (voir schemas.py) ; les calculs, lourds pour le processeur, tournent hors de la
boucle asynchrone (run_in_threadpool). Le premier appel charge les modèles, ou les entraîne s'ils manquent (≈ 30 s).
"""

import pandas as pd
from fastapi import APIRouter, Depends, HTTPException
from starlette.concurrency import run_in_threadpool

from app.core.llm_client import LLMClient, get_llm_client
from app.core.sanitizer import Sanitizer, get_sanitizer
from app.core.schemas import ApiResponse
from app.core.security import CurrentUser, get_current_user
from app.modules.user.llm import generation
from app.modules.user.llm.schemas import (
    CohorteRequest,
    CohorteResponse,
    ConseilRequest,
    ConseilResponse,
    DomainesRequest,
    DomainesResponse,
    ExplicationRisqueRequest,
    ExplicationRisqueResponse,
    PlanRequest,
    PlanResponse,
)
from app.modules.user.ml import entrainement
from app.modules.user.schemas import (
    CoursRecommande,
    Groupe,
    LotIndicateurs,
    ProfilResponse,
    RecommandationsRequest,
    RecommandationsResponse,
    RisqueApprenant,
    RisqueResponse,
)

MODULE = "user"
ROLES_RISQUE = ("ENSEIGNANT", "ADMIN")
ROLE_ADMIN = "ADMIN"
router = APIRouter(prefix="/api/ai/user", tags=["user"])


def _exiger(user: CurrentUser, *roles: str) -> None:
    if user.role not in roles:
        raise HTTPException(status_code=403, detail="Action réservée aux rôles : " + ", ".join(roles))


def _tableau(lot: LotIndicateurs) -> pd.DataFrame:
    # by_alias : colonnes en camelCase, exactement les noms attendus par les modèles.
    return pd.DataFrame([a.model_dump(by_alias=True) for a in lot.apprenants])


def _simulees(modeles: entrainement.Modeles) -> bool:
    return bool(modeles.metriques.get("donneesSimulees", False))


@router.post("/profil", response_model=ApiResponse[ProfilResponse], response_model_exclude_none=True)
async def profil(body: LotIndicateurs, user: CurrentUser = Depends(get_current_user)) -> ApiResponse[ProfilResponse]:
    def calculer() -> ProfilResponse:
        modeles = entrainement.obtenir()
        groupes = modeles.profils.predire(_tableau(body))
        return ProfilResponse(
            groupes=[Groupe(id=g.id, nom=g.nom, description=g.description) for g in groupes],
            donnees_simulees=_simulees(modeles),
        )

    return ApiResponse.ok(await run_in_threadpool(calculer))


@router.post("/risque", response_model=ApiResponse[RisqueResponse], response_model_exclude_none=True)
async def risque(body: LotIndicateurs, user: CurrentUser = Depends(get_current_user)) -> ApiResponse[RisqueResponse]:
    _exiger(user, *ROLES_RISQUE)

    def calculer() -> RisqueResponse:
        modeles = entrainement.obtenir()
        resultats = modeles.risque.evaluer(_tableau(body))
        return RisqueResponse(
            resultats=[RisqueApprenant(probabilite=r.probabilite, niveau=r.niveau, facteurs=r.facteurs) for r in resultats],
            seuil=modeles.risque.seuil,
            donnees_simulees=_simulees(modeles),
        )

    return ApiResponse.ok(await run_in_threadpool(calculer))


@router.post("/recommandations", response_model=ApiResponse[RecommandationsResponse], response_model_exclude_none=True)
async def recommandations(
    body: RecommandationsRequest,
    user: CurrentUser = Depends(get_current_user),
) -> ApiResponse[RecommandationsResponse]:
    def calculer() -> RecommandationsResponse:
        modeles = entrainement.obtenir()
        disponibles = None
        if body.cours_disponibles is not None:
            disponibles = pd.DataFrame(
                [{"coursId": c.cours_id, "titre": c.titre, "categorie": c.categorie, "niveau": c.niveau}
                 for c in body.cours_disponibles],
                columns=["coursId", "titre", "categorie", "niveau"],
            ).drop_duplicates("coursId").set_index("coursId")
        recos = modeles.recommandation.recommander(
            body.cours_suivis, body.domaines, body.cours_en_difficulte, n=body.nombre, catalogue=body.catalogue,
            cours_disponibles=disponibles,
        )
        return RecommandationsResponse(
            recommandations=[CoursRecommande(cours_id=r.coursId, titre=r.titre, score=r.score, raison=r.raison)
                             for r in recos],
            donnees_simulees=_simulees(modeles),
        )

    return ApiResponse.ok(await run_in_threadpool(calculer))


@router.get("/metriques", response_model=ApiResponse[dict], response_model_exclude_none=True)
async def metriques(user: CurrentUser = Depends(get_current_user)) -> ApiResponse[dict]:
    _exiger(user, ROLE_ADMIN)
    modeles = await run_in_threadpool(entrainement.obtenir)
    return ApiResponse.ok(modeles.metriques)


@router.post("/entrainer", response_model=ApiResponse[dict], response_model_exclude_none=True)
async def entrainer(user: CurrentUser = Depends(get_current_user)) -> ApiResponse[dict]:
    _exiger(user, ROLE_ADMIN)
    modeles = await run_in_threadpool(entrainement.reentrainer)
    return ApiResponse.ok(modeles.metriques, f"Modèles réentraînés en {modeles.metriques['dureeSecondes']} s")


# ---------------------------------------------------------------- IA générative (LLM) : le ML décide, le LLM explique

@router.post("/conseil", response_model=ApiResponse[ConseilResponse], response_model_exclude_none=True)
async def conseil(body: ConseilRequest, user: CurrentUser = Depends(get_current_user),
                  llm: LLMClient = Depends(get_llm_client),
                  sanitizer: Sanitizer = Depends(get_sanitizer)) -> ApiResponse[ConseilResponse]:
    return ApiResponse.ok(await generation.conseil(llm, sanitizer, body))


@router.post("/explication-risque", response_model=ApiResponse[ExplicationRisqueResponse],
             response_model_exclude_none=True)
async def explication_risque(body: ExplicationRisqueRequest, user: CurrentUser = Depends(get_current_user),
                             llm: LLMClient = Depends(get_llm_client),
                             sanitizer: Sanitizer = Depends(get_sanitizer)) -> ApiResponse[ExplicationRisqueResponse]:
    _exiger(user, *ROLES_RISQUE)
    return ApiResponse.ok(await generation.explication_risque(llm, sanitizer, body))


@router.post("/plan", response_model=ApiResponse[PlanResponse], response_model_exclude_none=True)
async def plan(body: PlanRequest, user: CurrentUser = Depends(get_current_user),
               llm: LLMClient = Depends(get_llm_client),
               sanitizer: Sanitizer = Depends(get_sanitizer)) -> ApiResponse[PlanResponse]:
    return ApiResponse.ok(await generation.plan(llm, sanitizer, body))


@router.post("/rapport-cohorte", response_model=ApiResponse[CohorteResponse], response_model_exclude_none=True)
async def rapport_cohorte(body: CohorteRequest, user: CurrentUser = Depends(get_current_user),
                          llm: LLMClient = Depends(get_llm_client),
                          sanitizer: Sanitizer = Depends(get_sanitizer)) -> ApiResponse[CohorteResponse]:
    _exiger(user, *ROLES_RISQUE)
    return ApiResponse.ok(await generation.rapport_cohorte(llm, sanitizer, body))


@router.post("/domaines", response_model=ApiResponse[DomainesResponse], response_model_exclude_none=True)
async def domaines(body: DomainesRequest, user: CurrentUser = Depends(get_current_user),
                   llm: LLMClient = Depends(get_llm_client),
                   sanitizer: Sanitizer = Depends(get_sanitizer)) -> ApiResponse[DomainesResponse]:
    return ApiResponse.ok(await generation.domaines(llm, sanitizer, body))
