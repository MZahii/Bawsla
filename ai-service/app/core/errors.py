"""Erreurs du socle IA et leur traduction au format d'erreur commun Bawsla.

Format : { success:false, status, error, message, path, timestamp, details? }
"""

from datetime import UTC, datetime

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException


class LLMError(Exception):
    """Base de toutes les erreurs liées au LLM. Ne jamais y mettre la clé API ni le prompt complet."""

    status_code: int = 502
    code: str = "LLM_ERROR"

    def __init__(self, message: str = "Erreur du fournisseur LLM") -> None:
        super().__init__(message)
        self.message = message


class LLMTimeoutError(LLMError):
    """Le fournisseur n'a pas répondu dans LLM_TIMEOUT_SECONDS."""

    status_code = 504
    code = "LLM_TIMEOUT"


class LLMQuotaError(LLMError):
    """Quota / rate limit atteint chez le fournisseur."""

    status_code = 429
    code = "LLM_QUOTA_EXCEEDED"


class LLMMissingKeyError(LLMError):
    """Aucune clé API (ou aucun fournisseur) configuré."""

    status_code = 503
    code = "LLM_MISSING_KEY"


class LLMInvalidResponseError(LLMError):
    """Réponse vide, non parsable, ou non conforme au schéma attendu."""

    status_code = 502
    code = "LLM_INVALID_RESPONSE"


class UnauthorizedError(Exception):
    """En-têtes X-User-* absents : la requête n'est pas passée par l'api-gateway."""


def _error_body(status: int, error: str, message: str, path: str, details: dict | None = None) -> dict:
    body = {
        "success": False,
        "status": status,
        "error": error,
        "message": message,
        "path": path,
        "timestamp": datetime.now(UTC).isoformat(),
    }
    if details:
        body["details"] = details
    return body


def register_exception_handlers(app: FastAPI) -> None:
    @app.exception_handler(LLMError)
    async def _llm_error(request: Request, exc: LLMError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_body(exc.status_code, exc.code, exc.message, request.url.path),
        )

    @app.exception_handler(UnauthorizedError)
    async def _unauthorized(request: Request, exc: UnauthorizedError) -> JSONResponse:
        return JSONResponse(
            status_code=401,
            content=_error_body(401, "UNAUTHORIZED", str(exc), request.url.path),
        )

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        details = {".".join(str(p) for p in e["loc"][1:]) or "body": e["msg"] for e in exc.errors()}
        return JSONResponse(
            status_code=400,
            content=_error_body(400, "BAD_REQUEST", "Données invalides", request.url.path, details),
        )

    @app.exception_handler(StarletteHTTPException)
    async def _http(request: Request, exc: StarletteHTTPException) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_error_body(exc.status_code, "HTTP_ERROR", str(exc.detail), request.url.path),
        )
