"""Évaluation du module Forum (La Vigie) pour le rapport (plan §9). À LANCER À LA MAIN.

Quatre parties, chacune sur un jeu de données de ce dossier :

- similarite (paires_questions.jsonl) : score lexical, cosinus et recherche hybride (RRF, k = 60) ; précision,
  rappel et F1 selon le seuil, d'où le choix des seuils « similaire » et « doublon » ; Recall@1 et MRR de la
  recherche, par langue (français, anglais, arabe / derja / arabizi) ;
- rag (rag_questions.jsonl, cours/*.md) : récupération (Recall@1, Recall@4, MRR), seuil de pertinence (abstention),
  puis génération par version de prompt : fidélité des citations, bonnes abstentions, réponses inventées hors cours
  (objectif 0), confiance moyenne des réponses justes et fausses ;
- securite (injections.jsonl) : injections neutralisées, données personnelles masquées (sans LLM) ;
- moderation (moderation.jsonl) : matrice de confusion, règles seules puis pipeline complet par version.

Les calculs reprennent ceux de forum-service (mots significatifs, score lexical, RRF, bonus) et le pipeline réel de
l'ai-service (`rag.repondre`, `moderation.moderer`, `nlp.reformuler`).

    cd ai-service
    .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --dry-run      # faux LLM, aucune clé
    .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum                # VRAI LLM : confirmation
    .venv/Scripts/python -m app.modules.forum.evaluation.evaluer_forum --oui --versions v2,v3 --parties rag

Avant tout appel réel, le script annonce le nombre d'appels et demande « o » (sauf --oui). Il ne lit jamais la clé :
le client vient de la fabrique du socle (`get_llm_client`), configurée par le .env comme l'application.
En --dry-run, les vecteurs sont ceux du stub (hachage de mots) : les mesures sémantiques n'ont alors AUCUNE valeur
de sens, elles vérifient seulement la chaîne de calcul. Les mesures lexicales et de sécurité sont, elles, réelles.
"""

import argparse
import asyncio
import csv
import json
import re
import statistics
import sys
import time
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path

from app.core.errors import LLMError
from app.core.llm_client import LLMClient, StubLLMClient, get_llm_client
from app.core.sanitizer import BasicSanitizer, Sanitizer
from app.modules.forum import moderation as module_moderation
from app.modules.forum import nlp as module_nlp
from app.modules.forum import prompts
from app.modules.forum import rag as module_rag
from app.modules.forum.config import ForumAiSettings, get_forum_ai_settings
from app.modules.forum.decoupage import Source, decouper
from app.modules.forum.schemas import (
    ModerationLLM,
    ModererRequest,
    PassageContexte,
    ReformulerRequest,
    RepondreRequest,
    ReponseLLM,
)
from app.modules.forum.securite import neutraliser, preparer

DOSSIER = Path(__file__).resolve().parent
SORTIE_PAR_DEFAUT = DOSSIER / "resultats"
PARTIES = ["similarite", "rag", "securite", "moderation"]
MODULE = "forum"
K_RRF = 60
CANDIDATS = 20
RETENUS = 4
SEUILS = [round(0.30 + 0.025 * i, 3) for i in range(27)]  # 0,30 à 0,95
PRECISION_DOUBLON = 0.95
CATEGORIES = ["OK", "HORS_SUJET", "IRRESPECTUEUX", "SPAM", "FUITE_EXAMEN", "INJECTION", "DONNEES_PERSONNELLES"]

# ---------------------------------------------------------------- calculs repris de forum-service (Similarite.java)

_MOTS = re.compile(r"[^\W_]+", re.UNICODE)
_VIDES = {
    "les", "des", "une", "est", "pas", "que", "qui", "pour", "dans", "avec", "sur", "mon", "mes", "son", "ses", "par",
    "plus", "comme", "sans", "aux", "ces", "cette", "the", "and", "for", "how", "why", "what", "does", "pourquoi",
    "comment", "quoi", "fait", "faire",
}


def mots(texte: str | None) -> set[str]:
    """Mots significatifs : minuscules, sans accents, 3 caractères au moins, hors mots vides (comme en Java)."""
    simple = "".join(c for c in unicodedata.normalize("NFD", (texte or "").lower()) if unicodedata.category(c) != "Mn")
    return {m for m in _MOTS.findall(simple) if len(m) >= 3 and m not in _VIDES}


def lexical(requete: set[str], titre: str | None, contenu: str | None) -> float:
    """Part des mots de la requête présents dans le titre (poids 2) et le contenu (poids 1), entre 0 et 1."""
    if not requete:
        return 0.0
    mt, mc = mots(titre), mots(contenu)
    return sum(2 if m in mt else 1 if m in mc else 0 for m in requete) / (2 * len(requete))


