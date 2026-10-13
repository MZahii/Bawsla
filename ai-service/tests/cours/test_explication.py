"""Explication orale : génération, fidélité, régénération, traduction, audio. LLM et fournisseurs vocaux simulés."""

import base64
from types import SimpleNamespace

import pytest

from app.core.errors import LLMInvalidResponseError
from app.core.llm_client import GeminiLLMClient
from app.modules.cours.explication.routes import get_llm_explication
from app.modules.cours.explication.schemas import ExplicationLLM, Locuteur
from app.modules.cours.explication.traduction import SegmentTraduit, TraductionLLM
from app.modules.cours.tts import (
    AudioSynthese,
    ChaineTTS,
    ErreurFournisseurTTS,
    FournisseurTTS,
    SonScript,
    get_chaine_tts,
)
from tests.core.test_gemini_client import CLE_SECRETE, FauxModeles, reponse
from tests.cours.conftest import UTILISATEUR, FauxLLM
from tests.cours.explication_outils import (
    EXTRAIT_A,
    PAGE1,
    PAGES_JSON,
    PLAN,
    script_dialogue,
    script_monologue,
    seg,
)

URL = "/api/ai/cours/explication"


def demander(client, **corps):
    base = {"coursId": 1, "pages": PAGES_JSON, "notions": PLAN, "niveau": "DEBUTANT", "mode": "monologue", "dureeMinutes": 4}
    return client.post(URL, json={**base, **corps}, headers=UTILISATEUR)


# ---------------------------------------------------------------- génération


def test_explication_monologue_fidele(client, avec_llm):
    llm = avec_llm(FauxLLM(script_monologue()))
    r = demander(client)
    assert r.status_code == 200
    d = r.json()["data"]
    assert (d["langue"], d["mode"], d["versionPrompt"], d["modele"]) == ("fr", "monologue", "explication_v1", "modele-test")
    assert [s["ordre"] for s in d["segments"]] == list(range(1, 13))
    assert d["segments"][0]["type"] == "intro" and d["segments"][-1]["type"] == "recap"
    assert d["scoreFidelite"] == 100 and d["alertes"] == [] and d["notionsChoisiesParIa"] is False
    assert all(s["justifiee"] for s in d["segments"]) and {s["locuteur"] for s in d["segments"]} == {"prof"}
    assert d["nbMots"] > 380 and 150 < d["dureeEstimeeSecondes"] < 300
    assert [s["analogie"] for s in d["segments"]].count(True) == 2  # les analogies sont marquées comme telles
    # La page renvoyée est celle où l'extrait a été retrouvé.
    assert d["segments"][1]["page"] == 1 and d["segments"][8]["page"] == 2

    appel = llm.appels[0]
    assert appel["module"] == "cours" and appel["temperature"] == 0.4 and appel["schema"] is ExplicationLLM
    systeme, utilisateur = (m.content for m in appel["messages"])
    for rubrique in ("RÔLE", "CONTEXTE", "TÂCHE", "FORMAT", "CONTRAINTES"):
        assert rubrique in systeme
    assert "[Page 1]" in utilisateur and "[Page 2]" in utilisateur and "<<<" in utilisateur and ">>>" in utilisateur
    assert "- INNER JOIN" in utilisateur and "- LEFT JOIN" in utilisateur  # le plan : des titres seulement
    assert "DEBUTANT" in utilisateur and "monologue" in utilisateur and "4 minutes" in utilisateur


def test_le_plan_n_envoie_que_des_titres_jamais_le_contenu_de_la_fiche(client, avec_llm):
    llm = avec_llm(FauxLLM(script_monologue()))
    demander(client, notions=["INNER JOIN", "  ", "LEFT JOIN"])
    plan = llm.appels[0]["messages"][1].content.split("Plan (")[1].split("Cours (")[0]
    assert plan.count("\n- ") == 2 and "INNER JOIN" in plan


def test_sans_plan_alerte_notions_choisies_par_l_ia(client, avec_llm):
    llm = avec_llm(FauxLLM(script_monologue()))
    d = demander(client, notions=[]).json()["data"]
    assert d["notionsChoisiesParIa"] is True
    assert d["alertes"][0].startswith("Notions choisies par l'IA")
    assert "choisis toi-même" in llm.appels[0]["messages"][1].content


