"""Lecture d'images : secours LOCAL (Florence-2) quand Gemini est indisponible. Faux Gemini, faux modèle local : aucun téléchargement."""

import base64
import os

import pytest

from app.core.errors import LLMError, LLMInvalidResponseError, LLMMissingKeyError, LLMQuotaError, LLMTimeoutError
from app.main import app
from app.modules.cours.config import get_cours_ai_settings
from app.modules.cours.local.gestionnaire import GestionnaireModele
from app.modules.cours.vision import local
from app.modules.cours.vision.local import FOURNISSEUR_LOCAL, LectureLocale, ModeleLocalInconnuError, MoteurFlorence
from app.modules.cours.vision.routes import get_llm_vision, get_moteur_local
from tests.cours.conftest import UTILISATEUR
from tests.cours.test_vision import PNG, URL, FauxVision, corps

MESSAGE = "Lecture des images indisponible pour le moment, réessaie plus tard"


class FauxLocal:
    """Faux modèle local : renvoie la lecture programmée (ou lève l'erreur) et compte ses appels."""

    def __init__(self, texte="Texte lu en local", figure="", erreur=None, present=True):
        self.lecture, self.erreur, self.present, self.appels = LectureLocale(texte, figure), erreur, present, 0

    def disponible(self):
        return self.present

    async def lire(self, image):
        self.appels += 1
        if self.erreur:
            raise self.erreur
        return self.lecture


@pytest.fixture
def lire(client, monkeypatch):
    def appeler(gemini, moteur, *, active=True, forcer=False, corps_extra=None):
        monkeypatch.setenv("VISION_LOCALE_ACTIVE", "true" if active else "false")
        monkeypatch.setenv("VISION_FORCER_LOCAL", "true" if forcer else "false")
        get_cours_ai_settings.cache_clear()
        app.dependency_overrides[get_llm_vision] = lambda: gemini
        app.dependency_overrides[get_moteur_local] = lambda: moteur
        return client.post(URL, json={**corps(), **(corps_extra or {})}, headers=UTILISATEUR)

    yield appeler
    get_cours_ai_settings.cache_clear()


# ---------------------------------------------------------------- la bascule


@pytest.mark.parametrize("erreur", [LLMError("Surchargé (503)"), LLMQuotaError("Quota (429)"), LLMTimeoutError("Délai dépassé")])
def test_gemini_en_panne_le_local_prend_le_relais(lire, erreur):
    local_ = FauxLocal("Bonjour le monde")
    r = lire(FauxVision(erreur=erreur), local_)
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["texte"] == "Bonjour le monde" and data["fournisseur"] == FOURNISSEUR_LOCAL and data["vide"] is False
    assert data["modele"] == "microsoft/Florence-2-base" and local_.appels == 1


def test_gemini_sain_le_local_n_est_pas_appele(lire):
    local_ = FauxLocal()
    r = lire(FauxVision("Texte Gemini"), local_)
    assert r.json()["data"]["fournisseur"] == "gemini" and local_.appels == 0


def test_local_desactive_message_habituel(lire):
    local_ = FauxLocal()
    r = lire(FauxVision(erreur=LLMError("Surchargé")), local_, active=False)
    assert r.status_code == 503 and r.json()["error"] == "VISION_INDISPONIBLE" and r.json()["message"] == MESSAGE
    assert local_.appels == 0


def test_local_en_echec_ou_absent_message_habituel(lire):
    for moteur in (FauxLocal(erreur=RuntimeError("mémoire insuffisante")), FauxLocal(erreur=TimeoutError()), FauxLocal(present=False)):
        r = lire(FauxVision(erreur=LLMQuotaError("Quota")), moteur)
        assert r.status_code == 503 and r.json()["message"] == MESSAGE and r.json()["error"] == "VISION_INDISPONIBLE"


def test_cle_refusee_ou_reponse_invalide_ne_declenchent_pas_le_local(lire):
    local_ = FauxLocal()
    r = lire(FauxVision(erreur=LLMMissingKeyError("Clé refusée")), local_)
    assert r.status_code == 503 and local_.appels == 0
    r = lire(FauxVision(erreur=LLMInvalidResponseError("vide")), local_)
    assert r.status_code == 200 and r.json()["data"]["vide"] is True and local_.appels == 0


# ---------------------------------------------------------------- VISION_FORCER_LOCAL


def test_forcer_local_gemini_jamais_appele(lire):
    gemini, local_ = FauxVision("Texte Gemini"), FauxLocal("Lu en local")
    r = lire(gemini, local_, forcer=True)
    assert r.json()["data"]["texte"] == "Lu en local" and gemini.recus == [] and local_.appels == 1


def test_forcer_local_sans_local_actif_message_habituel(lire):
    gemini = FauxVision("Texte Gemini")
    r = lire(gemini, FauxLocal(), active=False, forcer=True)
    assert r.status_code == 503 and gemini.recus == []


