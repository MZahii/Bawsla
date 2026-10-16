"""Serveur ai-service de démonstration pour les tests boîte noire du module Quiz.

Lance la VRAIE application (app.main:app, mêmes routes, mêmes validations, même Sanitizer) en remplaçant
seulement le fournisseur LLM par un simulateur déterministe : aucune clé API, aucun appel réseau.

    cd ai-service
    .venv/Scripts/python -m tests.quiz.boite_noire.serveur_demo            # LLM simulé, port 8000
    .venv/Scripts/python -m tests.quiz.boite_noire.serveur_demo --llm stub # sans LLM (quel que soit .env) : 503

Le simulateur produit volontairement, à chaque génération :
- une question mal formée (2 bonnes réponses) → REJETEE par la validation stricte ;
- une question « piège » sur laquelle le vérificateur diverge → REJETEE par la Self-Consistency.
Injection de panne : un thème ou une notion contenant « [panne-429] » simule un quota épuisé (429).
"""

import argparse
import re

import uvicorn

from app.core import llm_client as socle_llm
from app.core.errors import LLMQuotaError
from app.core.llm_client import LLMClient, LLMMessage, LLMResult, StubLLMClient, get_llm_client
from app.main import app
from app.modules.quiz.schemas import GenerationLlm, RemediationLlm, VerificationLlm

PANNE_429 = "[panne-429]"
ENONCE_PIEGE = "Quelle clause garantit qu'aucune ligne de la table de gauche n'est perdue ?"
_BLOC = re.compile(r"^(\d+)\. (.*)$")
_CHOIX = re.compile(r"^\s+([ABCD])\. (.*)$")

# Banque de questions réalistes (jointures SQL) : (énoncé, bonne réponse, [(distracteur, erreur)], explication)
BANQUE = [
    (
        "Tu veux lister tous les clients, même ceux sans commande. Quelle jointure utilises-tu ?",
        "LEFT JOIN de clients vers commandes",
        [
            ("INNER JOIN entre clients et commandes", "Croit que INNER JOIN garde les lignes sans correspondance"),
            ("RIGHT JOIN de clients vers commandes", "Inverse les tables gauche et droite"),
            ("CROSS JOIN entre clients et commandes", "Confond jointure externe et produit cartésien"),
        ],
        "LEFT JOIN conserve toutes les lignes de la table de gauche, avec NULL quand il n'y a pas de commande.",
    ),
    (
        "Que renvoie un INNER JOIN entre deux tables sans aucune valeur de clé commune ?",
        "Aucune ligne",
        [
            ("Toutes les lignes de la table de gauche", "Confond INNER JOIN et LEFT JOIN"),
            ("Le produit cartésien des deux tables", "Confond INNER JOIN et CROSS JOIN"),
            ("Une erreur de syntaxe", "Croit qu'une jointure vide est interdite"),
        ],
        "INNER JOIN ne garde que les paires qui vérifient la condition ON : sans correspondance, le résultat est vide.",
    ),
    (
        "Dans un LEFT JOIN, où places-tu un filtre sur la table de droite pour garder les lignes sans correspondance ?",
        "Dans la clause ON",
        [
            ("Dans la clause WHERE", "Ignore que WHERE élimine les lignes NULL et annule la jointure externe"),
            ("Dans la clause GROUP BY", "Confond filtrage et regroupement"),
            ("Dans la clause ORDER BY", "Confond filtrage et tri"),
        ],
        "Un filtre dans WHERE supprime les lignes où la table de droite est NULL ; dans ON, il les conserve.",
    ),
    (
        "Pourquoi une jointure sans condition ON multiplie-t-elle le nombre de lignes ?",
        "Elle forme toutes les paires possibles (produit cartésien)",
        [
            ("Elle duplique les clés primaires", "Croit que la clé primaire est recopiée"),
            ("Elle ajoute des lignes NULL", "Confond avec une jointure externe"),
            ("Elle trie puis répète les lignes", "Attribue au tri un effet sur le nombre de lignes"),
        ],
        "Sans condition, chaque ligne de A est associée à chaque ligne de B : |A| × |B| lignes.",
    ),
]


def _question(index: int, competence: str, niveau: str) -> dict:
    enonce, bonne, distracteurs, explication = BANQUE[index % len(BANQUE)]
    return {
        "enonce": enonce,
        "niveauBloom": niveau,
        "competence": competence,
        "choix": [{"texte": bonne, "estCorrecte": True}]
        + [{"texte": t, "estCorrecte": False, "erreurAssociee": e} for t, e in distracteurs],
        "explication": explication,
    }


