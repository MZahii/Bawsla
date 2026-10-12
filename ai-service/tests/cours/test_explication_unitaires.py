"""Explication orale : sélection des pages, structure, contrôle de fidélité, client LLM à délai propre, mise en voix d'un script."""

import io
import struct
import math
import wave
from types import SimpleNamespace

import pytest

from app.core.config import Settings
from app.core.llm_client import AudioResult, GeminiLLMClient, Replique
from app.modules.cours import fidelite
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.explication.routes import get_llm_explication
from app.modules.cours.explication.schemas import Mode, PageCours, SegmentEntree, TypeSegment
from app.modules.cours.explication.selection import selectionner_pages
from app.modules.cours.explication.structure import MOTS_MAX, MOTS_MIN, verifier_structure
from app.modules.cours.tts import audio_utils
from app.modules.cours.tts.base import AudioSynthese, ErreurFournisseurTTS, FournisseurTTS, SegmentVoix
from app.modules.cours.tts.chaine import ChaineTTS
from app.modules.cours.tts.gemini import GeminiTTS, repartir_au_prorata
from tests.cours.conftest import FauxLLM
from tests.cours.explication_outils import EXTRAIT_A, PAGES, PLAN, script_dialogue, script_monologue, seg

# ---------------------------------------------------------------- sélection des pages


def pages_numerotees(*textes):
    return [PageCours(numero=i, texte=t) for i, t in enumerate(textes, 1)]


def test_selection_tout_garder_quand_ca_tient():
    sel = selectionner_pages(pages_numerotees("a" * 100, "", "b" * 100), PLAN, 1000)
    assert (sel.tronque, sel.pages_gardees, sel.pages_totales) == (False, 2, 2)  # la page vide est écartée
    assert [p.numero for p in sel.pages] == [1, 3]


def test_selection_priorite_aux_pages_des_notions_du_plan():
    pages = pages_numerotees("rien " * 100, "rien " * 100, "Le LEFT JOIN est expliqué ici. " * 10, "rien " * 100, "Un autre INNER JOIN. " * 10)
    sel = selectionner_pages(pages, ["LEFT JOIN", "INNER JOIN"], 700)
    assert sel.tronque is True and sel.pages_totales == 5
    assert {p.numero for p in sel.pages} >= {3, 5}
    assert [p.numero for p in sel.pages] == sorted(p.numero for p in sel.pages)  # remis dans l'ordre du cours
    assert sum(len(p.texte) for p in sel.pages) <= 700


def test_selection_sans_plan_garde_les_premieres_pages():
    sel = selectionner_pages(pages_numerotees("x" * 400, "y" * 400, "z" * 400), [], 900)
    assert [p.numero for p in sel.pages] == [1, 2] and sel.tronque is True


def test_selection_une_page_trop_longue_est_coupee():
    sel = selectionner_pages(pages_numerotees("m" * 5000), ["m"], 1000)
    assert len(sel.pages) == 1 and len(sel.pages[0].texte) == 1000 and sel.tronque is True


# ---------------------------------------------------------------- structure


def test_structure_valide():
    assert verifier_structure(script_monologue().segments, Mode.MONOLOGUE) == []
    assert verifier_structure(script_dialogue().segments, Mode.DIALOGUE) == []


@pytest.mark.parametrize(
    "modifier,attendu",
    [
        (lambda s: s.pop(0), "premier segment"),
        (lambda s: s.pop(), "dernier segment"),
        (lambda s: s.__setitem__(slice(None), [x for x in s if x.type.value != "analogie"]), "analogie"),  # plus aucune analogie
        (lambda s: s.insert(2, s.pop(1)), "doit commencer par une « explication »"),  # analogie avant l'explication
        (lambda s: s.insert(4, s.pop(2)), "ordre non respecté"),  # exemple avant l'analogie
        (lambda s: s.__delitem__(slice(10, 11)), "réponse"),  # la réponse à la question disparaît
    ],
)
def test_structure_ecarts_detectes(modifier, attendu):
    segments = script_monologue().segments
    modifier(segments)
    assert any(attendu in p for p in verifier_structure(segments, Mode.MONOLOGUE)), verifier_structure(segments, Mode.MONOLOGUE)