def cosinus(a: list[float], b: list[float]) -> float:
    """Vecteurs normalisés : produit scalaire ; -1 si les dimensions diffèrent."""
    if not a or len(a) != len(b):
        return -1.0
    return sum(x * y for x, y in zip(a, b))


def rrf(classements: list[list[str]], bonus: dict[str, float] | None = None) -> list[tuple[str, float]]:
    """Reciprocal Rank Fusion (k = 60), puis bonus multiplicatif éventuel ; tri décroissant."""
    totaux: dict[str, float] = {}
    for classement in classements:
        for rang, cle in enumerate(classement):
            totaux[cle] = totaux.get(cle, 0.0) + 1.0 / (K_RRF + rang + 1)
    if bonus:
        totaux = {c: s * bonus.get(c, 1.0) for c, s in totaux.items()}
    return sorted(totaux.items(), key=lambda x: -x[1])


def classement(scores: dict[str, float], limite: int = CANDIDATS) -> list[str]:
    """Identifiants des scores strictement positifs, du meilleur au moins bon (comme RagService)."""
    return [c for c, s in sorted(scores.items(), key=lambda x: -x[1]) if s > 0][:limite]


# ---------------------------------------------------------------- métriques


def prf(vp: int, fp: int, fn: int) -> tuple[float, float, float]:
    precision = vp / (vp + fp) if vp + fp else 0.0
    rappel = vp / (vp + fn) if vp + fn else 0.0
    f1 = 2 * precision * rappel / (precision + rappel) if precision + rappel else 0.0
    return round(precision, 3), round(rappel, 3), round(f1, 3)


def courbe(scores: list[tuple[float, bool]], seuils: list[float] = SEUILS) -> list[dict]:
    """Précision, rappel, F1 de la décision « score ≥ seuil ⇒ même question », pour chaque seuil."""
    lignes = []
    for s in seuils:
        vp = sum(1 for x, ok in scores if x >= s and ok)
        fp = sum(1 for x, ok in scores if x >= s and not ok)
        fn = sum(1 for x, ok in scores if x < s and ok)
        p, r, f = prf(vp, fp, fn)
        lignes.append({"seuil": s, "precision": p, "rappel": r, "f1": f})
    return lignes


def choisir_seuils(c: list[dict]) -> tuple[float | None, float | None]:
    """Seuil « similaire » = meilleur F1 ; « doublon » = plus petit seuil dont la précision atteint 0,95."""
    if not c:
        return None, None
    similaire = max(c, key=lambda x: (x["f1"], x["seuil"]))
    doublon = next((x["seuil"] for x in c if x["precision"] >= PRECISION_DOUBLON and x["rappel"] > 0), None)
    return (similaire["seuil"] if similaire["f1"] > 0 else None), doublon


def rang(ids: list[str], pertinents: set[str]) -> int | None:
    """Rang (1 = premier) du premier élément pertinent, None s'il n'apparaît pas."""
    return next((i + 1 for i, c in enumerate(ids) if c in pertinents), None)


def recall_at(rangs: list[int | None], k: int) -> float:
    return round(sum(1 for r in rangs if r is not None and r <= k) / len(rangs), 3) if rangs else 0.0


def mrr(rangs: list[int | None]) -> float:
    return round(sum(1 / r for r in rangs if r) / len(rangs), 3) if rangs else 0.0


def matrice(vrais: list[str], predits: list[str], classes: list[str] = CATEGORIES) -> dict[str, dict[str, int]]:
    m = {a: {p: 0 for p in classes} for a in classes}
    for a, p in zip(vrais, predits):
        m.setdefault(a, {c: 0 for c in classes})[p] = m.get(a, {}).get(p, 0) + 1
    return m


def scores_classes(m: dict[str, dict[str, int]]) -> dict[str, tuple[float, float, float]]:
    resultat = {}
    for c in m:
        vp = m[c].get(c, 0)
        fp = sum(m[a].get(c, 0) for a in m if a != c)
        fn = sum(n for p, n in m[c].items() if p != c)
        resultat[c] = prf(vp, fp, fn)
    return resultat


def normaliser(texte: str) -> str:
    return re.sub(r"\s+", " ", texte.lower()).strip()


# ---------------------------------------------------------------- données


def lire_jsonl(chemin: Path) -> list[dict]:
    return [json.loads(l) for l in chemin.read_text(encoding="utf-8").splitlines() if l.strip()]


def lire_cours(dossier: Path) -> dict[str, str]:
    return {f.stem: f.read_text(encoding="utf-8") for f in sorted(dossier.glob("*.md"))}


def valider_donnees(cours: dict[str, str], questions: list[dict]) -> list[str]:
    """Erreurs du jeu RAG : cours inconnu, passage attendu absent du texte du cours."""
    erreurs = []
    for q in questions:
        if q["cours"] not in cours:
            erreurs.append(f"{q['id']} : cours inconnu {q['cours']}")
        elif not q["hors_cours"] and normaliser(q["attendu"]) not in normaliser(cours[q["cours"]]):
            erreurs.append(f"{q['id']} : passage attendu absent du cours")
    return erreurs


