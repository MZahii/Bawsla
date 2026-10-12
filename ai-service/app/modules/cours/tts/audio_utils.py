"""Outils audio : WAV vers MP3 (ffmpeg embarqué par imageio-ffmpeg, aucune installation système) et durée d'un fichier."""

import io
import logging
import subprocess
import wave

log = logging.getLogger("bawsla.cours.tts")


def wav_vers_mp3(wav: bytes, debit: str = "64k", delai_secondes: float = 120.0) -> bytes | None:
    """MP3 mono du WAV, ou None si la conversion échoue (l'appelant garde alors le WAV).

    Beaucoup plus léger : une minute de voix fait environ 0,5 Mo en MP3 64 kbit/s contre 2,9 Mo en WAV 24 kHz.
    """
    try:
        import imageio_ffmpeg

        commande = [
            imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-i", "pipe:0",
            "-vn", "-ac", "1", "-codec:a", "libmp3lame", "-b:a", debit, "-f", "mp3", "pipe:1",
        ]
        resultat = subprocess.run(commande, input=wav, capture_output=True, timeout=delai_secondes, check=False)
    except Exception as exc:  # noqa: BLE001 - binaire absent, délai, système de fichiers…
        log.warning("Conversion WAV vers MP3 impossible (%s)", type(exc).__name__)
        return None
    if resultat.returncode != 0 or not resultat.stdout:
        log.warning("Conversion WAV vers MP3 en échec (code %s)", resultat.returncode)
        return None
    return resultat.stdout


def duree_ms(donnees: bytes, mime: str) -> int | None:
    """Durée d'un fichier WAV ou MP3 en millisecondes, ou None si elle est illisible."""
    try:
        if "wav" in mime:
            with wave.open(io.BytesIO(donnees)) as w:
                return round(1000 * w.getnframes() / w.getframerate())
        from mutagen.mp3 import MP3

        return round(MP3(io.BytesIO(donnees)).info.length * 1000)
    except Exception:  # noqa: BLE001 - fichier corrompu ou format inattendu
        return None