def test_structure_longueur():
    court = script_monologue().segments[:1] + script_monologue().segments[-1:]
    assert any("trop court" in p for p in verifier_structure(court, Mode.MONOLOGUE))
    long_ = [seg("explication", "Mot " * 400, "INNER JOIN", EXTRAIT_A, 1), *script_monologue().segments]
    assert fidelite.compter_mots(long_) > MOTS_MAX and any("trop long" in p for p in verifier_structure(long_, Mode.MONOLOGUE))
    assert MOTS_MIN < 450 and MOTS_MAX > 750  # la fenêtre englobe la cible de 450 à 750 mots


def test_structure_roles():
    dialogue = script_dialogue().segments
    assert any("monologue" in p for p in verifier_structure(dialogue, Mode.MONOLOGUE))
    sans_etudiant = [s for s in dialogue if s.locuteur.value == "prof" and s.type != TypeSegment.QUESTION]
    assert any("au moins deux questions" in p for p in verifier_structure(sans_etudiant, Mode.DIALOGUE))


# ---------------------------------------------------------------- fidélité (fidelite.py)


def entree(ordre, type_, texte, extrait=None, page=None, notion=None):
    return SegmentEntree(ordre=ordre, type=TypeSegment(type_), texte=texte, extrait_source=extrait, page=page, notion=notion)


def evaluer(*segments, notions=()):
    return fidelite.evaluer_explication(list(segments), [(p.numero, p.texte) for p in PAGES], list(notions))


def test_fidelite_extrait_retrouve_page_deduite():
    r = evaluer(entree(1, "explication", "Texte.", EXTRAIT_A, page=None))
    assert (r.score, r.alertes, r.segments[0].justifiee, r.segments[0].page) == (100, [], True, 1)


def test_fidelite_tolere_les_retours_a_la_ligne_d_un_pdf():
    extrait = EXTRAIT_A.replace(" correspondance dans", "\ncorrespondance\ndans")
    assert evaluer(entree(1, "demonstration", "Texte.", extrait)).segments[0].justifiee is True


def test_fidelite_termes_techniques_et_nombres():
    r = evaluer(entree(1, "explication", "Le RIGHT JOIN existe aussi, et il y a 12 cas.", EXTRAIT_A, 1))
    assert r.score < 100
    assert any("RIGHT" in a for a in r.alertes) and any("12" in a for a in r.alertes)
    ok = evaluer(entree(1, "explication", "Dans l'exemple du cours, 3 clés étrangères suffisent pour un LEFT JOIN.", EXTRAIT_A, 1))
    assert ok.alertes == []  # le 3 et LEFT JOIN figurent dans le cours


def test_fidelite_analogie_non_controlee():
    r = evaluer(entree(1, "analogie", "Imagine 500 invités et un NOUVEAUTERME inconnu."), entree(2, "explication", "Texte.", EXTRAIT_A, 1))
    assert r.alertes == [] and r.segments[0].analogie is True and r.score == 100


def test_fidelite_un_segment_sur_deux_infidele():
    r = evaluer(
        entree(1, "explication", "Texte.", EXTRAIT_A, 1),
        entree(2, "exemple", "Texte.", "Une phrase inventée qui n'existe nulle part dans ce cours de bases de données"),
    )
    assert [c.justifiee for c in r.segments] == [True, False]
    assert r.score == 70  # extraits : 1 sur 2 = 30 points sur 60 ; nombres et termes : rien à signaler = 20 + 20 points


def test_fidelite_sans_segment_d_explication():
    r = evaluer(entree(1, "intro", "Bonjour."), entree(2, "recap", "Au revoir."))
    assert any("incomplet" in a for a in r.alertes) and r.score < 60 and r.alertes[0].startswith("Fidélité faible")