# ---------------------------------------------------------------- faux LLM (--dry-run)


class LLMFactice(StubLLMClient):
    """Vecteurs du stub (hachage) et réponses JSON déterministes qui diffèrent selon la version du prompt.

    repondre : v1 ne s'abstient jamais et invente une citation une fois sur trois ; v2 s'abstient si la question
    recouvre peu le passage et ne cite qu'une phrase sur deux ; v3 s'abstient de même et cite chaque phrase.
    moderer : v1 ne reconnaît que les insultes et le spam, v2 ajoute les autres catégories, v3 aussi l'injection.
    Aucun réseau : sert à vérifier toute la chaîne de calcul, pas à mesurer un vrai modèle.
    """

    nom = "dry-run"

    def __init__(self) -> None:
        self.appels = 0
        self._systemes = {
            (tache, v): prompts.prompt(tache, v).systeme
            for tache in ("repondre", "moderer") for v in prompts.versions_disponibles(tache)
        }

    def _version(self, tache: str, systeme: str) -> str:
        return next((v for (t, v), s in self._systemes.items() if t == tache and s == systeme), "v3")

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        self.appels += 1
        systeme, utilisateur = messages[0].content, messages[-1].content
        if schema is ReponseLLM:
            return self._repondre(self._version("repondre", systeme), utilisateur)
        if schema is ModerationLLM:
            return self._moderer(self._version("moderer", systeme), utilisateur)
        raise LLMError("Schéma non simulé en --dry-run")

    def _repondre(self, version: str, utilisateur: str) -> ReponseLLM:
        question = re.search(r"<question>\n(.*?)\n</question>", utilisateur, re.S)
        passages = re.findall(r'<passage id="(\d+)"[^>]*>\n(.*?)\n</passage>', utilisateur, re.S)
        q = mots(question.group(1) if question else "")
        recouvrement = {int(n): len(q & mots(t)) / max(1, len(q)) for n, t in passages}
        meilleur = max(recouvrement, key=recouvrement.get) if recouvrement else 1
        texte = dict((int(n), t) for n, t in passages).get(meilleur, "")
        phrase = re.split(r"(?<=[.!?])\s+", texte.strip())[0] if texte else ""
        citation = " ".join(phrase.split()[:14])
        if version != "v1" and recouvrement.get(meilleur, 0) < 0.25:
            return ReponseLLM(reponse="", hors_cours=True)
        if version == "v1" and self.appels % 3 == 0:
            citation = "Le cours affirme ici une chose qui ne figure dans aucun passage fourni."
        if version == "v2":
            reponse = f"D'après le cours, {citation.lower()} [{meilleur}]. C'est une notion importante à retenir."
        else:
            reponse = f"D'après le cours, {citation.lower()} [{meilleur}]."
        return ReponseLLM(reponse=reponse, hors_cours=False,
                          citations=[{"passage": meilleur, "citation": citation}] if citation else [])

    _INDICES = {
        "IRRESPECTUEUX": ("nul", "bete", "idiot", "debile", "incapable", "stupide", "pied", "primaire", "filles"),
        "SPAM": ("gagnez", "promo", "instagram", "crypto", "chaine", "concours"),
        "HORS_SUJET": ("match", "vends", "elections", "pizza", "coiffeur", "gateau", "telephone", "copine"),
        "FUITE_EXAMEN": ("examen", "certificat", "test final"),
        "DONNEES_PERSONNELLES": ("rue", "identite", "mot de passe"),
        "INJECTION": ("a partir de maintenant", "classe-le", "moderateur ia", "sans regles"),
    }

    def _moderer(self, version: str, utilisateur: str) -> ModerationLLM:
        texte = "".join(c for c in unicodedata.normalize("NFD", utilisateur.lower()) if unicodedata.category(c) != "Mn")
        connues = {"v1": ("IRRESPECTUEUX", "SPAM")}.get(
            version, tuple(c for c in self._INDICES if version == "v3" or c != "INJECTION"))
        for categorie in connues:
            if any(i in texte for i in self._INDICES[categorie]):
                return ModerationLLM(categorie=categorie, score=0.9, raison="Indice reconnu (dry-run)")
        return ModerationLLM(categorie="OK", score=0.0, raison="Rien à signaler (dry-run)")


# ---------------------------------------------------------------- mesures


@dataclass
class Rapport:
    """Tout ce qui finit dans RESULTATS.md (et dans les CSV)."""

    mode: str
    modele_embedding: str = ""
    lignes: dict[str, list[dict]] = field(default_factory=dict)
    resume: dict[str, object] = field(default_factory=dict)