def test_le_lot_force_le_local_page_apres_page(lire):
    """cours-service envoie forcerLocal=true dès qu'une page est revenue du local : Gemini n'est plus essayé (90 s économisées par page)."""
    gemini, local_ = FauxVision("Texte Gemini"), FauxLocal("Lu en local")
    r = lire(gemini, local_, corps_extra={"forcerLocal": True})
    assert r.json()["data"]["fournisseur"] == FOURNISSEUR_LOCAL and gemini.recus == []


# ---------------------------------------------------------------- sortie locale


def test_figure_ajoutee_et_texte_assaini(lire):
    r = lire(FauxVision(erreur=LLMError("x")), FauxLocal("Écris à prof@exemple.tn", figure="A diagram of a binary tree"))
    texte = r.json()["data"]["texte"]
    assert "prof@exemple.tn" not in texte and texte.endswith("[Figure : A diagram of a binary tree]")


def test_page_sans_rien_de_lisible_est_vide(lire):
    r = lire(FauxVision(erreur=LLMError("x")), FauxLocal("", ""))
    data = r.json()["data"]
    assert data["vide"] is True and data["texte"] == "" and data["fournisseur"] == FOURNISSEUR_LOCAL


# ---------------------------------------------------------------- ordre de lecture, figure, modèles épinglés


def test_lignes_remises_dans_l_ordre_de_lecture():
    def boite(x, y, w=40, h=10):
        return [x, y, x + w, y, x + w, y + h, x, y + h]

    boites = [boite(200, 52), boite(10, 100), boite(10, 50), boite(100, 51), boite(10, 100 + 40)]
    etiquettes = ["</s>fin", "<pad>deuxième", "début", "milieu", "troisième"]
    assert local.ordonner_lignes(boites, etiquettes) == "début milieu fin\ndeuxième\ntroisième"
    assert local.ordonner_lignes([], []) == ""


def test_figure_seulement_si_la_description_evoque_un_schema():
    assert local.figure_decrite("A diagram showing the water cycle.") != ""
    assert local.figure_decrite("A page of text with a title.") == ""
    # Description coupée par la limite de jetons : on garde les phrases complètes, sur une seule ligne.
    assert local.figure_decrite("A graph of a curve. It shows the slope of the line and a") == "A graph of a curve."


def test_seuls_les_modeles_epingles_sont_acceptes():
    assert set(local.REVISIONS) == {"microsoft/Florence-2-base", "microsoft/Florence-2-large"}
    assert all(len(h) == 40 for h in local.REVISIONS.values())
    with pytest.raises(ModeleLocalInconnuError):
        local.revision_epinglee("un-autre/modele-distant")


def test_flash_attn_retire_puis_toujours_restaure(monkeypatch):
    import sys
    import types

    original = lambda fichier: ["torch", "flash_attn", "einops"]  # noqa: E731
    faux = types.ModuleType("transformers.dynamic_module_utils")
    faux.get_imports = original
    pere = types.ModuleType("transformers")
    pere.dynamic_module_utils = faux
    monkeypatch.setitem(sys.modules, "transformers", pere)
    monkeypatch.setitem(sys.modules, "transformers.dynamic_module_utils", faux)
    with local.sans_flash_attn():
        assert faux.get_imports("x.py") == ["torch", "einops"]
    assert faux.get_imports is original
    with pytest.raises(RuntimeError), local.sans_flash_attn():  # même si le chargement plante
        raise RuntimeError("chargement en échec")
    assert faux.get_imports is original


def test_cache_vide_rendu_comme_none_au_code_du_modele():
    import types

    recus = []

    class Cache:
        def __init__(self, longueur):
            self.longueur = longueur

        def get_seq_length(self):
            return self.longueur

    langage = types.SimpleNamespace(prepare_inputs_for_generation=lambda ids, past_key_values=None, **kw: recus.append((past_key_values, kw)))
    local.reparer_cache(types.SimpleNamespace(language_model=langage))
    plein = Cache(3)
    langage.prepare_inputs_for_generation([1], past_key_values=Cache(0), attention_mask="m")
    langage.prepare_inputs_for_generation([1], past_key_values=plein)
    langage.prepare_inputs_for_generation([1], past_key_values=(("k", "v"),))
    assert recus[0] == (None, {"attention_mask": "m"}) and recus[1][0] is plein and recus[2][0] == (("k", "v"),)


# ---------------------------------------------------------------- un seul modèle en mémoire, résumé et vision


