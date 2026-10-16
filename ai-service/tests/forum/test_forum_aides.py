"""Lot 7, ai-service : résumé de fil, « Améliorer ma question », nom des thèmes (LLM simulé, replis sans LLM)."""

import pytest

from app.core.errors import LLMInvalidResponseError, LLMQuotaError
from app.modules.forum import prompts
from app.modules.forum.aides import manques_par_regles, noms_par_frequence

ENTETES = {"X-User-Id": "10", "X-User-Role": "ENSEIGNANT"}

FIL = {
    "titre": "Pourquoi mon LEFT JOIN renvoie autant de lignes qu'un INNER JOIN ?",
    "messages": [
        {"role": "VIGIE", "texte": "La condition du WHERE s'applique après la jointure [1]."},
        {"role": "REPONSE", "texte": "[ACCEPTÉE] Déplace la condition dans le ON. Écris-moi : sara@exemple.tn"},
        {"role": "COMMENTAIRE", "texte": "Merci, ça marche !"},
    ],
}


# ---------------------------------------------------------------- Résumé


def test_le_resume_garde_au_plus_cinq_puces_nettoyees(client, faux_llm):
    faux_llm.reponses.append({"puces": ["- Problème : LEFT JOIN filtré", "Cause : WHERE", "Solution : ON",
                                        "4", "5", "6", "  "]})
    r = client.post("/api/ai/forum/resumer", headers=ENTETES, json=FIL)
    data = r.json()["data"]
    assert r.status_code == 200
    assert data["puces"][:3] == ["Problème : LEFT JOIN filtré", "Cause : WHERE", "Solution : ON"]
    assert len(data["puces"]) == 5 and data["versionPrompt"] == "v2"


def test_le_fil_part_entre_balises_sans_donnee_personnelle(client, faux_llm):
    faux_llm.reponses.append({"puces": ["a", "b", "c"]})
    client.post("/api/ai/forum/resumer", headers=ENTETES, json=FIL)
    systeme, utilisateur = faux_llm.messages[0]
    assert utilisateur.content.startswith("<fil>") and "QUESTION : Pourquoi" in utilisateur.content
    assert "sara@exemple.tn" not in utilisateur.content and "[EMAIL]" in utilisateur.content
    assert "nom de personne" in systeme.content


def test_un_resume_vide_dit_qu_aucune_solution_n_est_retenue(client, faux_llm):
    faux_llm.reponses.append({"puces": []})
    data = client.post("/api/ai/forum/resumer", headers=ENTETES, json=FIL).json()["data"]
    assert data["puces"] == ["Pas encore de solution retenue dans ce fil."]


def test_sans_llm_le_resume_renvoie_une_erreur_propre(client, faux_llm):
    faux_llm.reponses.append(LLMQuotaError("quota"))
    r = client.post("/api/ai/forum/resumer", headers=ENTETES, json=FIL)
    assert r.status_code >= 400 and r.json()["success"] is False


# ---------------------------------------------------------------- Améliorer ma question


def ameliorer(client, **corps):
    base = {"titre": "aide join", "contenu": "ça marche pas", "etiquettesExistantes": ["SQL", "jointures", "index"]}
    return client.post("/api/ai/forum/ameliorer", headers=ENTETES, json=base | corps).json()["data"]


def test_les_etiquettes_proposees_sont_prises_dans_la_liste_existante(client, faux_llm):
    faux_llm.reponses.append({"titre": "Pourquoi mon LEFT JOIN perd-il des lignes ?",
                              "manques": ["Ajoute ta requête.", "Copie le message d'erreur.", "Dis ce que tu as essayé.",
                                          "Un quatrième"],
                              "etiquettes": ["sql", "Jointures", "kubernetes", "SQL"]})
    data = ameliorer(client)
    assert data["titre"] == "Pourquoi mon LEFT JOIN perd-il des lignes ?"
    assert data["etiquettes"] == ["SQL", "jointures"]
    assert len(data["manques"]) == 3 and data["repli"] is False


def test_un_titre_vide_garde_le_titre_d_origine(client, faux_llm):
    faux_llm.reponses.append({"titre": " ", "manques": [], "etiquettes": []})
    assert ameliorer(client)["titre"] == "aide join"


@pytest.mark.parametrize("erreur", [LLMQuotaError("quota"), LLMInvalidResponseError("json")])
def test_sans_llm_les_regles_proposent_les_manques(client, faux_llm, erreur):
    faux_llm.reponses.append(erreur)
    data = ameliorer(client, titre="Erreur sur ma requête SELECT", contenu="J'ai une erreur avec mon JOIN")
    assert data["repli"] is True and data["etiquettes"] == []
    assert data["titre"] == "Erreur sur ma requête SELECT"
    assert data["manques"][0] == "Copie le message d'erreur exact."


def test_les_regles_ne_reclament_pas_ce_qui_est_deja_la():
    contenu = ("J'ai essayé de déplacer la condition. Ma requête : `SELECT * FROM a LEFT JOIN b ON a.id = b.a_id` "
               "renvoie 3 lignes au lieu de 5.")
    assert manques_par_regles("LEFT JOIN", contenu) == []


# ---------------------------------------------------------------- Nom des thèmes


GROUPES = {"groupes": [
    {"titres": ["LEFT JOIN perd des lignes", "Condition WHERE après LEFT JOIN"]},
    {"titres": ["HAVING ou WHERE ?", "GROUP BY avec HAVING"]},
]}


def test_chaque_groupe_recoit_un_nom(client, faux_llm):
    faux_llm.reponses.append({"noms": ["Conditions dans LEFT JOIN", "Agrégats avec HAVING"]})
    r = client.post("/api/ai/forum/nommer-groupes", headers=ENTETES, json=GROUPES)
    data = r.json()["data"]
    assert data["noms"] == ["Conditions dans LEFT JOIN", "Agrégats avec HAVING"] and data["repli"] is False
    utilisateur = faux_llm.messages[0][1].content
    assert '<groupe id="1">' in utilisateur and '<groupe id="2">' in utilisateur


@pytest.mark.parametrize("sortie", [
    {"noms": ["Un seul nom"]},
    {"noms": ["Même nom", "même nom"]},
    {"noms": ["Nom", " "]},
    LLMQuotaError("quota"),
])
def test_des_noms_invalides_ou_un_llm_absent_donnent_les_mots_frequents(client, faux_llm, sortie):
    faux_llm.reponses.append(sortie)
    data = client.post("/api/ai/forum/nommer-groupes", headers=ENTETES, json=GROUPES).json()["data"]
    assert data["repli"] is True
    assert data["noms"][0].lower().startswith("left join") or "join" in data["noms"][0].lower()
    assert "having" in data["noms"][1].lower()


def test_les_noms_de_repli_sont_distincts():
    noms = noms_par_frequence([["Index SQL"], ["Index SQL"], []])
    assert len(set(noms)) == 3 and noms[2] == "Thème 3"


def test_les_versions_des_aides_existent():
    for tache in ("resumer", "ameliorer", "nommer_groupes"):
        assert prompts.versions_disponibles(tache) == ["v1", "v2"]
        assert prompts.prompt(tache, "v3").version == "v2"
