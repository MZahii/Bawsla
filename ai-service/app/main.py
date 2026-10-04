"""Point d'entrée ai-service (port 8000). Lancer depuis ai-service/ :

    uvicorn app.main:app --reload --port 8000
"""

from fastapi import FastAPI

from app.core.config import get_settings
from app.core.errors import register_exception_handlers
from app.core.schemas import ApiResponse
from app.modules.cours.router import router as cours_router
from app.modules.forum.router import router as forum_router
from app.modules.quiz.router import router as quiz_router
from app.modules.user.router import router as user_router

app = FastAPI(title="Bawsla ai-service", version="0.1.0")
register_exception_handlers(app)

app.include_router(user_router)
app.include_router(cours_router)
app.include_router(quiz_router)
app.include_router(forum_router)


@app.get("/health")
@app.get("/api/ai/health")
async def health() -> ApiResponse[dict]:
    """Santé, publique via la gateway (GET /api/ai/health)."""
    return ApiResponse.ok({"service": "ai-service", "status": "UP", "llmProvider": get_settings().llm_provider})