def test_resume_et_vision_ne_cohabitent_jamais_en_memoire():
    gestionnaire = GestionnaireModele()
    charges = []

    def fabrique(nom):
        def charger():
            charges.append(nom)
            return nom + "-a", nom + "-b"

        return charger

    gestionnaire.obtenir("barthez", fabrique("barthez"), "Résumé local")
    gestionnaire.obtenir("barthez", fabrique("barthez"), "Résumé local")  # déjà chargé
    assert gestionnaire.modele_charge == "barthez" and charges == ["barthez"]
    gestionnaire.obtenir("microsoft/Florence-2-base", fabrique("florence"), "Vision locale")
    assert gestionnaire.modele_charge == "microsoft/Florence-2-base"  # barthez est déchargé
    gestionnaire.obtenir("barthez", fabrique("barthez"), "Résumé local")
    assert gestionnaire.modele_charge == "barthez" and charges == ["barthez", "florence", "barthez"]


async def test_deux_demandes_en_meme_temps_passent_l_une_apres_l_autre():
    import asyncio
    import threading

    gestionnaire = GestionnaireModele()
    en_cours, maximum, verrou = 0, 0, threading.Lock()

    def travail(n):
        nonlocal en_cours, maximum
        with verrou:
            en_cours += 1
            maximum = max(maximum, en_cours)
        import time

        time.sleep(0.05)
        with verrou:
            en_cours -= 1
        return n

    resultats = await asyncio.gather(gestionnaire.executer(travail, 1), gestionnaire.executer(travail, 2))
    assert sorted(resultats) == [1, 2] and maximum == 1


def test_moteur_florence_indisponible_sans_bibliotheques(monkeypatch):
    monkeypatch.setattr(local, "bibliotheques_presentes", lambda: False)
    assert not MoteurFlorence(get_cours_ai_settings()).disponible()


# ---------------------------------------------------------------- test réel (téléchargement ~463 Mo) : RUN_SLOW=1


@pytest.mark.slow
async def test_reel_florence_sur_une_page(tmp_path):
    """Charge VRAIMENT Florence-2-base et lit une image de test. Mesure le pic de RAM et la durée (affichés avec -s)."""
    import io
    import time
    import tracemalloc  # noqa: F401 - la RAM du processus se lit avec psutil si présent, sinon resource

    from PIL import Image, ImageDraw, ImageFont

    chemin = os.environ.get("VISION_TEST_IMAGE")
    if chemin and os.path.exists(chemin):
        octets = open(chemin, "rb").read()
    else:
        # Page synthétique (aucun scan sous la main) : titre, paragraphe sur 12 lignes, puis un petit graphique avec axes et courbe.
        image = Image.new("RGB", (1240, 1754), "white")
        dessin = ImageDraw.Draw(image)
        dessin.text((80, 70), "Chapter 2 - Limits and derivatives", fill="black", font=ImageFont.load_default(size=44))
        lignes = [
            "The limit of a function describes the value that the function",
            "approaches when the input gets close to a given point.",
            "A function is continuous at a point if the limit exists and",
            "is equal to the value of the function at that point.",
            "The derivative measures the instantaneous rate of change.",
            "For example, the derivative of x squared is 2x, and the",
            "derivative of a constant is zero.",
            "The product rule states that (fg)' = f'g + fg'.",
            "The chain rule is used to differentiate composed functions.",
            "A tangent line touches the curve at exactly one point and",
            "has a slope equal to the derivative at that point.",
            "Figure 2.1 shows the graph of f(x) = x squared.",
        ]
        for i, ligne in enumerate(lignes):
            dessin.text((80, 170 + i * 52), ligne, fill="black", font=ImageFont.load_default(size=32))
        dessin.line([(200, 1000), (200, 1600), (1000, 1600)], fill="black", width=4)  # axes
        dessin.line([(x, 1600 - (x - 200) ** 2 // 600) for x in range(200, 800, 8)], fill="black", width=4)  # courbe
        dessin.text((420, 1660), "Figure 2.1 - graph of f(x) = x^2", fill="black", font=ImageFont.load_default(size=30))
        flux = io.BytesIO()
        image.save(flux, "PNG")
        octets = flux.getvalue()

    def memoire_mo():
        try:
            import psutil

            return psutil.Process().memory_info().rss / 2**20
        except ImportError:
            return -1.0

    reglages = get_cours_ai_settings()
    moteur = MoteurFlorence(reglages)
    avant = memoire_mo()
    debut = time.monotonic()
    premiere = await moteur.lire(octets)
    t_premiere = time.monotonic() - debut
    apres = memoire_mo()
    debut = time.monotonic()
    await moteur.lire(octets)
    t_suivante = time.monotonic() - debut
    print(f"\n[MESURE] modèle={reglages.vision_locale_modele} 1re page (avec chargement)={t_premiere:.1f}s "
          f"page suivante={t_suivante:.1f}s RAM avant={avant:.0f}Mo après={apres:.0f}Mo texte={premiere.texte!r} figure={premiere.figure!r}")
    assert premiere.texte.strip()
