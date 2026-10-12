"""Briques communes de la synthèse vocale : résultat, interface d'un fournisseur, erreur."""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import ClassVar

PROFESSEUR = "PROFESSEUR"
ETUDIANT = "ETUDIANT"


@dataclass(frozen=True)
class AudioSynthese:
    """Son produit par un fournisseur. `fournisseur` : elevenlabs, gemini, gtts ou edge."""

    donnees: bytes
    mime: str
    fournisseur: str


class ErreurFournisseurTTS(Exception):
    """Échec d'UN fournisseur (quota, clé refusée, réseau, délai…). La chaîne passe alors au suivant.

    Le message ne contient jamais la clé, le texte à lire ni de détail technique du fournisseur.
    """


class FournisseurTTS(ABC):
    """Un service de synthèse vocale. Il produit l'audio d'UN texte, avec la voix d'un locuteur."""

    nom: ClassVar[str]

    @abstractmethod
    def disponible(self) -> bool:
        """false si le fournisseur n'est pas configuré (clé absente…) : la chaîne l'ignore."""

    @abstractmethod
    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        """`langue` : "fr" ou "en". `locuteur` : PROFESSEUR ou ETUDIANT. Lève ErreurFournisseurTTS en cas d'échec."""


def resume_erreur(exc: BaseException) -> str:
    """Résumé sans donnée sensible d'une exception, pour les journaux : type et code HTTP éventuel."""
    code = getattr(exc, "status_code", None) or getattr(exc, "status", None) or getattr(exc, "code", None)
    return f"{type(exc).__name__}" + (f" {code}" if isinstance(code, int) else "")
