"""Briques communes de la synthèse vocale : résultat, interface d'un fournisseur, erreur."""

import asyncio
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


@dataclass(frozen=True)
class SegmentVoix:
    """Un segment d'un script à mettre en voix : son numéro, qui parle (PROFESSEUR ou ETUDIANT) et le texte."""

    ordre: int
    locuteur: str
    texte: str


@dataclass(frozen=True)
class SonScript:
    """Audio d'un script entier. `minutage` : (ordre, début en ms, fin en ms) de chaque segment.

    `minutage_estime` : true quand le minutage est réparti au prorata du nombre de caractères (un seul appel pour tout le script).
    """

    audio: AudioSynthese
    minutage: list[tuple[int, int, int]]
    duree_ms: int
    minutage_estime: bool


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

    async def synthetiser_script(self, segments: list[SegmentVoix], langue: str = "fr", pause_ms: int = 350) -> SonScript:
        """Audio d'un script entier : un appel par segment (une voix par locuteur), puis assemblage en un seul MP3.

        Les durées de chaque segment sont exactes (lues dans les MP3). Un segment en échec fait échouer tout le script avec
        ce fournisseur : la chaîne recommence alors avec le suivant, car deux fournisseurs ne se mélangent pas dans un audio.
        """
        from app.modules.cours.tts import audio_utils  # import tardif : audio_utils n'a pas besoin de ce module

        sons: list[AudioSynthese] = []
        durees: list[int] = []
        for segment in segments:
            son = await self.synthetiser(segment.texte, langue, segment.locuteur)
            duree = audio_utils.duree_ms(son.donnees, son.mime)
            if not duree:
                raise ErreurFournisseurTTS(f"{self.nom} : durée du segment {segment.ordre} illisible")
            sons.append(son)
            durees.append(duree)
        mp3 = await asyncio.to_thread(audio_utils.assembler_mp3, [s.donnees for s in sons], pause_ms)
        if not mp3:
            raise ErreurFournisseurTTS(f"{self.nom} : assemblage de l'audio impossible")
        minutage, debut = [], 0
        for segment, duree in zip(segments, durees, strict=True):
            minutage.append((segment.ordre, debut, debut + duree))
            debut += duree + pause_ms
        total = audio_utils.duree_ms(mp3, "audio/mpeg") or max(0, debut - pause_ms)
        return SonScript(AudioSynthese(mp3, "audio/mpeg", self.nom), minutage, total, minutage_estime=False)


def resume_erreur(exc: BaseException) -> str:
    """Résumé sans donnée sensible d'une exception, pour les journaux : type et code HTTP éventuel."""
    code = getattr(exc, "status_code", None) or getattr(exc, "status", None) or getattr(exc, "code", None)
    return f"{type(exc).__name__}" + (f" {code}" if isinstance(code, int) else "")