def test_fidelite_plan_et_decompte_de_mots():
    segments = [entree(1, "explication", "un deux trois", EXTRAIT_A, 1, notion="INNER JOIN")]
    assert evaluer(*segments, notions=["INNER JOIN"]).alertes == []
    assert any("LEFT JOIN" in a for a in evaluer(*segments, notions=["INNER JOIN", "LEFT JOIN"]).alertes)
    assert fidelite.compter_mots(segments) == 3 and fidelite.duree_estimee_secondes([entree(1, "intro", "mot " * 150)]) == 60


# ---------------------------------------------------------------- client LLM à délai propre (point 3)


def test_client_dedie_avec_son_propre_delai_sans_toucher_au_client_commun():
    reglages = CoursAiSettings(_env_file=None, explication_timeout_seconds=150)
    settings = Settings(_env_file=None, llm_provider="gemini", llm_api_key="cle-de-test", llm_model="gemini-test", llm_timeout_seconds=60)
    commun = GeminiLLMClient("cle-de-test", "gemini-test", timeout_seconds=60, client=SimpleNamespace())

    dedie = get_llm_explication(commun, settings, reglages)

    assert isinstance(dedie, GeminiLLMClient) and dedie is not commun
    assert dedie._timeout == 150 and dedie._model == "gemini-test"
    assert commun._timeout == 60 and settings.llm_timeout_seconds == 60  # LLM_TIMEOUT_SECONDS n'a pas bougé
    assert get_llm_explication(commun, settings, reglages) is dedie  # un seul client dédié, réutilisé


def test_client_dedie_conserve_le_secours_openai():
    from app.core.llm_client import LLMRoutage, OpenAILLMClient

    reglages = CoursAiSettings(_env_file=None, explication_timeout_seconds=150)
    settings = Settings(
        _env_file=None, llm_provider="gemini", llm_api_key="cle-de-test", llm_model="gemini-test", llm_timeout_seconds=60,
        llm_secours="openai", openai_api_key="sk-factice", openai_model="gpt-test",
    )
    commun = LLMRoutage(GeminiLLMClient("cle-de-test", "gemini-test", timeout_seconds=60, client=SimpleNamespace()),
                        OpenAILLMClient("sk-factice", "gpt-test", timeout_seconds=60, client=SimpleNamespace()))

    dedie = get_llm_explication(commun, settings, reglages)

    assert isinstance(dedie, LLMRoutage) and dedie is not commun
    assert dedie.principal._timeout == 150 and dedie.secours._timeout == 150  # délai propre aux deux fournisseurs
    assert commun.principal._timeout == 60 and settings.llm_timeout_seconds == 60


def test_client_non_gemini_renvoye_tel_quel():
    faux = FauxLLM()
    assert get_llm_explication(faux, Settings(_env_file=None), CoursAiSettings(_env_file=None)) is faux


# ---------------------------------------------------------------- mise en voix d'un script (point 6)


