"""Outils des tests de l'explication orale : un petit cours en deux pages et des scripts valides (monologue, dialogue)."""

from app.modules.cours.explication.schemas import ExplicationLLM, Locuteur, Mode, PageCours, SegmentLLM, TypeSegment

PAGE1 = (
    "Une jointure interne (INNER JOIN) ne garde que les lignes qui ont une correspondance dans les deux tables. "
    "Une jointure externe gauche (LEFT JOIN) garde toutes les lignes de la table de gauche, même sans correspondance."
)
PAGE2 = (
    "Pour un LEFT JOIN, le filtre sur la table de droite se place dans la clause ON, pas dans le WHERE. "
    "La clé primaire identifie chaque ligne de façon unique. Une table peut contenir 3 clés étrangères au maximum dans cet exemple."
)
PAGES = [PageCours(numero=1, texte=PAGE1), PageCours(numero=2, texte=PAGE2)]
PAGES_JSON = [{"numero": 1, "texte": PAGE1}, {"numero": 2, "texte": PAGE2}]

# Extraits recopiés mot pour mot du cours (au moins 6 mots).
EXTRAIT_A = "ne garde que les lignes qui ont une correspondance dans les deux tables"  # page 1
EXTRAIT_B = "garde toutes les lignes de la table de gauche, même sans correspondance"  # page 1
EXTRAIT_C = "le filtre sur la table de droite se place dans la clause ON, pas dans le WHERE"  # page 2

PLAN = ["INNER JOIN", "LEFT JOIN"]
_REMPLISSAGE = "Regarde bien cette idée simple, prends ton temps et redis-la avec tes propres mots avant de continuer."


def texte(debut: str, repetitions: int = 2) -> str:
    """Un texte oral d'environ 40 mots : le début donné, puis du remplissage sans chiffre ni terme technique."""
    return debut + " " + " ".join([_REMPLISSAGE] * repetitions)


def seg(type_: str, debut: str, notion: str | None = None, extrait: str | None = None, page: int | None = None,
        locuteur: str = "prof", repetitions: int = 2) -> SegmentLLM:
    return SegmentLLM(type=TypeSegment(type_), locuteur=Locuteur(locuteur), texte=texte(debut, repetitions), notion=notion,
                      extrait_source=extrait, page=page)


def _notion_inner() -> list[SegmentLLM]:
    return [
        seg("explication", "Commençons par la jointure interne.", "INNER JOIN", EXTRAIT_A, 1),
        seg("analogie", "Imagine deux listes d'invités qu'on compare.", "INNER JOIN"),
        seg("exemple", "Prenons le cas des clients et de leurs commandes.", "INNER JOIN", EXTRAIT_A, 1),
        seg("demonstration", "Ligne par ligne, on cherche la correspondance.", "INNER JOIN", EXTRAIT_A, 1),
    ]


def _notion_left() -> list[SegmentLLM]:
    return [
        seg("explication", "Voyons maintenant la jointure externe gauche.", "LEFT JOIN", EXTRAIT_B, 1),
        seg("analogie", "Imagine un professeur qui garde toute sa liste d'élèves.", "LEFT JOIN"),
        seg("exemple", "Un client sans commande reste dans le résultat.", "LEFT JOIN", EXTRAIT_B, 1),
        seg("demonstration", "Pas à pas, on place le filtre dans la clause ON.", "LEFT JOIN", EXTRAIT_C, 2),
    ]


def script_monologue() -> ExplicationLLM:
    return ExplicationLLM(mode=Mode.MONOLOGUE, segments=[
        seg("intro", "Bonjour, aujourd'hui on apprend à relier des tables.", repetitions=1),
        *_notion_inner(), *_notion_left(),
        seg("question", "Petite question pour vérifier : que garde une jointure interne ?", repetitions=0),
        seg("explication", "Elle garde seulement les lignes qui ont un partenaire.", "INNER JOIN", EXTRAIT_A, 1),
        seg("recap", "Retiens bien ces idées : relier, garder, filtrer au bon endroit.", repetitions=1),
    ])


def script_dialogue() -> ExplicationLLM:
    interne = _notion_inner()
    return ExplicationLLM(mode=Mode.DIALOGUE, segments=[
        seg("intro", "Bonjour, aujourd'hui on apprend à relier des tables.", repetitions=1),
        interne[0],
        seg("question", "Mais pourquoi relier deux tables ?", locuteur="etudiant", repetitions=0),
        seg("explication", "Parce que les informations sont rangées à des endroits différents.", "INNER JOIN", EXTRAIT_A, 1),
        *interne[1:], *_notion_left(),
        seg("question", "Et si une ligne n'a pas de correspondance ?", locuteur="etudiant", repetitions=0),
        seg("explication", "Alors elle est gardée ou non selon le type de jointure.", "LEFT JOIN", EXTRAIT_B, 1),
        seg("question", "Petite question pour vérifier : que garde une jointure interne ?", repetitions=0),
        seg("explication", "Elle garde seulement les lignes qui ont un partenaire.", "INNER JOIN", EXTRAIT_A, 1),
        seg("recap", "Retiens bien ces idées : relier, garder, filtrer au bon endroit.", repetitions=1),
    ])
