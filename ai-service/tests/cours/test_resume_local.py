"""Résumé automatique local : langue, découpage, extractif, garde-fou de fidélité, bascule auto, 503, langue du TTS. Faux modèles."""

import pytest

from app.core.sanitizer import BasicSanitizer
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.explication.generation import CoursSansTexteError
from app.modules.cours.explication.schemas import PageCours
from app.modules.cours.local import langue as detecteur
from app.modules.cours.local import resumeur_local
from app.modules.cours.local.extractif import PHRASES, resumer_extractif
from app.modules.cours.local.garde_fou import evaluer
from app.modules.cours.local.resumeur_local import (
    ALERTE_PEU_FIDELE,
    MoteurTransformers,
    ResultatAbstractif,
    decouper_en_morceaux,
    resumer_local,
)
from app.modules.cours.local.schemas import (
    LangueNonSupporteeError,
    Longueur,
    ModeResume,
    ResumeLocalIndisponibleError,
    ResumeLocalRequest,
)
from app.modules.cours.local.texte import Unite, decouper_en_unites, phrases_du_texte
from tests.cours.conftest import UTILISATEUR

FR = (
    "Une base de données relationnelle range l'information dans plusieurs tables liées entre elles par des clés. "
    "Pour retrouver une information qui se trouve dans deux tables, on utilise une jointure. "
    "Une jointure interne ne garde que les lignes qui ont une correspondance dans les deux tables. "
    "Une jointure externe gauche garde toutes les lignes de la table de gauche, même sans correspondance. "
    "La clé primaire identifie chaque ligne de façon unique dans une table. "
    "Contactez le formateur à prof@exemple.tn ou au 22 123 456 pour toute question sur ce chapitre."
)
EN = (
    "A design pattern is a general reusable solution to a commonly occurring problem within a given context in software design. "
    "The observer pattern defines a one-to-many dependency between objects so that when one object changes state, all of its "
    "dependents are notified and updated automatically. The strategy pattern lets the algorithm vary independently from the "
    "clients that use it. The singleton pattern ensures that a class has only one instance and provides a global access point."
)
ES = "El patrón de diseño es una solución general y reutilizable para un problema que ocurre con frecuencia en el diseño de software, " * 3


def pages(texte: str) -> list[PageCours]:
    return [PageCours(numero=1, texte=texte)]


def unites(texte: str) -> list[Unite]:
    return decouper_en_unites(pages(texte))


def reglages(**valeurs) -> CoursAiSettings:
    valeurs.setdefault("resume_local_actif", True)
    return CoursAiSettings(_env_file=None, **valeurs)


class FauxMoteur:
    """Moteur de reformulation simulé : renvoie le texte programmé, note les appels."""

    def __init__(self, texte: str = "", dispo: bool = True, total: int = 1):
        self.texte, self.dispo, self.total, self.appels = texte, dispo, total, []

    def disponible(self) -> bool:
        return self.dispo

    async def resumer(self, unites, langue, longueur):
        self.appels.append((len(unites), langue, longueur))
        return ResultatAbstractif(self.texte, "faux-modele", self.total, 1)


async def lancer(texte, moteur, *, mode=ModeResume.AUTO, longueur=Longueur.MOYENNE, **options):
    requete = ResumeLocalRequest(cours_id=1, pages=pages(texte), longueur=longueur, mode=mode)
    return await resumer_local(requete, BasicSanitizer(20_000), reglages(**options), moteur)


# ---------------------------------------------------------------- langue


def test_detection_fr_en_autre():
    assert detecteur.detecter_langue(FR) == "fr"
    assert detecteur.detecter_langue(EN) == "en"
    assert detecteur.detecter_langue(ES) == "autre"
    assert detecteur.detecter_langue("Trop court.") == "autre"
    assert detecteur.detecter_langue("") == "autre"


def test_detection_robuste_au_code_et_aux_diaporamas():
    diapo_en = "Observer pattern\nDefine a one-to-many dependency between objects\nSELECT * FROM users WHERE id = 3;\n" + EN
    assert detecteur.detecter_langue(diapo_en) == "en"
    assert detecteur.detecter_langue("SELECT a, b FROM t;\n" * 20 + FR) == "fr"


# ---------------------------------------------------------------- découpage


