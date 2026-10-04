"""Identité de l'utilisateur, transmise par l'api-gateway (en-têtes X-User-Id / X-User-Role).

L'id sert uniquement aux contrôles d'accès : il ne doit JAMAIS être envoyé au LLM.
"""

from typing import Annotated

from fastapi import Header
from pydantic import BaseModel

from app.core.errors import UnauthorizedError


class CurrentUser(BaseModel):
    id: int
    role: str


def get_current_user(
    x_user_id: Annotated[str | None, Header()] = None,
    x_user_role: Annotated[str | None, Header()] = None,
) -> CurrentUser:
    """Dépendance FastAPI : `user: CurrentUser = Depends(get_current_user)`."""
    if not x_user_id or not x_user_role or not x_user_id.isdigit():
        raise UnauthorizedError("Utilisateur non authentifié (passer par l'api-gateway)")
    return CurrentUser(id=int(x_user_id), role=x_user_role)