async def vectoriser(llm: LLMClient, textes: list[str], tache: str) -> tuple[list[list[float]], str]:
    r = await llm.embed(textes, module=MODULE, tache=tache)
    return r.vecteurs, r.modele


async def reformuler_tout(llm: LLMClient, textes: list[str], sanitizer: Sanitizer,
                          reglages: ForumAiSettings) -> list[str]:
    sorties = []
    for t in textes:
        r = await module_nlp.reformuler(ReformulerRequest(question=t), llm, sanitizer, reglages)
        sorties.append(r.requete)
    return sorties


async def evaluer_similarite(llm: LLMClient, paires: list[dict], rapport: Rapport, sanitizer: Sanitizer,
                             reglages: ForumAiSettings, reformuler: bool) -> None:
    requetes = [p["a"] for p in paires]
    if reformuler:
        requetes = await reformuler_tout(llm, requetes, sanitizer, reglages)
    documents = sorted({p["b"] for p in paires})
    vq, modele = await vectoriser(llm, requetes, "REQUETE")
    vd, _ = await vectoriser(llm, documents, "DOCUMENT")
    rapport.modele_embedding = modele
    index = {d: i for i, d in enumerate(documents)}
    lignes, cos_pairs, lex_pairs = [], [], []
    rangs: dict[str, dict[str, list[int | None]]] = {m: {} for m in ("lexical", "semantique", "hybride")}
    for i, p in enumerate(paires):
        q = mots(requetes[i])
        cos = cosinus(vq[i], vd[index[p["b"]]])
        lex = lexical(q, p["b"], "")
        cos_pairs.append((cos, p["meme"]))
        lex_pairs.append((lex, p["meme"]))
        lignes.append({"id": p["id"], "langue": p["langue"], "meme": p["meme"], "cosinus": round(cos, 4),
                       "lexical": round(lex, 4)})
        if p["meme"]:
            lexicaux = {d: lexical(q, d, "") for d in documents}
            semantiques = {d: max(0.0, cosinus(vq[i], vd[index[d]])) for d in documents}
            cl, cs = classement(lexicaux), classement(semantiques)
            hybride = [c for c, _ in rrf([cl, cs])]
            for methode, ids in (("lexical", cl), ("semantique", cs), ("hybride", hybride)):
                for groupe in ("tous", p["langue"]):
                    rangs[methode].setdefault(groupe, []).append(rang(ids, {p["b"]}))
    seuils_lignes = [{"methode": "semantique", **x} for x in courbe(cos_pairs)]
    seuils_lignes += [{"methode": "lexical", **x} for x in courbe(lex_pairs, [round(0.05 * i, 2) for i in range(1, 20)])]
    similaire, doublon = choisir_seuils([x for x in seuils_lignes if x["methode"] == "semantique"])
    sim_lex, _ = choisir_seuils([x for x in seuils_lignes if x["methode"] == "lexical"])
    recherche = [{"methode": m, "groupe": g, "n": len(r), "recall_1": recall_at(r, 1), "recall_5": recall_at(r, 5),
                  "mrr": mrr(r)} for m, groupes in rangs.items() for g, r in sorted(groupes.items())]
    rapport.lignes.update(similarite_paires=lignes, similarite_seuils=seuils_lignes, recherche=recherche)
    rapport.resume.update(seuil_similaire=similaire, seuil_doublon=doublon, seuil_lexical=sim_lex,
                          reformulation=reformuler)


def _bonne_reponse(q: dict, sources: list) -> bool:
    return any(normaliser(q["attendu"]) in normaliser(s.passage) for s in sources)


