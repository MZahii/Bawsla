"""Grille des cinq niveaux de difficulté, insérée telle quelle dans le prompt de génération v2.

Chaque niveau fixe des critères observables (nombre de notions combinées, étapes de raisonnement, forme de
l'énoncé, proximité des distracteurs) pour que deux niveaux voisins produisent des questions vraiment
différentes, et pas seulement un adjectif différent dans le prompt. La difficulté est indépendante des
niveaux de Bloom : un QCM « Se souvenir » peut être MASTER (détail rare), un QCM « Analyser » peut être FACILE.
"""

from dataclasses import dataclass

from app.modules.quiz.schemas import Difficulte


@dataclass(frozen=True)
class Niveau:
    nom: str
    consigne: str
    # Sortie estimée par question : un énoncé de niveau élevé est plus long (cas, extrait de code, explication nuancée).
    jetons_par_question: int


GRILLE: dict[Difficulte, Niveau] = {
    Difficulte.FACILE: Niveau(
        "facile (1 sur 5)",
        "une seule notion ; énoncé d'une phrase, sans code ni mise en situation ; une seule étape de raisonnement "
        "(reconnaître une définition, un mot-clé, un fait) ; les distracteurs sont clairement faux pour qui "
        "connaît la notion.",
        350,
    ),
    Difficulte.INTERMEDIAIRE: Niveau(
        "intermédiaire (2 sur 5)",
        "une notion appliquée à une situation simple et familière ; énoncé de 1 à 2 phrases ; une ou deux étapes "
        "de raisonnement ; chaque distracteur correspond à une erreur courante de débutant.",
        450,
    ),
    Difficulte.AVANCE: Niveau(
        "avancé (3 sur 5)",
        "deux notions combinées ; mise en situation concrète de 2 à 3 phrases, ou court extrait (code, requête, "
        "commande, configuration) ; deux étapes de raisonnement ; les distracteurs viennent de confusions entre "
        "notions voisines.",
        550,
    ),
    Difficulte.DIFFICILE: Niveau(
        "difficile (4 sur 5)",
        "cas réaliste avec au moins une contrainte (performance, sécurité, compatibilité, cas d'erreur) ; trois "
        "étapes de raisonnement ou plus ; au moins un distracteur est partiellement vrai (correct dans un autre "
        "contexte) ; l'apprenant doit choisir la MEILLEURE option ou prédire un résultat précis.",
        650,
    ),
    Difficulte.MASTER: Niveau(
        "master (5 sur 5)",
        "cas limite ou piège subtil que seul un praticien expérimenté repère (effet de bord, comportement non "
        "intuitif, compromis entre deux bonnes pratiques) ; raisonnement en plusieurs étapes avec un piège à "
        "éviter ; les 4 choix sont plausibles pour un professionnel confirmé ; l'explication dit pourquoi le "
        "distracteur le plus tentant échoue précisément dans ce cas.",
        750,
    ),
}


def niveau(difficulte: str) -> Niveau:
    return GRILLE[Difficulte(difficulte)]


def grille_texte() -> str:
    """Les 5 niveaux en une liste : le LLM voit l'échelle complète et place la demande dessus."""
    return "\n".join(f"- {n.nom} : {n.consigne}" for n in GRILLE.values())