def wav_sinus(secondes: float, frequence: int = 24000) -> bytes:
    donnees = b"".join(struct.pack("<h", int(8000 * math.sin(2 * math.pi * 440 * i / frequence))) for i in range(int(secondes * frequence)))
    tampon = io.BytesIO()
    with wave.open(tampon, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(frequence)
        w.writeframes(donnees)
    return tampon.getvalue()


def mp3_de(secondes: float, frequence: int = 24000) -> bytes:
    mp3 = audio_utils.wav_vers_mp3(wav_sinus(secondes, frequence))
    assert mp3
    return mp3


class VoixParSegment(FournisseurTTS):
    """Un appel par segment (comme ElevenLabs, gTTS, edge) : durées variables, panne possible sur un segment."""

    def __init__(self, nom="elevenlabs", durees=(1.0, 2.0, 1.5), panne_au_segment=None):
        self.nom, self._durees, self._panne = nom, list(durees), panne_au_segment
        self.appels: list[tuple[str, str, str]] = []

    def disponible(self):
        return True

    async def synthetiser(self, texte, langue="fr", locuteur="PROFESSEUR"):
        self.appels.append((texte, langue, locuteur))
        if self._panne is not None and len(self.appels) == self._panne:
            raise ErreurFournisseurTTS("panne simulée")
        return AudioSynthese(mp3_de(self._durees[(len(self.appels) - 1) % len(self._durees)]), "audio/mpeg", self.nom)


SEGMENTS = [SegmentVoix(1, "PROFESSEUR", "Bonjour."), SegmentVoix(2, "ETUDIANT", "Pourquoi ?"), SegmentVoix(3, "PROFESSEUR", "Parce que.")]


async def test_script_assemble_avec_pauses_et_minutage_exact():
    voix = VoixParSegment(durees=(1.0, 2.0, 1.5))
    son = await voix.synthetiser_script(SEGMENTS, "fr", pause_ms=350)
    assert [a[2] for a in voix.appels] == ["PROFESSEUR", "ETUDIANT", "PROFESSEUR"]  # une voix par locuteur
    assert son.minutage_estime is False and son.audio.mime == "audio/mpeg" and son.audio.fournisseur == "elevenlabs"
    # Début de chaque segment = durées précédentes + une pause par segment précédent.
    (o1, d1, f1), (o2, d2, f2), (o3, d3, f3) = son.minutage
    assert (o1, d1) == (1, 0) and abs(f1 - 1000) < 80
    assert abs(d2 - (f1 + 350)) < 5 and abs((f2 - d2) - 2000) < 80
    assert abs(d3 - (f2 + 350)) < 5 and abs((f3 - d3) - 1500) < 80
    # La durée réelle de l'audio assemblé (relue par mutagen) correspond au minutage.
    assert abs(son.duree_ms - f3) < 150
    assert abs(audio_utils.duree_ms(son.audio.donnees, son.audio.mime) - son.duree_ms) < 5


async def test_script_assemble_des_morceaux_de_frequences_differentes():
    class Mixte(VoixParSegment):
        async def synthetiser(self, texte, langue="fr", locuteur="PROFESSEUR"):
            self.appels.append((texte, langue, locuteur))
            return AudioSynthese(mp3_de(1.0, 24000 if len(self.appels) % 2 else 44100), "audio/mpeg", self.nom)

    son = await Mixte().synthetiser_script(SEGMENTS[:2], "fr", 200)
    assert abs(son.duree_ms - 2200) < 220  # + le remplissage de l'encodeur MP3 (environ 50 ms par fichier)


async def test_script_un_segment_en_echec_fait_echouer_ce_fournisseur():
    voix = VoixParSegment(panne_au_segment=2)
    with pytest.raises(ErreurFournisseurTTS):
        await voix.synthetiser_script(SEGMENTS, "fr")


async def test_chaine_script_recommence_tout_avec_le_fournisseur_suivant():
    en_panne, bon = VoixParSegment("elevenlabs", panne_au_segment=3), VoixParSegment("gtts")
    son = await ChaineTTS([en_panne, bon]).synthetiser_script(SEGMENTS, "fr")
    assert (len(en_panne.appels), len(bon.appels)) == (3, 3) and son.audio.fournisseur == "gtts"
    assert son.minutage_estime is False  # un audio vient d'un seul fournisseur


async def test_chaine_script_aucun_fournisseur():
    from app.modules.cours.tts import TtsIndisponibleError

    with pytest.raises(TtsIndisponibleError):
        await ChaineTTS([VoixParSegment(panne_au_segment=1)]).synthetiser_script(SEGMENTS, "fr")


class LLMVoix(FauxLLM):
    def __init__(self, secondes=3.0):
        super().__init__()
        self.secondes, self.mono, self.dialogue = secondes, [], []

    async def synthetiser(self, texte, voix, langue):
        self.mono.append((texte, voix, langue))
        return AudioResult(data=wav_sinus(self.secondes), mime_type="audio/wav", model="gemini-tts-test")

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        self.dialogue.append((repliques, voix_par_locuteur, langue))
        return AudioResult(data=wav_sinus(self.secondes), mime_type="audio/wav", model="gemini-tts-test")


async def test_gemini_un_seul_appel_monologue_minutage_estime():
    llm = LLMVoix(3.0)
    segments = [SegmentVoix(1, "PROFESSEUR", "a" * 100), SegmentVoix(2, "PROFESSEUR", "b" * 300)]
    son = await GeminiTTS(llm, "Kore", "Puck").synthetiser_script(segments, "fr")
    assert len(llm.mono) == 1 and llm.dialogue == []  # UN appel pour tout le script
    assert llm.mono[0][1] == "Kore" and "a" * 100 in llm.mono[0][0] and "b" * 300 in llm.mono[0][0]
    assert son.minutage_estime is True and son.audio.mime == "audio/mpeg" and son.audio.fournisseur == "gemini"
    (_, d1, f1), (_, d2, f2) = son.minutage
    assert d1 == 0 and f1 == d2 and f2 == son.duree_ms  # tout l'audio est réparti
    assert abs((f1 - d1) / son.duree_ms - 0.25) < 0.01  # au prorata des caractères : 100 sur 400


async def test_gemini_un_seul_appel_dialogue_deux_voix():
    llm = LLMVoix(2.0)
    son = await GeminiTTS(llm, "Kore", "Puck").synthetiser_script(SEGMENTS, "en")
    assert llm.mono == [] and len(llm.dialogue) == 1
    repliques, voix, langue = llm.dialogue[0]
    assert [r.locuteur for r in repliques] == ["PROFESSEUR", "ETUDIANT", "PROFESSEUR"] and all(isinstance(r, Replique) for r in repliques)
    assert voix == {"PROFESSEUR": "Kore", "ETUDIANT": "Puck"} and langue == "en"
    assert son.minutage_estime is True and len(son.minutage) == 3


async def test_gemini_erreur_du_llm_et_script_vide():
    from app.core.errors import LLMQuotaError

    class Quota(FauxLLM):
        async def synthetiser(self, texte, voix, langue):
            raise LLMQuotaError("Quota atteint")

    with pytest.raises(ErreurFournisseurTTS):
        await GeminiTTS(Quota(), "Kore", "Puck").synthetiser_script(SEGMENTS[:1], "fr")
    with pytest.raises(ErreurFournisseurTTS):
        await GeminiTTS(LLMVoix(), "Kore", "Puck").synthetiser_script([], "fr")


def test_repartition_au_prorata_couvre_toute_la_duree():
    minutage = repartir_au_prorata([SegmentVoix(1, "P", "a" * 10), SegmentVoix(2, "P", "b" * 30), SegmentVoix(3, "P", "c" * 60)], 10_000)
    assert minutage == [(1, 0, 1000), (2, 1000, 4000), (3, 4000, 10000)]


def test_assemblage_d_un_seul_morceau_et_donnees_invalides():
    un = audio_utils.assembler_mp3([mp3_de(1.0)], 350)
    assert un and abs(audio_utils.duree_ms(un, "audio/mpeg") - 1000) < 100
    assert audio_utils.assembler_mp3([], 350) is None
    assert audio_utils.assembler_mp3([b"ceci n'est pas un mp3"], 350) is None


def test_fournisseur_enregistre_dans_les_reponses(monkeypatch):
    from app.core import llm_client
    from app.modules.cours import fournisseur

    monkeypatch.setattr(fournisseur, "get_settings", lambda: Settings(_env_file=None, llm_provider="gemini"))
    assert fournisseur.fournisseur_utilise() == "gemini"  # pas de secours en jeu : le fournisseur configuré
    jeton = llm_client._fournisseur_utilise.set("openai")  # le routage a basculé sur OpenAI
    try:
        assert fournisseur.fournisseur_utilise() == "openai"
    finally:
        llm_client._fournisseur_utilise.reset(jeton)