def test_decoupage_en_unites_puces_code_doublons_et_pages():
    page1 = PageCours(numero=1, texte="Introduction\n• Un objet encapsule son état et son comportement dans une classe\n"
                                      "• Une classe décrit les attributs communs à tous ses objets\nSELECT * FROM t WHERE x = 1;\n12")
    page2 = PageCours(numero=2, texte="Une classe décrit les attributs communs à tous ses objets.\nLe polymorphisme permet de traiter\n"
                                      "des objets différents de la même manière dans le programme.")
    resultat = decouper_en_unites([page1, page2])
    assert [u.page for u in resultat] == [1, 1, 2]
    assert resultat[0].texte == "Un objet encapsule son état et son comportement dans une classe"
    assert "SELECT" not in " ".join(u.texte for u in resultat)  # code écarté
    assert resultat[2].texte.startswith("Le polymorphisme permet de traiter des objets")  # retours à la ligne recollés


def test_phrases_du_texte():
    assert phrases_du_texte("Première phrase. Deuxième phrase ! Troisième ?") == ["Première phrase.", "Deuxième phrase !", "Troisième ?"]


def test_decoupage_en_morceaux_respecte_la_taille_et_les_phrases():
    liste = [Unite(f"phrase numéro {i} " + "mot " * 8, 1) for i in range(10)]
    morceaux = decouper_en_morceaux(liste, lambda t: len(t.split()), 25)
    assert all(sum(len(u.texte.split()) for u in m) <= 25 for m in morceaux)
    assert [u for m in morceaux for u in m] == liste  # rien perdu, ordre conservé, aucune phrase coupée
    assert len(morceaux) > 1
    assert len(decouper_en_morceaux(liste, lambda t: 1, 1000)) == 1


# ---------------------------------------------------------------- extractif


def test_extractif_phrases_du_cours_recopiees_dans_l_ordre():
    liste = unites(FR + " " + EN)
    retenues = resumer_extractif(liste, Longueur.COURTE)
    assert 1 <= len(retenues) <= PHRASES[Longueur.COURTE]
    assert all(u in liste for u in retenues)  # aucune phrase réécrite
    assert [liste.index(u) for u in retenues] == sorted(liste.index(u) for u in retenues)
    assert len(resumer_extractif(liste, Longueur.LONGUE)) >= len(retenues)


def test_extractif_dans_les_deux_langues_et_sans_unites():
    assert resumer_extractif(unites(EN), Longueur.MOYENNE)
    assert resumer_extractif(unites(FR), Longueur.MOYENNE)
    assert resumer_extractif([], Longueur.MOYENNE) == []


# ---------------------------------------------------------------- garde-fou de fidélité


def test_garde_fou_resume_fidele_score_eleve_avec_extrait_et_page():
    controle = evaluer(["The observer pattern defines a one-to-many dependency between objects ."], unites(EN), "en", 55)
    assert controle.score >= 70 and controle.phrases[0].justifiee
    assert "observer pattern" in controle.phrases[0].extrait_source and controle.phrases[0].page == 1


def test_garde_fou_hallucination_score_bas():
    # L'hallucination observée avec BARThez sur un cours anglais.
    controle = evaluer(["Le projet de loi sur la distanciation sociale sera examiné le 12 mars ."], unites(EN), "en", 55)
    assert controle.score < 50 and not controle.phrases[0].justifiee
    assert any("langue du cours" in a for a in controle.alertes) and any("12" in a for a in controle.alertes)


def test_garde_fou_vide():
    assert evaluer([], unites(EN), "en", 55).score == 0


# ---------------------------------------------------------------- orchestration


async def test_auto_garde_le_reformule_fidele():
    moteur = FauxMoteur("The observer pattern defines a one-to-many dependency between objects. The strategy pattern lets the algorithm vary.")
    rep = await lancer(EN, moteur)
    assert (rep.langue, rep.mode_utilise, rep.modele) == ("en", ModeResume.ABSTRACTIF, "faux-modele")
    assert rep.score_fidelite >= 50 and rep.score_reformule == rep.score_fidelite
    assert [p.page for p in rep.phrases] == [1, 1]
    assert rep.resume == " ".join(p.texte for p in rep.phrases)


async def test_auto_bascule_sur_l_extractif_si_le_score_est_sous_le_seuil():
    moteur = FauxMoteur("Le projet de loi sur la distanciation sociale sera examiné le 12 mars. La mairie est fermée le dimanche.")
    rep = await lancer(EN, moteur)
    assert rep.mode_utilise == ModeResume.EXTRACTIF and rep.modele == "extractif-textrank"
    assert ALERTE_PEU_FIDELE in rep.alertes
    assert rep.score_reformule is not None and rep.score_reformule < 50
    assert rep.score_fidelite == 100 and all(p.justifiee for p in rep.phrases)
    assert all(p.texte in EN for p in rep.phrases)  # phrases du cours, mot pour mot


