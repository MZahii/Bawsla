"""Lecture d'une page par un modèle multimodal (Gemini seul) : image vérifiée, transcription, texte nettoyé par le Sanitizer."""

import asyncio
import base64
import binascii
import logging

from app.core.errors import LLMError, LLMInvalidResponseError, LLMQuotaError, LLMTimeoutError
from app.core.llm_client import LLMClient, LLMMessage
from app.core.sanitizer import Sanitizer
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.prompts import VISION
from app.modules.cours.vision.local import FOURNISSEUR_LOCAL, LectureLocale, MoteurVision
from app.modules.cours.vision.schemas import (
    MIMES,
    ImageInvalideError,
    VisionIndisponibleError,
    VisionRequest,
    VisionResponse,
)

log = logging.getLogger("bawsla.cours.vision")

MODULE = "cours"
TEMPERATURE = 0.1  # transcrire, pas créer
MAX_TOKENS = 4096  # une page dense avec un tableau en Markdown
#: Un texte réduit à ces marques ne contient rien de lisible.
MARQUES_VIDES = {"", "[illisible]", "[figure]"}


def type_reel(octets: bytes) -> str | None:
    """Type d'image d'après ses premiers octets (PNG, JPEG, WebP), indépendamment de ce que l'envoi annonce."""
    if octets.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if octets.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if octets[:4] == b"RIFF" and octets[8:12] == b"WEBP":
        return "image/webp"
    return None


def decoder_image(requete: VisionRequest, reglages: CoursAiSettings) -> bytes:
    """Octets de l'image, après contrôle du type annoncé, de la taille et de la signature réelle."""
    if requete.mime not in MIMES:
        raise ImageInvalideError("Type d'image non pris en charge : PNG, JPEG ou WebP.")
    try:
        octets = base64.b64decode(requete.image_base64, validate=True)
    except (binascii.Error, ValueError):
        raise ImageInvalideError("L'image n'est pas du base64 valide.") from None
    if not octets:
        raise ImageInvalideError("Image vide.")
    if len(octets) > reglages.vision_image_max_octets:
        raise ImageInvalideError(f"Image trop lourde ({reglages.vision_image_max_octets // (1024 * 1024)} Mo au plus).")
    reel = type_reel(octets)
    if reel is None or reel != requete.mime:
        raise ImageInvalideError("Le contenu du fichier ne correspond pas au type d'image annoncé.")
    return octets


def est_vide(texte: str) -> bool:
    return texte.strip().lower() in MARQUES_VIDES


def peut_basculer_en_local(exc: LLMError) -> bool:
    """Quota (429), délai dépassé ou fournisseur surchargé/indisponible (503) : le local prend le relais. Une clé refusée, un modèle
    introuvable ou une requête invalide ne sont pas des pannes passagères : pas de bascule."""
    return isinstance(exc, LLMQuotaError | LLMTimeoutError) or type(exc) is LLMError


def mettre_en_forme(lecture: LectureLocale) -> str:
    texte = lecture.texte.strip()
    if lecture.figure:
        texte = (texte + "\n\n[Figure : " + lecture.figure + "]").strip()
    return texte


async def lire_en_local(
    moteur: MoteurVision | None, sanitizer: Sanitizer, image: bytes, requete: VisionRequest, reglages: CoursAiSettings
) -> VisionResponse:
    """Lecture par le modèle local. Toute impossibilité (désactivé, dépendances absentes, délai, échec) donne le message habituel."""
    if not reglages.vision_locale_active or moteur is None or not moteur.disponible():
        raise VisionIndisponibleError()
    try:
        lecture = await asyncio.wait_for(moteur.lire(image), timeout=reglages.vision_locale_timeout_seconds)
    except TimeoutError:
        log.warning("Vision locale : page %d non lue dans le délai (%.0f s)", requete.numero, reglages.vision_locale_timeout_seconds)
        raise VisionIndisponibleError() from None
    except Exception as exc:  # noqa: BLE001 - modèle absent du disque, mémoire, code distant : jamais de détail technique au client
        log.warning("Vision locale en échec pour la page %d (%s)", requete.numero, type(exc).__name__)
        raise VisionIndisponibleError() from None
    texte = sanitizer.sanitize(mettre_en_forme(lecture)).text.strip()
    vide = est_vide(texte)
    log.info("Vision locale : page %d lue (%d caractères)", requete.numero, len(texte))
    return VisionResponse(
        texte="" if vide else texte,
        vide=vide,
        modele=reglages.vision_locale_modele,
        fournisseur=FOURNISSEUR_LOCAL,
        version_prompt=VISION.version,
    )


async def lire_page(
    llm: LLMClient,
    sanitizer: Sanitizer,
    requete: VisionRequest,
    reglages: CoursAiSettings,
    modele: str,
    moteur_local: MoteurVision | None = None,
) -> VisionResponse:
    """Gemini d'abord ; s'il est en quota, en délai dépassé ou indisponible, le modèle local (si activé) ; sinon le message habituel."""
    image = decoder_image(requete, reglages)
    if reglages.vision_forcer_local or requete.forcer_local:
        return await lire_en_local(moteur_local, sanitizer, image, requete, reglages)
    messages = [
        LLMMessage(role="system", content=VISION.systeme),
        LLMMessage(role="user", content=VISION.utilisateur(numero=str(requete.numero))),
    ]
    try:
        resultat = await llm.complete_with_image(
            messages, image, requete.mime, module=MODULE, temperature=TEMPERATURE, max_tokens=MAX_TOKENS
        )
    except LLMInvalidResponseError:
        # Réponse vide ou bloquée par le fournisseur : la page n'a rien rendu de lisible (ce n'est pas une panne).
        log.info("Vision : page %d sans texte exploitable (réponse vide)", requete.numero)
        return VisionResponse(texte="", vide=True, modele=modele, fournisseur=None, version_prompt=VISION.version)
    except LLMError as exc:
        # Quota (429), délai dépassé, fournisseur indisponible, clé refusée : un seul message clair, jamais de détail technique.
        log.warning("Vision indisponible (%s)", type(exc).__name__)
        if reglages.vision_locale_active and peut_basculer_en_local(exc):
            log.info("Vision : bascule sur le modèle local pour la page %d", requete.numero)
            return await lire_en_local(moteur_local, sanitizer, image, requete, reglages)
        raise VisionIndisponibleError() from None
    texte = sanitizer.sanitize(resultat.text).text.strip()
    vide = est_vide(texte)
    log.info("Vision : page %d lue (%d caractères, fournisseur %s)", requete.numero, len(texte), resultat.fournisseur)
    return VisionResponse(
        texte="" if vide else texte,
        vide=vide,
        modele=resultat.model or modele,
        fournisseur=resultat.fournisseur,
        version_prompt=VISION.version,
    )
