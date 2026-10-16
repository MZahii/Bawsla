"""Synthèse vocale du module Cours : un fournisseur par fichier, assemblés en chaîne de secours (voir chaine.py)."""

from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, SegmentVoix, SonScript
from app.modules.cours.tts.chaine import ChaineTTS, TtsIndisponibleError, construire_chaine, get_chaine_tts

__all__ = [
    "AudioSynthese", "ChaineTTS", "ErreurFournisseurTTS", "ETUDIANT", "FournisseurTTS",
    "PROFESSEUR", "SegmentVoix", "SonScript", "TtsIndisponibleError", "construire_chaine", "get_chaine_tts",
]
