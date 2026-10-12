"""Chaîne de secours de la synthèse vocale : fournisseurs, ordre, repli, conversion WAV vers MP3, route /audio.

Aucun appel réseau : les SDK (ElevenLabs, gTTS, edge-tts) et le LLM sont simulés. La conversion WAV vers MP3 utilise,
elle, le vrai ffmpeg embarqué par imageio-ffmpeg.
"""

import asyncio
import io
import logging
import math
import struct
import wave

import pytest
from elevenlabs.core.api_error import ApiError
from pydantic import SecretStr

from app.core.errors import LLMQuotaError
from app.core.llm_client import AudioResult, RequeteRefuseeError, StubLLMClient
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.tts import (
    AudioSynthese,
    ChaineTTS,
    ErreurFournisseurTTS,
    ETUDIANT,
    PROFESSEUR,
    FournisseurTTS,
    SegmentVoix,
    TtsIndisponibleError,
    audio_utils,
    construire_chaine,
    get_chaine_tts,
)
from app.modules.cours.tts.elevenlabs import ElevenLabsTTS
from app.modules.cours.tts.gemini import GeminiTTS
from app.modules.cours.tts.gtts_fournisseur import GTTSFournisseur
from tests.cours.conftest import UTILISATEUR, FauxLLM

CLE = "cle-elevenlabs-factice-0123456789"
TEXTE_SECRET = "Contenu confidentiel du cours sur les jointures."


