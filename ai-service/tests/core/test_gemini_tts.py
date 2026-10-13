"""Synthèse vocale Gemini (synthetiser, synthetiser_dialogue) avec un SDK simulé : aucun appel réseau."""

import io
import logging
import wave
from types import SimpleNamespace

import pytest

from app.core.config import Settings
from app.core.errors import LLMError, LLMInvalidResponseError, LLMMissingKeyError, LLMQuotaError
from app.core.llm_client import (
    GeminiLLMClient,
    Replique,
    StubLLMClient,
    build_llm_client,
)
from tests.core.test_gemini_client import CLE_SECRETE, FauxModeles, erreur_api

PCM = b"\x01\x00\xff\xff" * 12_000  # 12 000 échantillons 16 bits (0,5 s à 24 kHz)
MIME_PCM = "audio/L16;codec=pcm;rate=24000"


def reponse_audio(donnees=PCM, mime=MIME_PCM):
    partie = SimpleNamespace(inline_data=SimpleNamespace(data=donnees, mime_type=mime), text=None)
    return SimpleNamespace(candidates=[SimpleNamespace(content=SimpleNamespace(parts=[partie]))], usage_metadata=None, text=None)


def tts(*scenario, **options) -> tuple[GeminiLLMClient, FauxModeles]:
    modeles = FauxModeles(scenario)
    client = SimpleNamespace(aio=SimpleNamespace(models=modeles))
    options.setdefault("tts_model", "gemini-tts-test")
    return GeminiLLMClient(CLE_SECRETE, "gemini-texte-test", client=client, attente_entre_tentatives=0, **options), modeles


def lire_wav(octets: bytes):
    with wave.open(io.BytesIO(octets)) as w:
        return w.getnchannels(), w.getsampwidth(), w.getframerate(), w.getnframes()


async def test_synthese_simple_emballe_le_pcm_en_wav():
    llm, modeles = tts(reponse_audio())
    audio = await llm.synthetiser("Bonjour à tous.", "Kore", "fr")
    assert audio.mime_type == "audio/wav" and audio.model == "gemini-tts-test" and audio.stub is False
    assert lire_wav(audio.data) == (1, 2, 24000, 24_000)
    appel = modeles.appels[0]
    assert appel["model"] == "gemini-tts-test"  # le modèle de synthèse, pas celui du texte
    config = appel["config"]
    assert list(config.response_modalities) == ["AUDIO"]
    assert config.speech_config.voice_config.prebuilt_voice_config.voice_name == "Kore"
    assert config.speech_config.language_code == "fr-FR"
    assert appel["contents"][0].parts[0].text == "Bonjour à tous."


async def test_la_frequence_vient_du_type_mime():
    llm, _ = tts(reponse_audio(mime="audio/L16;codec=pcm;rate=16000"))
    assert lire_wav((await llm.synthetiser("Bonjour.", "Kore", "fr")).data)[2] == 16000


async def test_un_wav_deja_emballe_est_conserve():
    llm, _ = tts(reponse_audio(donnees=b"RIFF....WAVE", mime="audio/wav"))
    assert (await llm.synthetiser("Bonjour.", "Kore", "fr")).data == b"RIFF....WAVE"


async def test_langue_anglaise():
    llm, modeles = tts(reponse_audio())
    await llm.synthetiser("Hello everyone.", "Puck", "en")
    assert modeles.appels[0]["config"].speech_config.language_code == "en-US"


async def test_dialogue_en_un_seul_appel_avec_une_voix_par_locuteur():
    llm, modeles = tts(reponse_audio())
    repliques = [
        Replique(locuteur="PROFESSEUR", texte="Une jointure combine deux tables."),
        Replique(locuteur="ETUDIANT", texte="Et si une ligne n'a pas de correspondance ?"),
        Replique(locuteur="PROFESSEUR", texte="Alors elle disparaît d'un INNER JOIN."),
    ]
    audio = await llm.synthetiser_dialogue(repliques, {"PROFESSEUR": "Kore", "ETUDIANT": "Puck"}, "fr")
    assert lire_wav(audio.data)[3] == 24_000
    assert len(modeles.appels) == 1  # un seul appel pour tout le script
    config = modeles.appels[0]["config"].speech_config
    voix = {v.speaker: v.voice_config.prebuilt_voice_config.voice_name for v in config.multi_speaker_voice_config.speaker_voice_configs}
    assert voix == {"PROFESSEUR": "Kore", "ETUDIANT": "Puck"}
    texte = modeles.appels[0]["contents"][0].parts[0].text
    assert texte.splitlines()[0] == "PROFESSEUR: Une jointure combine deux tables."
    assert texte.splitlines()[1].startswith("ETUDIANT: ")


