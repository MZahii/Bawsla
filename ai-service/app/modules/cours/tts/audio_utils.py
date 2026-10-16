"""Outils audio : WAV vers MP3 (ffmpeg embarqué par imageio-ffmpeg, aucune installation système) et durée d'un fichier."""

import io
import logging
import subprocess
import tempfile
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


def assembler_mp3(morceaux: list[bytes], pause_ms: int = 350, debit: str = "80k", delai_secondes: float = 180.0) -> bytes | None:
    """Colle des MP3 bout à bout, avec `pause_ms` de silence entre deux, en un seul MP3 mono 44,1 kHz (ffmpeg embarqué).

    Chaque morceau est d'abord ramené au même format : des fournisseurs différents (ou des réglages différents) ne se collent
    pas octet à octet sans artefacts. Renvoie None si l'assemblage échoue.
    """
    if not morceaux:
        return None
    try:
        import imageio_ffmpeg

        with tempfile.TemporaryDirectory(prefix="bawsla-audio-") as dossier:
            chemins = []
            for i, morceau in enumerate(morceaux):
                chemin = f"{dossier}/seg{i}.mp3"
                with open(chemin, "wb") as f:
                    f.write(morceau)
                chemins.append(chemin)
            n = len(chemins)
            commande = [imageio_ffmpeg.get_ffmpeg_exe(), "-hide_banner", "-loglevel", "error", "-y"]
            for chemin in chemins:
                commande += ["-i", chemin]
            filtres = [f"[{i}:a]aresample=44100,aformat=channel_layouts=mono[a{i}]" for i in range(n)]
            if n > 1:
                commande += ["-f", "lavfi", "-t", f"{pause_ms / 1000:.3f}", "-i", "anullsrc=r=44100:cl=mono"]
                filtres.append(f"[{n}:a]asplit={n - 1}" + "".join(f"[p{i}]" for i in range(n - 1)))
                ordre = "".join(f"[a{i}][p{i}]" for i in range(n - 1)) + f"[a{n - 1}]"
                filtres.append(f"{ordre}concat=n={2 * n - 1}:v=0:a=1[sortie]")
            else:
                filtres.append("[a0]anull[sortie]")
            sortie = f"{dossier}/sortie.mp3"
            commande += ["-filter_complex", ";".join(filtres), "-map", "[sortie]", "-c:a", "libmp3lame", "-b:a", debit, "-ar", "44100", "-ac", "1", sortie]
            resultat = subprocess.run(commande, capture_output=True, timeout=delai_secondes, check=False)
            if resultat.returncode != 0:
                log.warning("Assemblage MP3 en échec (code %s)", resultat.returncode)
                return None
            with open(sortie, "rb") as f:
                return f.read() or None
    except Exception as exc:  # noqa: BLE001 - binaire absent, délai, disque
        log.warning("Assemblage MP3 impossible (%s)", type(exc).__name__)
        return None