def test_donnees_personnelles_masquees_avant_le_llm(client, avec_llm):
    llm = avec_llm(FauxLLM(script_monologue()))
    pages = [{"numero": 1, "texte": PAGE1 + " Contact : prof@exemple.tn ou 22 123 456."}]
    demander(client, pages=pages)
    envoye = llm.appels[0]["messages"][1].content
    assert "prof@exemple.tn" not in envoye and "22 123 456" not in envoye and "[EMAIL]" in envoye


def test_dialogue(client, avec_llm):
    avec_llm(FauxLLM(script_dialogue()))
    d = demander(client, mode="dialogue").json()["data"]
    assert d["mode"] == "dialogue" and d["alertes"] == []
    assert {s["locuteur"] for s in d["segments"]} == {"prof", "etudiant"}
    assert [s["type"] for s in d["segments"] if s["locuteur"] == "etudiant"] == ["question", "question"]


def test_monologue_force_le_locuteur_prof(client, avec_llm):
    script = script_monologue()
    script.segments[3] = script.segments[3].model_copy(update={"locuteur": Locuteur.ETUDIANT})
    avec_llm(FauxLLM(script, script))  # le second appel (régénération) renvoie le même défaut
    d = demander(client).json()["data"]
    assert {s["locuteur"] for s in d["segments"]} == {"prof"}
    assert any("monologue" in a for a in d["alertes"])


# ---------------------------------------------------------------- fidélité


def test_segment_infidele_signale(client, avec_llm):
    script = script_monologue()
    script.segments[1] = seg(
        "explication", "Il existe aussi 42 types de jointures, dont le RIGHT JOIN.", "INNER JOIN",
        "Le RIGHT JOIN garde toutes les lignes de la table de droite et fusionne les index", 1,
    )
    avec_llm(FauxLLM(script))
    d = demander(client).json()["data"]
    assert d["segments"][1]["justifiee"] is False and all(s["justifiee"] for i, s in enumerate(d["segments"]) if i != 1)
    alertes = " | ".join(d["alertes"])
    assert "Segment 2" in alertes and "introuvable" in alertes
    assert "42" in alertes and "RIGHT" in alertes  # nombre et terme absents du cours
    assert d["scoreFidelite"] < 100


def test_extrait_absent_ou_trop_court(client, avec_llm):
    script = script_monologue()
    script.segments[1] = seg("explication", "Explication sans extrait.", "INNER JOIN")
    script.segments[3] = seg("exemple", "Exemple avec extrait trop court.", "INNER JOIN", "les lignes", 1)
    avec_llm(FauxLLM(script))
    d = demander(client).json()["data"]
    alertes = " | ".join(d["alertes"])
    assert "Segment 2" in alertes and "aucun extrait" in alertes and "Segment 4" in alertes and "trop court" in alertes
    assert [d["segments"][1]["justifiee"], d["segments"][3]["justifiee"]] == [False, False]


def test_page_corrigee(client, avec_llm):
    script = script_monologue()
    script.segments[1] = seg("explication", "Commençons par la jointure interne.", "INNER JOIN", EXTRAIT_A, 2)  # l'extrait est en page 1
    avec_llm(FauxLLM(script))
    d = demander(client).json()["data"]
    assert d["segments"][1]["page"] == 1 and d["segments"][1]["justifiee"] is True
    assert any("page corrigée" in a for a in d["alertes"])


def test_analogie_libre_mais_marquee(client, avec_llm):
    script = script_monologue()
    script.segments[2] = seg("analogie", "Imagine 99 invités dans la salle ZORG.", "INNER JOIN")
    avec_llm(FauxLLM(script))
    d = demander(client).json()["data"]
    assert d["segments"][2]["analogie"] is True and d["segments"][2]["justifiee"] is True
    assert d["alertes"] == [] and d["scoreFidelite"] == 100  # ni chiffre ni terme technique d'une analogie ne sont contrôlés


def test_notion_du_plan_non_traitee(client, avec_llm):
    avec_llm(FauxLLM(script_monologue()))
    d = demander(client, notions=[*PLAN, "Index composite"]).json()["data"]
    assert any("Index composite" in a and "non traitée" in a for a in d["alertes"])


# ---------------------------------------------------------------- structure et JSON


def test_structure_non_respectee_une_seule_regeneration(client, avec_llm):
    mauvais = script_monologue()
    mauvais.segments.pop()  # plus de récap
    llm = avec_llm(FauxLLM(mauvais, script_monologue()))
    d = demander(client).json()["data"]
    assert len(llm.appels) == 2 and d["alertes"] == [] and d["segments"][-1]["type"] == "recap"
    retour = [m for m in llm.appels[1]["messages"] if m.role == "user"][-1].content
    assert "recap" in retour and "COMPLET" in retour  # les écarts sont donnés au modèle