async def test_seuil_configurable():
    moteur = FauxMoteur("Le projet de loi sur la distanciation sociale sera examiné le 12 mars.")
    rep = await lancer(EN, moteur, resume_local_seuil=0)  # seuil 0 : jamais de bascule
    assert rep.mode_utilise == ModeResume.ABSTRACTIF and ALERTE_PEU_FIDELE not in rep.alertes


async def test_mode_abstractif_garde_le_reformule_avec_un_avertissement():
    moteur = FauxMoteur("Le projet de loi sur la distanciation sociale sera examiné le 12 mars.")
    rep = await lancer(EN, moteur, mode=ModeResume.ABSTRACTIF)
    assert rep.mode_utilise == ModeResume.ABSTRACTIF
    assert any("peu fidèle" in a for a in rep.alertes) and any(not p.justifiee for p in rep.phrases)


async def test_mode_extractif_n_appelle_jamais_le_modele():
    moteur = FauxMoteur("inutile")
    rep = await lancer(FR, moteur, mode=ModeResume.EXTRACTIF, longueur=Longueur.COURTE)
    assert moteur.appels == [] and rep.mode_utilise == ModeResume.EXTRACTIF and rep.langue == "fr"
    assert len(rep.phrases) <= 3


async def test_langue_et_longueur_transmises_au_moteur():
    moteur = FauxMoteur("Une jointure interne ne garde que les lignes qui ont une correspondance.")
    await lancer(FR, moteur, longueur=Longueur.LONGUE)
    assert moteur.appels[0][1:] == ("fr", Longueur.LONGUE)


async def test_cours_tronque_signale():
    moteur = FauxMoteur("The observer pattern defines a one-to-many dependency between objects.", total=20)
    rep = await lancer(EN, moteur)
    assert any(a.startswith("Cours tronqué") for a in rep.alertes)


async def test_sans_bibliotheques_auto_donne_l_extractif_et_abstractif_un_503():
    moteur = FauxMoteur(dispo=False)
    rep = await lancer(FR, moteur)
    assert rep.mode_utilise == ModeResume.EXTRACTIF and any("indisponible" in a for a in rep.alertes) and moteur.appels == []
    with pytest.raises(ResumeLocalIndisponibleError) as info:
        await lancer(FR, moteur, mode=ModeResume.ABSTRACTIF)
    assert (info.value.status_code, info.value.message) == (503, "Résumé local indisponible sur ce serveur")


async def test_interrupteur_eteint_503_pour_tous_les_modes():
    for mode in ModeResume:
        with pytest.raises(ResumeLocalIndisponibleError):
            await lancer(FR, FauxMoteur(), mode=mode, resume_local_actif=False)


async def test_langue_autre_et_cours_vide_erreurs_claires():
    with pytest.raises(LangueNonSupporteeError, match="français et en anglais"):
        await lancer(ES, FauxMoteur())
    with pytest.raises(CoursSansTexteError):
        await lancer("trop court", FauxMoteur())


async def test_emails_et_telephones_masques_avant_le_traitement():
    rep = await lancer(FR, FauxMoteur(), mode=ModeResume.EXTRACTIF, longueur=Longueur.LONGUE)
    assert "prof@exemple.tn" not in rep.resume and "22 123 456" not in rep.resume


# ---------------------------------------------------------------- moteur réel : mémoire et bibliothèques (sans modèle)


def test_moteur_indisponible_sans_bibliotheques(monkeypatch):
    monkeypatch.setattr(resumeur_local, "bibliotheques_presentes", lambda: False)
    assert not MoteurTransformers(reglages()).disponible()


async def test_moteur_sans_bibliotheques_leve_un_503(monkeypatch):
    monkeypatch.setattr(resumeur_local, "bibliotheques_presentes", lambda: False)
    with pytest.raises(ResumeLocalIndisponibleError):
        await MoteurTransformers(reglages()).resumer([Unite("x", 1)], "fr", Longueur.COURTE)


def test_modele_inactif_decharge_apres_le_delai():
    moteur = MoteurTransformers(reglages(resume_local_liberer_apres_secondes=600))
    moteur._charge = ("modele-en", object(), object())
    moteur._derniere_utilisation = 1000.0
    assert not moteur.liberer_si_inactif(maintenant=1500.0) and moteur.modele_charge == "modele-en"  # 500 s : encore actif
    assert moteur.liberer_si_inactif(maintenant=1601.0) and moteur.modele_charge is None  # 601 s : déchargé
    assert not moteur.liberer_si_inactif(maintenant=9999.0)  # rien à décharger