async def evaluer_rag(llm: LLMClient, cours: dict[str, str], questions: list[dict], versions: list[str],
                      rapport: Rapport, sanitizer: Sanitizer, reglages: ForumAiSettings, generation: bool) -> None:
    passages = {nom: decouper([Source(texte=t, titre=nom)], reglages.forum_taille_passage, reglages.forum_chevauchement)
                for nom, t in cours.items()}
    vecteurs: dict[str, list[list[float]]] = {}
    for nom, ps in passages.items():
        vecteurs[nom], modele = await vectoriser(llm, [p.texte for p in ps], "DOCUMENT")
        rapport.modele_embedding = modele
    vq, _ = await vectoriser(llm, [q["question"] for q in questions], "REQUETE")
    recuperation, retenus_par_question = [], {}
    for i, q in enumerate(questions):
        ps, vs = passages[q["cours"]], vecteurs[q["cours"]]
        qm = mots(q["question"])
        cos = {str(j): max(0.0, cosinus(vq[i], vs[j])) for j in range(len(ps))}
        lex = {str(j): lexical(qm, ps[j].titre_section, ps[j].texte) for j in range(len(ps))}
        fusion = [c for c, _ in rrf([classement(lex), classement(cos)])][:RETENUS]
        pertinents = set() if q["hors_cours"] else {
            str(j) for j, p in enumerate(ps) if normaliser(q["attendu"]) in normaliser(p.texte)}
        meilleur = max((cos[c] for c in fusion), default=0.0)
        retenus_par_question[q["id"]] = (fusion, cos)
        recuperation.append({"id": q["id"], "cours": q["cours"], "hors_cours": q["hors_cours"],
                             "rang": rang(fusion, pertinents), "meilleur_cosinus": round(meilleur, 4)})
    dans = [r for r in recuperation if not r["hors_cours"]]
    rangs = [r["rang"] for r in dans]
    # Seuil de pertinence : meilleure exactitude « répondre si dans le cours, s'abstenir sinon ».
    exactitudes = []
    for s in SEUILS:
        justes = sum(1 for r in recuperation if (r["meilleur_cosinus"] >= s) != r["hors_cours"])
        exactitudes.append({"seuil": s, "exactitude": round(justes / len(recuperation), 3)})
    seuil = max(exactitudes, key=lambda x: (x["exactitude"], x["seuil"]))
    # Seuil sûr : plus petit seuil où AUCUNE question hors cours ne passe (objectif « 0 réponse inventée »).
    hors = [r["meilleur_cosinus"] for r in recuperation if r["hors_cours"]]
    sur = next((s for s in SEUILS if all(c < s for c in hors)), None)
    servies = round(sum(1 for r in dans if r["meilleur_cosinus"] >= sur) / len(dans), 3) if sur and dans else 0.0
    rapport.lignes.update(rag_recuperation=recuperation, rag_seuils=exactitudes)
    rapport.resume.update(rag_recall_1=recall_at(rangs, 1), rag_recall_4=recall_at(rangs, 4), rag_mrr=mrr(rangs),
                          seuil_pertinence=seuil["seuil"], exactitude_abstention=seuil["exactitude"],
                          seuil_sur=sur, servies_au_seuil_sur=servies)
    if not generation:
        return
    lignes, synthese = [], []
    for v in versions:
        r_version = reglages.model_copy(update={"prompt_forum_version": v})
        mesures = []
        for q in questions:
            fusion, cos = retenus_par_question[q["id"]]
            ps = passages[q["cours"]]
            contexte = [PassageContexte(id=int(c), cours=q["cours"], chapitre=ps[int(c)].titre_section,
                                        texte=ps[int(c)].texte[:4000], score=round(cos[c], 4)) for c in fusion]
            debut = time.perf_counter()
            try:
                r = await module_rag.repondre(RepondreRequest(question=q["question"], passages=contexte), llm,
                                              sanitizer, r_version)
                erreur = ""
            except LLMError as e:
                r, erreur = None, type(e).__name__
            rejetees = sum(1 for a in (r.alertes if r else []) if "introuvable" in a)
            verifiees = len(r.sources) if r else 0
            ligne = {
                "version": v, "id": q["id"], "hors_cours_attendu": q["hors_cours"],
                "abstention": bool(r and r.hors_cours), "juste": bool(r and not r.hors_cours and not q["hors_cours"]
                                                                      and _bonne_reponse(q, r.sources)),
                "citations_verifiees": verifiees, "citations_rejetees": rejetees,
                "confiance": r.confiance if r else None, "duree_s": round(time.perf_counter() - debut, 2),
                "erreur": erreur,
            }
            mesures.append(ligne)
        lignes += mesures
        valides = [m for m in mesures if not m["erreur"]]
        hors = [m for m in valides if m["hors_cours_attendu"]]
        dedans = [m for m in valides if not m["hors_cours_attendu"]]
        verif = sum(m["citations_verifiees"] for m in valides)
        rejet = sum(m["citations_rejetees"] for m in valides)
        conf_justes = [m["confiance"] for m in dedans if m["juste"]]
        conf_fausses = [m["confiance"] for m in valides if not m["juste"] and not m["abstention"]]
        synthese.append({
            "version": v, "questions": len(valides), "erreurs": len(mesures) - len(valides),
            "reponses_justes": round(sum(m["juste"] for m in dedans) / len(dedans), 3) if dedans else 0.0,
            "bonnes_abstentions": round(sum(m["abstention"] for m in hors) / len(hors), 3) if hors else 0.0,
            "inventees_hors_cours": round(sum(not m["abstention"] for m in hors) / len(hors), 3) if hors else 0.0,
            "fidelite_citations": round(verif / (verif + rejet), 3) if verif + rejet else 0.0,
            "confiance_justes": round(statistics.mean(conf_justes), 1) if conf_justes else None,
            "confiance_fausses": round(statistics.mean(conf_fausses), 1) if conf_fausses else None,
        })
    rapport.lignes.update(rag_generation=lignes, rag_versions=synthese)