def test_structure_toujours_mauvaise_gardee_avec_alertes(client, avec_llm):
    mauvais = script_monologue()
    mauvais.segments.pop()
    llm = avec_llm(FauxLLM(mauvais, mauvais))
    d = demander(client).json()["data"]
    assert len(llm.appels) == 2  # jamais plus d'une régénération
    assert any(a.startswith("Structure :") and "recap" in a for a in d["alertes"])


def test_la_regeneration_qui_echoue_garde_le_premier_script(client, avec_llm):
    mauvais = script_monologue()
    mauvais.segments.pop()
    avec_llm(FauxLLM(mauvais, LLMInvalidResponseError("JSON invalide")))
    r = demander(client)
    assert r.status_code == 200 and any(a.startswith("Structure :") for a in r.json()["data"]["alertes"])


def test_json_invalide_relance_une_fois_par_le_vrai_client(client):
    """Le vrai client Gemini (SDK simulé) : une réponse non JSON est relancée UNE fois avec une consigne de correction."""
    import json

    from app.main import app

    valide = json.dumps(script_monologue().model_dump(mode="json", by_alias=True), ensure_ascii=False)
    modeles = FauxModeles([reponse("Voici ton script : pas du JSON"), reponse(valide)])
    sdk = SimpleNamespace(aio=SimpleNamespace(models=modeles))
    app.dependency_overrides[get_llm_explication] = lambda: GeminiLLMClient(CLE_SECRETE, "gemini-test", client=sdk, attente_entre_tentatives=0)
    r = demander(client)
    assert r.status_code == 200 and r.json()["data"]["scoreFidelite"] == 100
    assert len(modeles.appels) == 2
    assert "JSON" in modeles.appels[1]["contents"][-1].parts[0].text


def test_json_toujours_invalide_donne_une_erreur_502(client, avec_llm):
    avec_llm(FauxLLM(LLMInvalidResponseError("Réponse du LLM non conforme au schéma attendu")))
    r = demander(client)
    assert r.status_code == 502 and r.json()["error"] == "LLM_INVALID_RESPONSE"


# ---------------------------------------------------------------- texte vide, cours long, entrées


@pytest.mark.parametrize(
    "pages",
    [[], [{"numero": 1, "texte": "   "}], [{"numero": 1, "texte": "Court."}, {"numero": 2, "texte": ""}]],
    ids=["aucune page", "page blanche", "presque rien (PDF scanné)"],
)
def test_cours_vide_ou_scanne_erreur_claire_sans_appel_au_llm(client, avec_llm, pages):
    llm = avec_llm(FauxLLM())
    r = demander(client, pages=pages)
    assert r.status_code == 400 and r.json()["error"] == "BAD_REQUEST"
    assert "scan" in r.json()["message"] and llm.appels == []


def test_cours_long_garde_les_pages_des_notions_du_plan(client, avec_llm, monkeypatch):
    from app.modules.cours.config import get_cours_ai_settings

    monkeypatch.setenv("EXPLICATION_TEXTE_MAX_CHARS", "1500")
    get_cours_ai_settings.cache_clear()
    llm = avec_llm(FauxLLM(script_monologue()))
    bruit = "Cette page parle d'autre chose, d'installation et de configuration générale du poste de travail. " * 6
    pages = [{"numero": n, "texte": bruit} for n in (1, 2, 3, 4, 5)]
    pages[3]["texte"] = "La jointure LEFT JOIN garde toutes les lignes de la table de gauche. " + bruit[:300]
    d = demander(client, pages=pages, notions=["LEFT JOIN"]).json()["data"]
    envoye = llm.appels[0]["messages"][1].content
    assert "[Page 4]" in envoye and envoye.count("[Page ") < 5
    assert any(a.startswith("Cours tronqué") and "sur 5" in a for a in d["alertes"])


def test_entrees_invalides(client, avec_llm):
    avec_llm(FauxLLM())
    assert demander(client, mode="conference").status_code == 400
    assert demander(client, niveau="EXPERT").status_code == 400
    assert demander(client, dureeMinutes=30).status_code == 400
    assert client.post(URL, json={"coursId": 1}, headers=UTILISATEUR).status_code == 400
    assert client.post(URL, json={"coursId": 1, "pages": PAGES_JSON}).status_code == 401


# ---------------------------------------------------------------- traduction