def test_liberation_desactivee_avec_zero():
    moteur = MoteurTransformers(reglages(resume_local_liberer_apres_secondes=0))
    moteur._charge = ("modele-fr", object(), object())
    assert not moteur.liberer_si_inactif(maintenant=10**9) and moteur.modele_charge == "modele-fr"


def test_un_seul_modele_en_memoire_changer_de_langue_decharge_l_autre(monkeypatch):
    import sys
    import types

    chargements = []

    class _Auto:
        @classmethod
        def from_pretrained(cls, nom, **kw):
            chargements.append(nom)
            return types.SimpleNamespace(nom=nom, eval=lambda: None)

    faux = types.ModuleType("transformers")
    faux.AutoTokenizer = _Auto
    faux.AutoModelForSeq2SeqLM = _Auto
    monkeypatch.setitem(sys.modules, "transformers", faux)
    moteur = MoteurTransformers(reglages(resume_local_modele_fr="m-fr", resume_local_modele_en="m-en"))
    moteur._obtenir("m-fr")
    moteur._obtenir("m-fr")  # déjà chargé : pas de second chargement
    assert moteur.modele_charge == "m-fr" and chargements == ["m-fr", "m-fr"]  # tokenizer + modèle, une seule fois
    moteur._obtenir("m-en")
    assert moteur.modele_charge == "m-en"  # m-fr est déchargé : jamais deux modèles en mémoire


def test_tokenizer_rapide_en_echec_repli_use_fast_false(monkeypatch):
    import sys
    import types

    appels = []

    class _Tok:
        @classmethod
        def from_pretrained(cls, nom, **kw):
            appels.append(kw)
            if not kw:
                raise TypeError("'dict' object is not an instance of 'Sequence'")
            return types.SimpleNamespace()

    class _Modele:
        @classmethod
        def from_pretrained(cls, nom, **kw):
            return types.SimpleNamespace(eval=lambda: None)

    faux = types.ModuleType("transformers")
    faux.AutoTokenizer, faux.AutoModelForSeq2SeqLM = _Tok, _Modele
    monkeypatch.setitem(sys.modules, "transformers", faux)
    MoteurTransformers(reglages())._obtenir("m")
    assert appels == [{}, {"use_fast": False}]


# ---------------------------------------------------------------- route HTTP


def test_route_resume_local_503_quand_l_interrupteur_est_eteint(client):
    corps = {"coursId": 1, "pages": [{"numero": 1, "texte": FR}], "longueur": "courte", "mode": "extractif"}
    r = client.post("/api/ai/cours/resume-local", json=corps, headers=UTILISATEUR)
    assert r.status_code == 503 and r.json()["message"] == "Résumé local indisponible sur ce serveur"


def test_route_resume_local_extractif_forme_camel_case(client, monkeypatch):
    monkeypatch.setenv("RESUME_LOCAL_ACTIF", "true")
    from app.modules.cours.config import get_cours_ai_settings

    get_cours_ai_settings.cache_clear()
    corps = {"coursId": 1, "pages": [{"numero": 1, "texte": FR}], "longueur": "courte", "mode": "extractif"}
    r = client.post("/api/ai/cours/resume-local", json=corps, headers=UTILISATEUR)
    assert r.status_code == 200
    data = r.json()["data"]
    assert {"langue", "modele", "modeUtilise", "resume", "phrases", "scoreFidelite", "alertes", "dureeMs"} <= set(data)
    assert data["langue"] == "fr" and data["modeUtilise"] == "extractif" and data["phrases"][0]["extraitSource"]
    assert client.post("/api/ai/cours/resume-local", json=corps).status_code in (401, 403)  # authentification exigée


def test_route_resume_local_langue_inconnue_400(client, monkeypatch):
    monkeypatch.setenv("RESUME_LOCAL_ACTIF", "true")
    from app.modules.cours.config import get_cours_ai_settings

    get_cours_ai_settings.cache_clear()
    corps = {"coursId": 1, "pages": [{"numero": 1, "texte": ES}], "mode": "extractif"}
    r = client.post("/api/ai/cours/resume-local", json=corps, headers=UTILISATEUR)
    assert r.status_code == 400 and "français et en anglais" in r.json()["message"]


# ---------------------------------------------------------------- la langue détectée arrive à la chaîne vocale


class _FournisseurEspion:
    nom = "espion"

    def __init__(self):
        self.langues = []

    def disponible(self):
        return True

    async def synthetiser(self, texte, langue="fr", locuteur="PROFESSEUR"):
        from app.modules.cours.tts import AudioSynthese

        self.langues.append(langue)
        return AudioSynthese(b"ID3", "audio/mpeg", self.nom)


