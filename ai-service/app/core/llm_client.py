"""Client LLM commun : SEUL point d'appel à un LLM dans tout le projet.

Règles :
- Les modules n'importent jamais un SDK LLM directement : ils dépendent de `LLMClient`
  via `Depends(get_llm_client)`.
- Tout texte venant d'un utilisateur passe par `app.core.sanitizer` AVANT d'arriver ici.
- Aucune donnée personnelle (nom, email, identifiant) dans les messages.

État actuel : seul `StubLLMClient` existe (aucun appel réseau). L'implémentation d'un vrai
fournisseur se fait dans ce fichier, par pull request relue.
"""

from abc import ABC, abstractmethod
from typing import Literal, TypeVar

from pydantic import BaseModel, ValidationError

from app.core.config import Settings, get_settings
from app.core.errors import LLMInvalidResponseError, LLMMissingKeyError

T = TypeVar("T", bound=BaseModel)


class LLMMessage(BaseModel):
    role: Literal["system", "user", "assistant"]
    content: str


class LLMResult(BaseModel):
    text: str
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None
    stub: bool = False


class LLMClient(ABC):
    """Interface commune. Toutes les implémentations lèvent UNIQUEMENT des `LLMError`
    (LLMTimeoutError, LLMQuotaError, LLMMissingKeyError, LLMInvalidResponseError)."""

    @abstractmethod
    async def complete(
        self,
        messages: list[LLMMessage],
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        """Renvoie la réponse texte du modèle.

        `module` ("user" | "cours" | "quiz" | "forum") sert au suivi d'usage et aux logs.
        """

    async def complete_json(
        self,
        messages: list[LLMMessage],
        schema: type[T],
        *,
        module: str,
        temperature: float = 0.0,
        max_tokens: int = 2048,
    ) -> T:
        """Demande une réponse JSON et la valide avec `schema` (pydantic).

        Lève `LLMInvalidResponseError` si la réponse n'est pas conforme.
        """
        result = await self.complete(messages, module=module, temperature=temperature, max_tokens=max_tokens)
        try:
            return schema.model_validate_json(_strip_code_fence(result.text))
        except ValidationError as exc:
            raise LLMInvalidResponseError("Réponse du LLM non conforme au schéma attendu") from exc


class StubLLMClient(LLMClient):
    """Implémentation factice : aucun appel réseau, réponse clairement marquée [STUB]."""

    async def complete(
        self,
        messages: list[LLMMessage],
        *,
        module: str,
        temperature: float = 0.2,
        max_tokens: int = 1024,
    ) -> LLMResult:
        return LLMResult(
            text=f"[STUB] Réponse factice du LLM pour le module '{module}' ({len(messages)} message(s) reçu(s)).",
            model="stub",
            stub=True,
        )

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        raise LLMMissingKeyError("Aucun fournisseur LLM configuré (LLM_PROVIDER=stub) : complete_json indisponible")


def _strip_code_fence(text: str) -> str:
    """Retire un éventuel bloc ```json ... ``` autour de la réponse."""
    t = text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else ""
        t = t.rsplit("```", 1)[0]
    return t.strip()


def build_llm_client(settings: Settings) -> LLMClient:
    if settings.llm_provider == "stub":
        return StubLLMClient()
    if settings.llm_api_key is None or not settings.llm_api_key.get_secret_value():
        raise LLMMissingKeyError(f"LLM_API_KEY absente pour le fournisseur '{settings.llm_provider}'")
    # Point d'extension : implémenter le client du fournisseur choisi ici (PR relue).
    raise LLMMissingKeyError(f"Fournisseur LLM '{settings.llm_provider}' non implémenté")


def get_llm_client() -> LLMClient:
    """Dépendance FastAPI : `llm: LLMClient = Depends(get_llm_client)`."""
    return build_llm_client(get_settings())