def segments_fr(script=None):
    script = script or script_monologue()
    return [
        {"ordre": i, "type": s.type.value, "locuteur": s.locuteur.value, "texte": s.texte, "notion": s.notion,
         "extraitSource": s.extrait_source, "page": s.page}
        for i, s in enumerate(script.segments, 1)
    ]


def traduire(client, segments, **corps):
    return client.post(f"{URL}/traduire", json={"segments": segments, "pages": PAGES_JSON, "notions": PLAN, "mode": "monologue", **corps}, headers=UTILISATEUR)


def traduction_de(segments, suffixe=" (translated)"):
    return TraductionLLM(segments=[SegmentTraduit(ordre=s["ordre"], texte=s["texte"].strip() + suffixe) for s in segments])


def test_traduction_garde_le_decoupage_et_les_extraits_francais(client, avec_llm):
    fr = segments_fr()
    llm = avec_llm(FauxLLM(traduction_de(fr)))
    d = traduire(client, fr).json()["data"]
    assert (d["langue"], d["mode"], d["versionPrompt"]) == ("en", "monologue", "traduction_v1")
    assert len(d["segments"]) == len(fr)
    for original, traduit in zip(fr, d["segments"], strict=True):
        assert traduit["texte"] == original["texte"].strip() + " (translated)"
        # Tout le reste est recopié du script français validé, y compris l'extrait source FR.
        assert (traduit["ordre"], traduit["type"], traduit["locuteur"], traduit["notion"], traduit["extraitSource"]) == (
            original["ordre"], original["type"], original["locuteur"], original["notion"], original["extraitSource"])
    assert d["scoreFidelite"] == 100 and d["alertes"] == []
    assert "anglais" in llm.appels[0]["messages"][1].content and llm.appels[0]["schema"] is TraductionLLM


def test_traduction_nombre_modifie_signale(client, avec_llm):
    fr = segments_fr()
    fr[3]["texte"] = fr[3]["texte"] + " Il y a 3 lignes."  # un exemple (pas une analogie : les chiffres y sont contrôlés)
    en = traduction_de(fr)
    en.segments[3].texte = en.segments[3].texte.replace("3 lignes", "5 rows")
    avec_llm(FauxLLM(en))
    alertes = traduire(client, fr).json()["data"]["alertes"]
    assert any("Segment 4" in a and "nombres de la traduction" in a for a in alertes)


def test_traduction_segment_manquant_une_seule_nouvelle_tentative(client, avec_llm):
    fr = segments_fr()
    incomplet = TraductionLLM(segments=traduction_de(fr).segments[:-1])
    llm = avec_llm(FauxLLM(incomplet, traduction_de(fr)))
    r = traduire(client, fr)
    assert r.status_code == 200 and len(llm.appels) == 2 and len(r.json()["data"]["segments"]) == len(fr)
    assert "segments manquants" in llm.appels[1]["messages"][-1].content


def test_traduction_qui_reste_incomplete_est_refusee(client, avec_llm):
    fr = segments_fr()
    incomplet = TraductionLLM(segments=traduction_de(fr).segments[:-1])
    llm = avec_llm(FauxLLM(incomplet, incomplet))
    r = traduire(client, fr)
    assert r.status_code == 502 and len(llm.appels) == 2


def test_traduction_ordres_en_double_refuses_sans_appel(client, avec_llm):
    fr = segments_fr()
    fr[1]["ordre"] = 1
    llm = avec_llm(FauxLLM())
    assert traduire(client, fr).status_code == 502 and llm.appels == []


# ---------------------------------------------------------------- fidélité seule (après correction)


def test_fidelite_apres_correction_sans_llm(client, avec_llm):
    llm = avec_llm(FauxLLM())
    fr = segments_fr()
    fr[1]["texte"] = "Le formateur a ajouté une phrase avec 77 éléments."
    fr[1]["extraitSource"] = "Cette phrase a été inventée par le formateur et ne figure pas dans le cours"
    r = client.post(f"{URL}/fidelite", json={"segments": fr, "pages": PAGES_JSON, "notions": PLAN}, headers=UTILISATEUR)
    assert r.status_code == 200
    d = r.json()["data"]
    assert d["segments"][1]["justifiee"] is False and d["segments"][2]["justifiee"] is True
    assert d["scoreFidelite"] < 100 and any("77" in a for a in d["alertes"]) and d["nbMots"] > 300
    assert llm.appels == []


# ---------------------------------------------------------------- audio