async def test_dialogue_a_un_seul_locuteur_devient_une_synthese_simple():
    llm, modeles = tts(reponse_audio())
    await llm.synthetiser_dialogue(
        [Replique(locuteur="PROFESSEUR", texte="Première phrase."), Replique(locuteur="PROFESSEUR", texte="Deuxième phrase.")],
        {"PROFESSEUR": "Kore", "ETUDIANT": "Puck"}, "fr",
    )
    config = modeles.appels[0]["config"].speech_config
    assert config.multi_speaker_voice_config is None and config.voice_config.prebuilt_voice_config.voice_name == "Kore"
    assert modeles.appels[0]["contents"][0].parts[0].text == "Première phrase. Deuxième phrase."


@pytest.mark.parametrize(
    "repliques,voix",
    [
        ([], {"A": "Kore"}),
        ([Replique(locuteur="A", texte=" ")], {"A": "Kore"}),
        ([Replique(locuteur=n, texte="Texte.") for n in "ABC"], {"A": "Kore", "B": "Puck", "C": "Fenrir"}),
        ([Replique(locuteur="A", texte="Texte."), Replique(locuteur="B", texte="Texte.")], {"A": "Kore"}),
    ],
)
async def test_dialogue_invalide_aucun_appel(repliques, voix):
    llm, modeles = tts()
    with pytest.raises(LLMInvalidResponseError):
        await llm.synthetiser_dialogue(repliques, voix, "fr")
    assert modeles.appels == []


async def test_texte_vide():
    llm, modeles = tts()
    with pytest.raises(LLMInvalidResponseError):
        await llm.synthetiser("   ", "Kore", "fr")
    assert modeles.appels == []


async def test_language_code_refuse_on_reessaie_sans_et_on_s_en_souvient():
    llm, modeles = tts(erreur_api(400, "INVALID_ARGUMENT", "language_code is not supported"), reponse_audio(), reponse_audio())
    await llm.synthetiser("Bonjour.", "Kore", "fr")
    assert modeles.appels[0]["config"].speech_config.language_code == "fr-FR"
    assert modeles.appels[1]["config"].speech_config.language_code is None
    await llm.synthetiser("Encore.", "Kore", "fr")
    assert modeles.appels[2]["config"].speech_config.language_code is None  # plus d'essai inutile


async def test_une_surcharge_ne_declenche_pas_le_repli_de_langue():
    llm, modeles = tts(*[erreur_api(503, "UNAVAILABLE")] * 3)
    with pytest.raises(LLMError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")
    assert len(modeles.appels) == 3  # les 3 tentatives habituelles, rien de plus
    assert all(a["config"].speech_config.language_code == "fr-FR" for a in modeles.appels)


async def test_quota_et_cle_refusee():
    llm, _ = tts(erreur_api(429, "RESOURCE_EXHAUSTED"))
    with pytest.raises(LLMQuotaError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")
    llm, _ = tts(erreur_api(403, "PERMISSION_DENIED"))
    with pytest.raises(LLMMissingKeyError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")


async def test_reponse_sans_son():
    llm, _ = tts(SimpleNamespace(candidates=[SimpleNamespace(content=SimpleNamespace(parts=[SimpleNamespace(inline_data=None, text="blabla")]))], usage_metadata=None, text="blabla"))
    with pytest.raises(LLMInvalidResponseError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")
    llm, _ = tts(SimpleNamespace(candidates=None, usage_metadata=None, text=None))
    with pytest.raises(LLMInvalidResponseError):
        await llm.synthetiser("Bonjour.", "Kore", "fr")


async def test_modele_de_synthese_absent():
    llm, modeles = tts(tts_model=None)
    with pytest.raises(LLMMissingKeyError) as info:
        await llm.synthetiser("Bonjour.", "Kore", "fr")
    assert "LLM_TTS_MODEL" in info.value.message and modeles.appels == []


async def test_la_cle_ne_sort_pas_dans_les_logs_de_synthese(caplog):
    caplog.set_level(logging.DEBUG)
    llm, _ = tts(erreur_api(429, "RESOURCE_EXHAUSTED"), reponse_audio())
    with pytest.raises(LLMQuotaError):
        await llm.synthetiser("Texte confidentiel du cours.", "Kore", "fr")
    await llm.synthetiser("Texte confidentiel du cours.", "Kore", "fr")
    assert CLE_SECRETE not in caplog.text and "confidentiel" not in caplog.text


async def test_le_client_stub_refuse_la_synthese():
    stub = StubLLMClient()
    with pytest.raises(LLMMissingKeyError):
        await stub.synthetiser("Bonjour.", "Kore", "fr")
    with pytest.raises(LLMMissingKeyError):
        await stub.synthetiser_dialogue([Replique(locuteur="A", texte="Bonjour.")], {"A": "Kore"}, "fr")


def test_le_modele_de_synthese_vient_de_la_configuration():
    reglages = Settings(_env_file=None, llm_provider="gemini", llm_api_key=CLE_SECRETE, llm_model="m")
    assert reglages.llm_tts_model == "gemini-3.8-flash-tts"
    client = build_llm_client(Settings(_env_file=None, llm_provider="gemini", llm_api_key=CLE_SECRETE, llm_model="m", llm_tts_model="autre-tts"))
    assert client._tts_model == "autre-tts"