def evaluer_securite(injections: list[dict], rapport: Rapport, sanitizer: Sanitizer) -> None:
    """Sans LLM : règles de modération, Sanitizer (injection et données personnelles), balises neutralisées."""
    lignes = []
    for x in injections:
        regle = module_moderation.appliquer_regles(None, x["texte"])
        prepare = preparer(sanitizer, x["texte"])
        lignes.append({
            "id": x["id"], "type": x["type"],
            "regle": regle.categorie if regle else "", "sanitizer_injection": prepare.injection_suspectee,
            "balises_neutralisees": neutraliser(x["texte"]) != x["texte"],
            "pii_attendues": x.get("pii", 0), "pii_masquees": prepare.donnees_masquees,
        })
    inj = [l for l in lignes if l["type"] == "injection"]
    pii = [l for l in lignes if l["type"] == "pii"]
    neutralisees = [l for l in inj if l["regle"] == "INJECTION" or l["sanitizer_injection"]]
    rapport.lignes["securite"] = lignes
    rapport.resume.update(
        injections_neutralisees_n=len(neutralisees),
        pii_elements=sum(l["pii_attendues"] for l in pii),
        pii_elements_masques=sum(min(l["pii_masquees"], l["pii_attendues"]) for l in pii),
        pii_signalees_n=sum(l["regle"] == "DONNEES_PERSONNELLES" for l in pii),
    )
    rapport.resume.update(
        injections=len(inj), injections_neutralisees=round(len(neutralisees) / len(inj), 3) if inj else 0.0,
        injections_par_regles=round(sum(l["regle"] == "INJECTION" for l in inj) / len(inj), 3) if inj else 0.0,
        pii_messages=len(pii),
        pii_masquees=round(sum(min(l["pii_masquees"], l["pii_attendues"]) for l in pii)
                           / max(1, sum(l["pii_attendues"] for l in pii)), 3),
        pii_signalees_par_regles=round(sum(l["regle"] == "DONNEES_PERSONNELLES" for l in pii) / len(pii), 3)
        if pii else 0.0,
    )


async def evaluer_moderation(llm: LLMClient, messages: list[dict], versions: list[str], rapport: Rapport,
                             sanitizer: Sanitizer, reglages: ForumAiSettings, avec_llm: bool) -> None:
    vrais = [m["categorie"] for m in messages]
    lignes, syntheses = [], []
    regles = [(module_moderation.appliquer_regles(None, m["texte"]) or None) for m in messages]
    predits = [r.categorie if r else "OK" for r in regles]
    lignes += [{"version": "regles", "id": m["id"], "attendu": a, "predit": p} for m, a, p in zip(messages, vrais, predits)]
    syntheses.append(("regles", vrais, predits))
    for v in (versions if avec_llm else []):
        r_version = reglages.model_copy(update={"prompt_forum_version": v})
        predits_v = []
        for m in messages:
            try:
                r = await module_moderation.moderer(ModererRequest(contenu=m["texte"]), llm, sanitizer, r_version)
                predits_v.append(r.categorie)
            except LLMError:
                predits_v.append("OK")
        lignes += [{"version": v, "id": m["id"], "attendu": a, "predit": p} for m, a, p in zip(messages, vrais, predits_v)]
        syntheses.append((v, vrais, predits_v))
    resume = []
    for v, a, p in syntheses:
        mat = matrice(a, p)
        par_classe = scores_classes(mat)
        resume.append({"version": v, "exactitude": round(sum(x == y for x, y in zip(a, p)) / len(a), 3),
                       "f1_macro": round(statistics.mean(f for _, _, f in par_classe.values()), 3),
                       "matrice": mat, "par_classe": par_classe})
    rapport.lignes["moderation"] = lignes
    rapport.resume["moderation"] = resume


# ---------------------------------------------------------------- sorties


def ecrire_csv(chemin: Path, lignes: list[dict]) -> None:
    if not lignes:
        return
    with chemin.open("w", encoding="utf-8", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(lignes[0].keys()))
        w.writeheader()
        w.writerows(lignes)


def _fmt(x: object) -> str:
    if x is None:
        return "–"
    if isinstance(x, float):
        return f"{x:.3f}".rstrip("0").rstrip(".") if x != int(x) else str(int(x))
    return str(x)