class FauxVoix(FournisseurTTS):
    """Fournisseur de script simulé : enregistre ce qu'on lui demande de lire."""

    def __init__(self, nom="gemini", *, panne=False, estime=True):
        self.nom, self.panne, self.estime = nom, panne, estime
        self.demandes: list[tuple[list, str, int]] = []

    def disponible(self):
        return True

    async def synthetiser(self, texte, langue="fr", locuteur="PROFESSEUR"):
        raise NotImplementedError

    async def synthetiser_script(self, segments, langue="fr", pause_ms=350):
        self.demandes.append((segments, langue, pause_ms))
        if self.panne:
            raise ErreurFournisseurTTS("panne simulée")
        minutage, debut = [], 0
        for s in segments:
            minutage.append((s.ordre, debut, debut + 1000))
            debut += 1350
        return SonScript(AudioSynthese(b"ID3-mp3-du-script", "audio/mpeg", self.nom), minutage, debut - 350, self.estime)


def audio(client, segments, **corps):
    return client.post(f"{URL}/audio", json={"segments": segments, "langue": "fr", "mode": "monologue", **corps}, headers=UTILISATEUR)


def brancher_voix(*fournisseurs):
    from app.main import app

    app.dependency_overrides[get_chaine_tts] = lambda: ChaineTTS(list(fournisseurs))
    return fournisseurs[0]


def test_audio_une_voix_par_locuteur_en_dialogue(client):
    voix = brancher_voix(FauxVoix())
    r = audio(client, segments_fr(script_dialogue()), mode="dialogue", langue="en")
    assert r.status_code == 200
    d = r.json()["data"]
    assert (d["fournisseur"], d["mime"], d["minutageEstime"], d["dureeMs"]) == ("gemini", "audio/mpeg", True, d["segments"][-1]["finMs"])
    assert base64.b64decode(d["audioBase64"]) == b"ID3-mp3-du-script"
    assert [s["ordre"] for s in d["segments"]] == list(range(1, 17)) and d["segments"][0] == {"ordre": 1, "debutMs": 0, "finMs": 1000}
    lus, langue, pause = voix.demandes[0]
    assert langue == "en" and pause == 350
    assert {s.locuteur for s in lus} == {"PROFESSEUR", "ETUDIANT"}
    assert [s.locuteur for s in lus if s.texte.startswith("Mais pourquoi")] == ["ETUDIANT"]


def test_audio_monologue_tout_au_professeur(client):
    voix = brancher_voix(FauxVoix())
    segments = segments_fr(script_dialogue())  # des segments « etudiant »... lus par le professeur en mode monologue
    audio(client, segments, mode="monologue")
    assert {s.locuteur for s in voix.demandes[0][0]} == {"PROFESSEUR"}


def test_audio_minutage_exact_signale_comme_tel(client):
    brancher_voix(FauxVoix("elevenlabs", estime=False))
    d = audio(client, segments_fr()).json()["data"]
    assert d["fournisseur"] == "elevenlabs" and d["minutageEstime"] is False


def test_audio_texte_nettoye_et_segment_vide_non_lu(client):
    voix = brancher_voix(FauxVoix())
    segments = segments_fr()[:4]
    segments[1]["texte"] = "Écris à prof@exemple.tn ou au 22 123 456. **Merci.**"
    segments[2]["texte"] = "***"  # rien à lire après nettoyage
    d = audio(client, segments).json()["data"]
    lus = voix.demandes[0][0]
    assert [s.ordre for s in lus] == [1, 2, 4]
    assert "prof@exemple.tn" not in lus[1].texte and "22 123 456" not in lus[1].texte and "*" not in lus[1].texte
    assert [s["ordre"] for s in d["segments"]] == [1, 2, 3, 4]
    assert d["segments"][2]["debutMs"] == d["segments"][2]["finMs"]  # le segment non lu a une durée nulle


def test_audio_repli_sur_le_fournisseur_suivant(client):
    premier, second = FauxVoix("elevenlabs", panne=True), FauxVoix("gtts")
    brancher_voix(premier, second)
    d = audio(client, segments_fr()).json()["data"]
    assert d["fournisseur"] == "gtts" and len(premier.demandes) == 1 and len(second.demandes) == 1


def test_audio_aucun_fournisseur_503_et_entrees_invalides(client):
    brancher_voix(FauxVoix(panne=True))
    r = audio(client, segments_fr())
    assert r.status_code == 503 and r.json()["error"] == "TTS_UNAVAILABLE"
    assert audio(client, [{"ordre": 1, "texte": "***"}]).status_code == 400
    assert audio(client, []).status_code == 400
    assert audio(client, segments_fr(), langue="es").status_code == 400