def _objectifs(texte: str) -> tuple[list[str], list[str], int]:
    competences = re.findall(r"^- (.+)$", texte.split("<objectifs>")[-1], re.MULTILINE)
    niveaux = re.search(r"Niveaux de Bloom à couvrir : (.+)", texte).group(1).split(", ")
    nb = int(re.search(r"Écris exactement (\d+) questions", texte).group(1))
    return competences, niveaux, nb


class LLMSimule(LLMClient):
    """Réponses plausibles et reproductibles, au format JSON brut qu'un vrai fournisseur renverrait."""

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="(simulé)", model="simulateur", fournisseur="simulateur")

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="(simulé)", model="simulateur", fournisseur="simulateur")

    async def synthetiser(self, texte, voix, langue):
        raise NotImplementedError

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        raise NotImplementedError

    async def complete_json(self, messages: list[LLMMessage], schema, *, module, temperature=0.0, max_tokens=2048):
        texte = messages[-1].content
        if PANNE_429 in texte:
            raise LLMQuotaError("Quota du fournisseur LLM atteint (panne simulée)")
        if schema is GenerationLlm:
            brut = self._generation(texte)
        elif schema is VerificationLlm:
            brut = self._verification(texte)
        elif schema is RemediationLlm:
            brut = self._remediation(texte)
        else:
            raise NotImplementedError(schema.__name__)
        socle_llm._fournisseur_utilise.set("simulateur")  # comme LLMRoutage : lu par fournisseur_courant()
        return schema.model_validate(brut)

    def _generation(self, texte: str) -> dict:
        competences, niveaux, nb = _objectifs(texte)
        questions = [_question(i, competences[i % len(competences)], niveaux[i % len(niveaux)]) for i in range(nb)]
        if nb >= 2:  # mal formée : deux bonnes réponses
            questions[-1]["choix"][1]["estCorrecte"] = True
        if nb >= 3:  # piège : le vérificateur se trompera dessus
            questions[-2]["enonce"] = ENONCE_PIEGE
        return {"questions": questions}

    def _verification(self, texte: str) -> dict:
        bonnes = {b for _, b, _, _ in BANQUE}
        reponses, numero, choix, enonce = [], 0, {}, ""
        for ligne in texte.splitlines() + ["0. fin"]:
            if m := _BLOC.match(ligne):
                if numero:
                    cible = next((l for l, t in choix.items() if (t in bonnes) != (enonce == ENONCE_PIEGE)), "A")
                    reponses.append({"numero": numero, "choix": cible, "justification": "Raisonnement simulé."})
                numero, enonce, choix = int(m.group(1)), m.group(2), {}
            elif (m := _CHOIX.match(ligne)) and numero:
                choix[m.group(1)] = m.group(2)
        return {"reponses": reponses}

    def _remediation(self, texte: str) -> dict:
        champ = lambda nom: (re.search(rf"^{nom} : (.*)$", texte, re.MULTILINE) or [None, ""])[1]  # noqa: E731
        notion, niveau = champ("Notion"), champ("Niveau de Bloom")
        donne, bonne = champ("Choix de l'apprenant"), champ("Bonne réponse")
        rattrapage = _question(1, notion, "COMPRENDRE" if niveau != "MEMORISER" else "MEMORISER")
        return {
            "raisonnement": [
                f"La bonne réponse est « {bonne} » car elle correspond exactement à la notion visée.",
                f"L'apprenant a choisi « {donne} », ce qui révèle une confusion entre les deux mécanismes.",
                "L'erreur associée proposée décrit bien cette confusion : le diagnostic est confirmé.",
                "Une question plus simple sur le même mécanisme permettra de vérifier la correction.",
            ],
            "explication": f"Tu as choisi « {donne} » : c'est une confusion fréquente. "
            f"Ici, « {bonne} » est la bonne réponse, parce qu'elle respecte la règle de la notion.",
            "conseil": "Reprends un petit exemple à la main avec deux tables de trois lignes et compare les résultats.",
            "questionRattrapage": rattrapage,
        }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--llm", choices=("simule", "stub"), default="simule")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    # Les deux modes remplacent le fournisseur : même si .env configure Gemini, aucun appel réel ne part.
    app.dependency_overrides[get_llm_client] = LLMSimule if args.llm == "simule" else StubLLMClient
    uvicorn.run(app, host="127.0.0.1", port=args.port, log_level="warning")


if __name__ == "__main__":
    main()
