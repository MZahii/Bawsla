"""Schémas et erreurs de POST /api/ai/cours/vision."""

from pydantic import Field

from app.core.errors import LLMError
from app.core.schemas import CamelModel

MIMES = ("image/png", "image/jpeg", "image/webp")


class VisionRequest(CamelModel):
    cours_id: int
    #: Numéro de la page dans le cours (sert seulement au prompt et aux journaux).
    numero: int = Field(ge=1, le=100_000)
    #: Image en base64 (sans préfixe data:). 8 Mo décodés au plus.
    image_base64: str = Field(min_length=16, max_length=14_000_000)
    mime: str
    #: true : ne pas essayer Gemini pour cette page (le lot a déjà basculé en local : on ne perd pas 90 s par page).
    forcer_local: bool = False


class VisionResponse(CamelModel):
    #: Texte transcrit (emails et téléphones masqués). Vide si la page est vide ou illisible.
    texte: str
    #: true : rien de lisible sur la page.
    vide: bool
    modele: str
    fournisseur: str | None = None
    version_prompt: str


class VisionIndisponibleError(LLMError):
    """Gemini est en quota, en délai dépassé ou indisponible : le secours (Groq, OpenAI) n'est volontairement PAS utilisé pour les images."""

    status_code = 503
    code = "VISION_INDISPONIBLE"

    def __init__(self) -> None:
        super().__init__("Lecture des images indisponible pour le moment, réessaie plus tard")


class ImageInvalideError(LLMError):
    """Image absente, illisible, d'un autre type que celui annoncé, ou trop lourde."""

    status_code = 400
    code = "BAD_REQUEST"