def wav_sinus(secondes: float = 1.0, frequence: int = 24000) -> bytes:
    """WAV mono 16 bits d'un la à 440 Hz."""
    echantillons = b"".join(
        struct.pack("<h", int(8000 * math.sin(2 * math.pi * 440 * i / frequence))) for i in range(int(secondes * frequence))
    )
    tampon = io.BytesIO()
    with wave.open(tampon, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(frequence)
        w.writeframes(echantillons)
    return tampon.getvalue()


class FauxFournisseur(FournisseurTTS):
    def __init__(self, nom, *, disponible=True, resultat=None, delai=0.0):
        self.nom = nom
        self._disponible = disponible
        self._resultat = resultat  # None : succès ; sinon l'exception à lever
        self._delai = delai
        self.appels: list[tuple[str, str, str]] = []

    def disponible(self) -> bool:
        return self._disponible

    async def synthetiser(self, texte, langue="fr", locuteur="PROFESSEUR"):
        self.appels.append((texte, langue, locuteur))
        if self._delai:
            await asyncio.sleep(self._delai)
        if self._resultat is not None:
            raise self._resultat
        return AudioSynthese(f"son-{self.nom}".encode(), "audio/mpeg", self.nom)


# ---------------------------------------------------------------- la chaîne


async def test_le_premier_fournisseur_disponible_gagne():
    a, b = FauxFournisseur("a"), FauxFournisseur("b")
    audio = await ChaineTTS([a, b]).synthetiser("Bonjour.", "en", "ETUDIANT")
    assert (audio.fournisseur, audio.donnees, audio.mime) == ("a", b"son-a", "audio/mpeg")
    assert a.appels == [("Bonjour.", "en", "ETUDIANT")] and b.appels == []


async def test_un_fournisseur_non_configure_est_ignore():
    a, b = FauxFournisseur("a", disponible=False), FauxFournisseur("b")
    chaine = ChaineTTS([a, b])
    assert chaine.noms == ["b"]
    assert (await chaine.synthetiser("Bonjour.")).fournisseur == "b"
    assert a.appels == []


async def test_repli_sur_le_suivant_apres_un_echec():
    a = FauxFournisseur("a", resultat=ErreurFournisseurTTS("ElevenLabs en échec (ApiError 429)"))
    b = FauxFournisseur("b", resultat=RuntimeError("panne imprévue"))
    c = FauxFournisseur("c")
    audio = await ChaineTTS([a, b, c]).synthetiser("Bonjour.")
    assert audio.fournisseur == "c" and len(a.appels) == len(b.appels) == len(c.appels) == 1


async def test_un_delai_depasse_passe_au_suivant():
    lent, rapide = FauxFournisseur("lent", delai=5), FauxFournisseur("rapide")
    audio = await ChaineTTS([lent, rapide], delai_secondes=0.05).synthetiser("Bonjour.")
    assert audio.fournisseur == "rapide"


async def test_tous_en_echec():
    chaine = ChaineTTS([FauxFournisseur("a", resultat=ErreurFournisseurTTS("x")), FauxFournisseur("b", disponible=False)])
    with pytest.raises(TtsIndisponibleError):
        await chaine.synthetiser("Bonjour.")
    with pytest.raises(TtsIndisponibleError):
        await ChaineTTS([]).synthetiser("Bonjour.")


async def test_les_journaux_ne_contiennent_ni_cle_ni_texte(caplog):
    caplog.set_level(logging.DEBUG)
    eleven = ElevenLabsTTS(SecretStr(CLE), "voix-prof", None, client=_ClientElevenLabs(erreur=ApiError(headers={}, status_code=401, body={"detail": CLE})))
    audio = await ChaineTTS([eleven, FauxFournisseur("b")]).synthetiser(TEXTE_SECRET)
    assert audio.fournisseur == "b"
    assert "elevenlabs" in caplog.text and "401" in caplog.text  # on sait qui a échoué et pourquoi
    assert CLE not in caplog.text and "confidentiel" not in caplog.text


# ---------------------------------------------------------------- construction depuis la configuration


def reglages(**valeurs) -> CoursAiSettings:
    return CoursAiSettings(_env_file=None, **valeurs)


def test_ordre_de_la_configuration_noms_inconnus_et_doublons():
    chaine = construire_chaine(reglages(tts_fournisseurs=" gtts , Edge,inconnu,gtts"), StubLLMClient())
    assert chaine.noms == ["gtts", "edge"]


def test_elevenlabs_sans_cle_ou_sans_voix_est_ignore():
    base = {"tts_fournisseurs": "elevenlabs,gtts"}
    assert construire_chaine(reglages(**base), FauxLLM()).noms == ["gtts"]
    assert construire_chaine(reglages(**base, elevenlabs_api_key="k"), FauxLLM()).noms == ["gtts"]  # pas de voix
    assert construire_chaine(reglages(**base, elevenlabs_api_key="k", elevenlabs_voice_prof="v"), FauxLLM()).noms == ["elevenlabs", "gtts"]
    assert construire_chaine(reglages(**base, elevenlabs_api_key="", elevenlabs_voice_prof="v"), FauxLLM()).noms == ["gtts"]


def test_gemini_est_ignore_quand_le_fournisseur_llm_est_stub():
    base = {"tts_fournisseurs": "gemini,gtts"}
    assert construire_chaine(reglages(**base), StubLLMClient()).noms == ["gtts"]
    assert construire_chaine(reglages(**base), FauxLLM()).noms == ["gemini", "gtts"]


def test_valeurs_par_defaut_de_la_chaine(monkeypatch):
    monkeypatch.delenv("TTS_FOURNISSEURS")
    assert reglages().noms_fournisseurs() == ["elevenlabs", "gemini", "gtts", "edge"]


# ---------------------------------------------------------------- ElevenLabs


class _ClientElevenLabs:
    """Simule AsyncElevenLabs : text_to_speech.convert() est un générateur asynchrone de morceaux d'octets."""

    def __init__(self, morceaux=(b"ID3", b"mp3"), erreur=None):
        self.appels: list[dict] = []
        self._morceaux, self._erreur = morceaux, erreur
        self.text_to_speech = self

    async def convert(self, **kwargs):
        self.appels.append(kwargs)
        if self._erreur:
            raise self._erreur
        for m in self._morceaux:
            yield m


async def test_elevenlabs_une_voix_par_locuteur_et_modele_multilingue():
    client = _ClientElevenLabs()
    eleven = ElevenLabsTTS(SecretStr(CLE), "voix-prof", "voix-etudiant", "eleven_multilingual_v2", client=client)
    prof = await eleven.synthetiser("Bonjour.", "en", "PROFESSEUR")
    etudiant = await eleven.synthetiser("Pourquoi ?", "fr", "ETUDIANT")
    assert (prof.donnees, prof.mime, prof.fournisseur) == (b"ID3mp3", "audio/mpeg", "elevenlabs")
    assert client.appels[0] == {"voice_id": "voix-prof", "text": "Bonjour.", "model_id": "eleven_multilingual_v2", "output_format": "mp3_44100_128"}
    assert client.appels[1]["voice_id"] == "voix-etudiant"  # mêmes voix en français et en anglais
    assert etudiant.fournisseur == "elevenlabs"


async def test_elevenlabs_voix_etudiant_absente_reprend_celle_du_professeur():
    client = _ClientElevenLabs()
    await ElevenLabsTTS(SecretStr(CLE), "voix-prof", None, client=client).synthetiser("Pourquoi ?", "fr", "ETUDIANT")
    assert client.appels[0]["voice_id"] == "voix-prof"


@pytest.mark.parametrize("code", [401, 402, 403, 429, 500])
async def test_elevenlabs_erreur_http_sans_fuite(code):
    eleven = ElevenLabsTTS(SecretStr(CLE), "v", None, client=_ClientElevenLabs(erreur=ApiError(headers={}, status_code=code, body={"detail": CLE})))
    with pytest.raises(ErreurFournisseurTTS) as info:
        await eleven.synthetiser("Bonjour.")
    assert str(code) in str(info.value) and CLE not in str(info.value)


async def test_elevenlabs_reponse_vide():
    with pytest.raises(ErreurFournisseurTTS):
        await ElevenLabsTTS(SecretStr(CLE), "v", None, client=_ClientElevenLabs(morceaux=())).synthetiser("Bonjour.")


# ---------------------------------------------------------------- Gemini (+ conversion MP3)


class _LLMAvecVoix(FauxLLM):
    def __init__(self, wav: bytes, erreur=None):
        super().__init__()
        self.wav, self.erreur, self.demandes = wav, erreur, []

    async def synthetiser(self, texte, voix, langue):
        self.demandes.append((texte, voix, langue))
        if self.erreur:
            raise self.erreur
        return AudioResult(data=self.wav, mime_type="audio/wav", model="gemini-tts-test")


async def test_gemini_wav_converti_en_mp3_avec_la_bonne_voix():
    llm = _LLMAvecVoix(wav_sinus(1.0))
    gemini = GeminiTTS(llm, "Kore", "Puck")
    audio = await gemini.synthetiser("Bonjour.", "en", "ETUDIANT")
    assert llm.demandes == [("Bonjour.", "Puck", "en")]
    assert (audio.mime, audio.fournisseur) == ("audio/mpeg", "gemini")
    assert audio.donnees[:3] == b"ID3" or audio.donnees[0] == 0xFF  # vrai MP3
    assert len(audio.donnees) < len(llm.wav) / 4  # beaucoup plus léger que le WAV
    assert 900 <= audio_utils.duree_ms(audio.donnees, audio.mime) <= 1150


async def test_gemini_garde_le_wav_si_la_conversion_echoue(monkeypatch):
    monkeypatch.setattr(audio_utils, "wav_vers_mp3", lambda wav, *a, **k: None)
    llm = _LLMAvecVoix(wav_sinus(0.5))
    audio = await GeminiTTS(llm, "Kore", "Puck").synthetiser("Bonjour.")
    assert (audio.mime, audio.donnees) == ("audio/wav", llm.wav)


async def test_gemini_erreur_du_llm_devient_une_erreur_de_fournisseur():
    gemini = GeminiTTS(_LLMAvecVoix(b"", erreur=LLMQuotaError("Quota atteint")), "Kore", "Puck")
    with pytest.raises(ErreurFournisseurTTS) as info:
        await gemini.synthetiser("Bonjour.")
    assert "LLMQuotaError" in str(info.value)


class _LLMDialogueRefuse(FauxLLM):
    def __init__(self):
        super().__init__()
        self.appels_dialogue = 0

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        self.appels_dialogue += 1
        raise RequeteRefuseeError("Requête refusée par le fournisseur LLM")


async def test_gemini_dialogue_refuse_une_fois_puis_ignore_sans_gaspiller_de_quota(monkeypatch):
    from app.modules.cours.tts import gemini as module_gemini

    monkeypatch.setattr(module_gemini, "_dialogue_refuse", False)
    llm = _LLMDialogueRefuse()
    segments = [SegmentVoix(1, PROFESSEUR, "Bonjour."), SegmentVoix(2, ETUDIANT, "Question ?")]
    for _ in range(2):
        with pytest.raises(ErreurFournisseurTTS):
            await GeminiTTS(llm, "Kore", "Puck").synthetiser_script(segments, "fr")
    assert llm.appels_dialogue == 1  # le second dialogue n'appelle plus Gemini


# ---------------------------------------------------------------- gTTS


class _FauxGTTS:
    appels: list[dict] = []
    erreur: Exception | None = None

    def __init__(self, text, lang, tld):
        _FauxGTTS.appels.append({"text": text, "lang": lang, "tld": tld})

    def write_to_fp(self, fp):
        if _FauxGTTS.erreur:
            raise _FauxGTTS.erreur
        fp.write(b"ID3gtts")


@pytest.fixture
def faux_gtts(monkeypatch):
    import gtts

    _FauxGTTS.appels, _FauxGTTS.erreur = [], None
    monkeypatch.setattr(gtts, "gTTS", _FauxGTTS)
    return _FauxGTTS


async def test_gtts_langue_et_accent_selon_le_locuteur(faux_gtts):
    fournisseur = GTTSFournisseur()
    audio = await fournisseur.synthetiser("Bonjour.", "fr", "PROFESSEUR")
    await fournisseur.synthetiser("Pourquoi ?", "fr", "ETUDIANT")
    await fournisseur.synthetiser("Hello.", "en", "PROFESSEUR")
    assert (audio.donnees, audio.mime, audio.fournisseur) == (b"ID3gtts", "audio/mpeg", "gtts")
    assert [(a["lang"], a["tld"]) for a in faux_gtts.appels] == [("fr", "fr"), ("fr", "ca"), ("en", "com")]


async def test_gtts_erreur_reseau(faux_gtts):
    faux_gtts.erreur = ConnectionError("réseau coupé " + TEXTE_SECRET)
    with pytest.raises(ErreurFournisseurTTS) as info:
        await GTTSFournisseur().synthetiser("Bonjour.")
    assert "confidentiel" not in str(info.value) and "réseau" not in str(info.value)


# ---------------------------------------------------------------- outils audio


def test_wav_vers_mp3_et_duree():
    wav = wav_sinus(2.0)
    mp3 = audio_utils.wav_vers_mp3(wav)
    assert mp3 and len(mp3) < len(wav) / 4
    assert 1900 <= audio_utils.duree_ms(wav, "audio/wav") <= 2100
    assert 1900 <= audio_utils.duree_ms(mp3, "audio/mpeg") <= 2200


def test_outils_audio_tolerent_les_donnees_invalides():
    assert audio_utils.wav_vers_mp3(b"ceci n'est pas un wav") is None
    assert audio_utils.duree_ms(b"rien", "audio/mpeg") is None
    assert audio_utils.duree_ms(b"rien", "audio/wav") is None


# ---------------------------------------------------------------- route /audio avec la chaîne


def brancher(app_, fournisseurs):
    app_.dependency_overrides[get_chaine_tts] = lambda: ChaineTTS(fournisseurs)


def test_audio_type_mime_et_fournisseur_dans_la_reponse(client):
    from app.main import app

    gemini = FauxFournisseur("gemini")
    gemini.synthetiser = _wav_gemini  # type: ignore[method-assign]
    brancher(app, [FauxFournisseur("elevenlabs", resultat=ErreurFournisseurTTS("429")), gemini])
    r = client.post("/api/ai/cours/audio", json={"texte": "Les jointures relient des tables.", "langue": "en"}, headers=UTILISATEUR)
    assert r.status_code == 200
    assert r.headers["content-type"] == "audio/wav" and r.headers["x-tts-fournisseur"] == "gemini"
    assert r.content == b"RIFFwav"


async def _wav_gemini(texte, langue="fr", locuteur="PROFESSEUR"):
    return AudioSynthese(b"RIFFwav", "audio/wav", "gemini")


def test_audio_la_langue_est_transmise_et_vaut_fr_par_defaut(client):
    from app.main import app

    f = FauxFournisseur("a")
    brancher(app, [f])
    client.post("/api/ai/cours/audio", json={"texte": "Bonjour"}, headers=UTILISATEUR)
    client.post("/api/ai/cours/audio", json={"texte": "Hello", "langue": "en"}, headers=UTILISATEUR)
    assert [a[1] for a in f.appels] == ["fr", "en"]
    assert client.post("/api/ai/cours/audio", json={"texte": "Hola", "langue": "es"}, headers=UTILISATEUR).status_code == 400


def test_audio_aucun_fournisseur_ne_repond_503(client):
    from app.main import app

    brancher(app, [FauxFournisseur("a", resultat=ErreurFournisseurTTS("x")), FauxFournisseur("b", disponible=False)])
    r = client.post("/api/ai/cours/audio", json={"texte": "Bonjour"}, headers=UTILISATEUR)
    assert r.status_code == 503 and r.json()["error"] == "TTS_UNAVAILABLE" and r.json()["success"] is False


def test_audio_le_texte_envoye_aux_fournisseurs_est_nettoye(client):
    from app.main import app

    f = FauxFournisseur("a")
    brancher(app, [f])
    client.post("/api/ai/cours/audio", json={"texte": "Écris à prof@exemple.tn ou au 22 123 456. **Merci.**"}, headers=UTILISATEUR)
    lu = f.appels[0][0]
    assert "prof@exemple.tn" not in lu and "22 123 456" not in lu and "*" not in lu
