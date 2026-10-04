"""Modèles communs : JSON en camelCase (comme Java/Angular), Python en snake_case."""

from datetime import UTC, datetime
from typing import Generic, TypeVar

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

T = TypeVar("T")


class CamelModel(BaseModel):
    """Base de TOUS les schémas d'entrée/sortie de l'API."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, serialize_by_alias=True)


class ApiResponse(CamelModel, Generic[T]):
    """Même enveloppe que les services Java : { success, data, message, timestamp }."""

    success: bool = True
    data: T | None = None
    message: str | None = None
    timestamp: datetime = Field(default_factory=lambda: datetime.now(UTC))

    @classmethod
    def ok(cls, data: T, message: str | None = None) -> "ApiResponse[T]":
        return cls(data=data, message=message)