def markdown(rapport: Rapport) -> str:
    r = rapport.resume
    md = [f"# Évaluation du module Forum (La Vigie)\n",
          f"- **Mode** : {rapport.mode}",
          f"- **Modèle d'embeddings** : {rapport.modele_embedding or '–'}",
          f"- **Date** : {time.strftime('%Y-%m-%d %H:%M')}\n"]
    if rapport.mode.startswith("dry-run"):
        md.append("> **Attention : exécution `--dry-run`.** Vecteurs du stub (hachage de mots) et LLM factice : les "
                  "mesures sémantiques et de génération vérifient la chaîne de calcul, elles NE mesurent PAS un vrai "
                  "modèle. Les mesures lexicales, de récupération lexicale, de sécurité et des règles de modération "
                  "sont réelles. Relancer sans `--dry-run` (clé dans le .env) pour fixer les seuils.\n")
    if "recherche" in rapport.lignes:
        md += ["## 1. Questions similaires\n",
               f"Seuil « similaire » (meilleur F1 du cosinus) : **{_fmt(r.get('seuil_similaire'))}** · "
               f"seuil « doublon » (précision ≥ {PRECISION_DOUBLON}) : **{_fmt(r.get('seuil_doublon'))}** · "
               f"seuil lexical (meilleur F1) : {_fmt(r.get('seuil_lexical'))} · reformulation : "
               f"{'oui' if r.get('reformulation') else 'non'}\n",
               "| Méthode | Groupe | Paires | Recall@1 | Recall@5 | MRR |", "|---|---|---|---|---|---|"]
        md += [f"| {x['methode']} | {x['groupe']} | {x['n']} | {_fmt(x['recall_1'])} | {_fmt(x['recall_5'])} | "
               f"{_fmt(x['mrr'])} |" for x in rapport.lignes["recherche"]]
        meilleurs = [x for x in rapport.lignes["similarite_seuils"] if x["methode"] == "semantique"]
        md += ["", "| Seuil cosinus | Précision | Rappel | F1 |", "|---|---|---|---|"]
        md += [f"| {_fmt(x['seuil'])} | {_fmt(x['precision'])} | {_fmt(x['rappel'])} | {_fmt(x['f1'])} |"
               for x in meilleurs if round(x["seuil"] * 40) % 2 == 0]
        lexicaux = [x for x in rapport.lignes["similarite_seuils"] if x["methode"] == "lexical"]
        md += ["", "| Seuil lexical (repli sans vecteurs) | Précision | Rappel | F1 |", "|---|---|---|---|"]
        md += [f"| {_fmt(x['seuil'])} | {_fmt(x['precision'])} | {_fmt(x['rappel'])} | {_fmt(x['f1'])} |"
               for x in lexicaux if round(x["seuil"] * 20) % 2 == 0]
        md.append("")
    if "rag_recuperation" in rapport.lignes:
        md += ["## 2. RAG\n", f"Récupération (questions dans le cours) : Recall@1 **{_fmt(r['rag_recall_1'])}**, "
               f"Recall@4 **{_fmt(r['rag_recall_4'])}**, MRR **{_fmt(r['rag_mrr'])}**. Seuil de pertinence retenu : "
               f"**{_fmt(r['seuil_pertinence'])}** (exactitude « répondre / s'abstenir » : "
               f"{_fmt(r['exactitude_abstention'])}). Seuil sûr (aucune question hors cours ne passe) : "
               f"**{_fmt(r['seuil_sur'])}**, qui sert encore {_fmt(r['servies_au_seuil_sur'])} des questions du "
               "cours.\n"]
        if "rag_versions" in rapport.lignes:
            md += ["| Version | Questions | Réponses justes | Bonnes abstentions | Inventées hors cours | "
                   "Fidélité des citations | Confiance (justes) | Confiance (fausses) | Erreurs |",
                   "|---|---|---|---|---|---|---|---|---|"]
            md += [f"| {x['version']} | {x['questions']} | {_fmt(x['reponses_justes'])} | {_fmt(x['bonnes_abstentions'])} | "
                   f"{_fmt(x['inventees_hors_cours'])} | {_fmt(x['fidelite_citations'])} | {_fmt(x['confiance_justes'])} | "
                   f"{_fmt(x['confiance_fausses'])} | {x['erreurs']} |" for x in rapport.lignes["rag_versions"]]
            md.append("")
    if "securite" in rapport.lignes:
        md += ["## 3. Sécurité (sans LLM)\n",
               f"- Injections neutralisées (règles ou Sanitizer) : **{r['injections_neutralisees_n']} / "
               f"{r['injections']}** (taux {_fmt(r['injections_neutralisees'])} ; règles seules : "
               f"{_fmt(r['injections_par_regles'])})",
               f"- Données personnelles masquées avant le LLM : **{r['pii_elements_masques']} / {r['pii_elements']}** "
               f"éléments (taux {_fmt(r['pii_masquees'])}) dans {r['pii_messages']} messages ; signalées à l'auteur "
               f"par les règles : {r['pii_signalees_n']} / {r['pii_messages']} messages\n"]
    if "moderation" in r:
        md += ["## 4. Modération\n", "| Version | Exactitude | F1 macro |", "|---|---|---|"]
        md += [f"| {x['version']} | {_fmt(x['exactitude'])} | {_fmt(x['f1_macro'])} |" for x in r["moderation"]]
        derniere = r["moderation"][-1]
        md += ["", f"Matrice de confusion ({derniere['version']}, lignes = attendu, colonnes = prédit) :\n",
               "| | " + " | ".join(c[:6] for c in CATEGORIES) + " |", "|---" * (len(CATEGORIES) + 1) + "|"]
        md += [f"| {a} | " + " | ".join(str(derniere["matrice"][a][p]) for p in CATEGORIES) + " |" for a in CATEGORIES]
        md.append("")
    return "\n".join(md) + "\n"


