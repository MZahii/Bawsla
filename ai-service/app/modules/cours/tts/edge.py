"""edge-tts : service en ligne de Microsoft, sans clé. Non officiel : il peut être bloqué (erreur 403), d'où son rang de dernier recours."""

import edge_tts

from app.modules.cours.tts.base import ETUDIANT, PROFESSEUR, AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, resume_erreur

VOIX = {
    "fr": {PROFESSEUR: "fr-FR-DeniseNeural", ETUDIANT: "fr-FR-HenriNeural"},
    "en": {PROFESSEUR: "en-US-JennyNeural", ETUDIANT: "en-US-GuyNeural"},
}


class EdgeTTSFournisseur(FournisseurTTS):
    nom = "edge"

    def disponible(self) -> bool:
        return True

    async def synthetiser(self, texte: str, langue: str = "fr", locuteur: str = PROFESSEUR) -> AudioSynthese:
        voix = VOIX.get(langue, VOIX["fr"])
        octets = bytearray()
        try:
            async for element in edge_tts.Communicate(texte, voix.get(locuteur, voix[PROFESSEUR])).stream():
                if element.get("type") == "audio":
                    octets += element["data"]
        except Exception as exc:  # noqa: BLE001 - réseau, service Microsoft (403…), texte non lisible
            raise ErreurFournisseurTTS(f"edge-tts en échec ({resume_erreur(exc)})") from None
        if not octets:
            raise ErreurFournisseurTTS("edge-tts n'a produit aucun son")
        return AudioSynthese(bytes(octets), "audio/mpeg", self.nom)