@pytest.mark.parametrize(("texte", "attendue"), [(FR, "fr"), (EN, "en")])
def test_la_route_audio_transmet_la_langue_detectee_a_la_chaine(client, texte, attendue):
    from app.main import app
    from app.modules.cours.tts import ChaineTTS, get_chaine_tts

    espion = _FournisseurEspion()
    app.dependency_overrides[get_chaine_tts] = lambda: ChaineTTS([espion])
    langue = detecteur.detecter_langue(texte)
    r = client.post("/api/ai/cours/audio", json={"texte": "Le résumé du cours.", "langue": langue}, headers=UTILISATEUR)
    assert r.status_code == 200 and espion.langues == [attendue]


async def test_chaque_fournisseur_recoit_la_langue(monkeypatch):
    from app.core.llm_client import AudioResult
    from app.modules.cours.tts import edge
    from app.modules.cours.tts.gemini import GeminiTTS
    from app.modules.cours.tts.gtts_fournisseur import GTTSFournisseur
    from app.modules.cours.tts.openai_fournisseur import OpenAITTS
    from tests.cours.test_tts import _LLMAvecVoix

    # gTTS : le code de langue part à la bibliothèque.
    vus = []
    monkeypatch.setattr(GTTSFournisseur, "_appeler", staticmethod(lambda texte, langue, accent: vus.append(langue) or b"ID3"))
    await GTTSFournisseur().synthetiser("Hello", "en")
    await GTTSFournisseur().synthetiser("Bonjour", "fr")
    assert vus == ["en", "fr"]

    # Gemini : la langue part au client LLM du socle (language_code).
    llm = _LLMAvecVoix(b"RIFF")
    try:
        await GeminiTTS(llm, "Kore", "Puck").synthetiser("Hello", "en")
    except Exception:  # noqa: BLE001 - le faux WAV n'est pas lisible ; seule la langue transmise compte ici
        pass
    assert llm.demandes[0][2] == "en"

    # OpenAI : même chose.
    class _Client:
        langues = []

        async def synthetiser(self, texte, voix, langue):
            self.langues.append(langue)
            return AudioResult(data=b"ID3", mime_type="audio/mpeg", model="m")

    client = _Client()
    await OpenAITTS(client, "marin", "cedar").synthetiser("Hello", "en")
    assert client.langues == ["en"]

    # edge-tts : une voix différente par langue.
    assert edge.VOIX["fr"] != edge.VOIX["en"]


# ---------------------------------------------------------------- test réel (vrais modèles) : RUN_SLOW=1


@pytest.mark.slow
async def test_reel_diaporama_anglais_auto_bascule_sur_l_extractif_et_scores():
    """Cours EN (diaporama de 65 pages) : un reformulé hallucinatoire (BARThez FR) est remplacé par l'extractif ; scores affichés."""
    import os
    from pathlib import Path

    pypdf = pytest.importorskip("pypdf")
    chemin = Path(os.environ.get("RESUME_LOCAL_PDF_TEST", Path.home() / "Downloads" / "Chapter3_2023_2024.pdf"))
    if not chemin.exists():
        pytest.skip(f"PDF de test absent : {chemin}")
    cours = [PageCours(numero=i + 1, texte=p.extract_text() or "") for i, p in enumerate(pypdf.PdfReader(str(chemin)).pages)]
    requete = ResumeLocalRequest(cours_id=1, pages=cours, mode=ModeResume.AUTO)
    sanitizer = BasicSanitizer(20_000)

    # 1. Modèle anglais normal.
    normal_reglages = reglages()
    normal = await resumer_local(requete, sanitizer, normal_reglages, MoteurTransformers(normal_reglages))
    # 2. Le modèle FRANÇAIS sur ce cours anglais : l'hallucination observée au départ.
    faux_reglages = reglages(resume_local_modele_en="moussaKam/barthez-orangesum-abstract")
    halluciné = await resumer_local(requete, sanitizer, faux_reglages, MoteurTransformers(faux_reglages))

    print(f"\nSCORES reformulé distilbart = {normal.score_reformule} ; reformulé BARThez sur cours EN = {halluciné.score_reformule} ; extractif = 100")
    assert normal.langue == "en" and halluciné.langue == "en"
    assert halluciné.score_reformule is not None and halluciné.score_reformule < 50
    assert halluciné.mode_utilise == ModeResume.EXTRACTIF and ALERTE_PEU_FIDELE in halluciné.alertes
    assert halluciné.score_fidelite == 100