# ---------------------------------------------------------------- exécution


def appels_prevus(parties: list[str], versions: list[str], paires: int, questions: int, messages: int,
                  reformuler: bool, generation: bool) -> int:
    n = 0
    if "similarite" in parties:
        n += 2 + (paires if reformuler else 0)
    if "rag" in parties:
        n += 4 + (questions * len(versions) if generation else 0)
    if "moderation" in parties:
        n += messages * len(versions)
    return n


async def executer(args: argparse.Namespace) -> Rapport:
    versions = args.versions
    inconnues = [v for v in versions if v not in prompts.versions_disponibles("repondre")]
    if inconnues:
        raise SystemExit(f"Version(s) de prompt inconnue(s) : {', '.join(inconnues)}")
    paires = lire_jsonl(DOSSIER / "paires_questions.jsonl")[: args.limite]
    questions = lire_jsonl(DOSSIER / "rag_questions.jsonl")
    cours = lire_cours(DOSSIER / "cours")
    erreurs = valider_donnees(cours, questions)
    if erreurs:
        raise SystemExit("Jeu de données RAG invalide : " + " ; ".join(erreurs))
    questions = questions[: args.limite]
    injections = lire_jsonl(DOSSIER / "injections.jsonl")
    messages = lire_jsonl(DOSSIER / "moderation.jsonl")[: args.limite]

    llm: LLMClient = LLMFactice() if args.dry_run else get_llm_client()
    if not args.dry_run:
        n = appels_prevus(args.parties, versions, len(paires), len(questions), len(messages), args.reformuler,
                          not args.sans_generation)
        print(f"Environ {n} appels au fournisseur LLM ({getattr(llm, 'nom', '?')}), versions {', '.join(versions)}.")
        if not args.oui and input("Continuer ? (o/N) ").strip().lower() != "o":
            raise SystemExit("Annulé.")
    sanitizer = BasicSanitizer(20_000)
    reglages = get_forum_ai_settings()
    rapport = Rapport(mode="dry-run (faux LLM, vecteurs du stub)" if args.dry_run else f"réel ({getattr(llm, 'nom', '?')})")
    if "similarite" in args.parties:
        await evaluer_similarite(llm, paires, rapport, sanitizer, reglages, args.reformuler and not args.dry_run)
    if "rag" in args.parties:
        await evaluer_rag(llm, cours, questions, versions, rapport, sanitizer, reglages, not args.sans_generation)
    if "securite" in args.parties:
        evaluer_securite(injections, rapport, sanitizer)
    if "moderation" in args.parties:
        await evaluer_moderation(llm, messages, versions, rapport, sanitizer, reglages, True)
    return rapport


def enregistrer(rapport: Rapport, sortie: Path) -> list[Path]:
    sortie.mkdir(parents=True, exist_ok=True)
    fichiers = []
    for nom, lignes in rapport.lignes.items():
        chemin = sortie / f"{nom}.csv"
        ecrire_csv(chemin, lignes)
        fichiers.append(chemin)
    md = sortie / "RESULTATS.md"
    md.write_text(markdown(rapport), encoding="utf-8")
    return fichiers + [md]


def analyser_arguments(argv: list[str] | None = None) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Évaluation du module Forum (La Vigie).")
    p.add_argument("--dry-run", action="store_true", help="faux LLM et vecteurs du stub : aucune clé, aucun quota")
    p.add_argument("--oui", action="store_true", help="ne pas demander de confirmation avant les appels réels")
    p.add_argument("--versions", default="v1,v2,v3", type=lambda s: [v.strip() for v in s.split(",") if v.strip()])
    p.add_argument("--parties", default=",".join(PARTIES),
                   type=lambda s: [x.strip() for x in s.split(",") if x.strip() in PARTIES])
    p.add_argument("--sortie", type=Path, default=SORTIE_PAR_DEFAUT)
    p.add_argument("--limite", type=int, default=None, help="nombre maximal d'éléments par jeu (essai rapide)")
    p.add_argument("--reformuler", action="store_true", help="reformuler les questions avant la recherche (LLM)")
    p.add_argument("--sans-generation", action="store_true", help="RAG : récupération seulement, sans LLM")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = analyser_arguments(argv)
    rapport = asyncio.run(executer(args))
    for f in enregistrer(rapport, args.sortie):
        print("écrit :", f)
    return 0


if __name__ == "__main__":
    sys.exit(main())
