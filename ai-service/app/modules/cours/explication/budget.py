"""Budget de jetons de l'explication orale : taille de la sortie demandée, et pages utiles quand le secours est limité.

Le fournisseur de secours (Groq) limite les jetons par minute, entrée + sortie réservée comprises. On ne réserve donc
pas une valeur fixe énorme : la sortie est calculée à partir de la durée demandée.
"""

import math

from app.core.llm_client import CARACTERES_PAR_JETON, OpenAILLMClient, estimer_tokens
from app.modules.cours.explication.schemas import PageCours
from app.modules.cours.explication.selection import Selection, selectionner_pages

MOTS_PAR_MINUTE = 150
#: Jetons par mot français (prudent : le français est plus verbeux que l'anglais en jetons).
TOKENS_PAR_MOT = 1.6
#: Par segment du JSON : clés, type, locuteur, notion, page (~45 jetons) et extrait source recopié du cours (~35 en moyenne).
TOKENS_STRUCTURE_SEGMENT = 45
TOKENS_EXTRAIT_SEGMENT = 35
#: Marge sur le total, pour les mots en trop que le modèle ajoute souvent.
MARGE_SORTIE = 1.10
MAX_TOKENS_MIN, MAX_TOKENS_MAX = 1200, 8000
#: Part de la limite par minute gardée en réserve (estimation approximative des jetons).
MARGE_LIMITE = 0.20
#: Texte minimal (en jetons) qu'il faut pouvoir envoyer pour que l'explication ait un sens.
TEXTE_MIN_TOKENS = 300


def bornes_mots(duree_minutes: int) -> tuple[int, int]:
    """Fenêtre de mots demandée au modèle : de 75 % à 125 % de 150 mots par minute."""
    cible = duree_minutes * MOTS_PAR_MINUTE
    return round(cible * 0.75), round(cible * 1.25)


def nombre_segments(nb_notions: int) -> int:
    """Segments attendus : intro et récapitulatif, puis explication, analogie, exemple et démonstration ou question par notion."""
    return 2 + 4 * nb_notions


def calculer_max_tokens(mots_max: int, nb_notions: int) -> int:
    """Jetons de sortie à demander :

        (mots_max x 1,6 + segments x (45 + 35)) x 1,10, arrondi à 50, entre 1 200 et 8 000

    avec segments = 2 + 4 x notions (3 si le plan est vide, 5 au plus). Exemple : 2 minutes (375 mots max), 3 notions, soit
    14 segments : (600 + 1 120) x 1,10 = 1 892, arrondi à 1 900 (au lieu de 6 000 fixes).
    """
    brut = mots_max * TOKENS_PAR_MOT + nombre_segments(nb_notions) * (TOKENS_STRUCTURE_SEGMENT + TOKENS_EXTRAIT_SEGMENT)
    arrondi = math.ceil(brut * MARGE_SORTIE / 50) * 50
    return max(MAX_TOKENS_MIN, min(MAX_TOKENS_MAX, arrondi))


def texte_du_cours(pages: list[PageCours]) -> str:
    return "\n\n".join(f"[Page {p.numero}]\n{p.texte}" for p in pages)


def selection_pour_secours(
    pages: list[PageCours], notions: list[str], secours: OpenAILLMClient, *, tokens_prompt: int, max_tokens: int
) -> Selection:
    """Pages utiles (celles qui parlent des notions du plan) tronquées pour tenir dans la limite du secours avec 20 % de marge.

    Total visé : jetons du prompt + jetons du texte + sortie réservée (max_tokens + marge de réflexion) <= 80 % de la limite.
    Lève `ValueError` s'il ne reste pas de place pour au moins 300 jetons de cours (durée trop longue pour ce fournisseur).
    """
    limite = secours.limite_tokens_minute or 0
    disponible = int(limite * (1 - MARGE_LIMITE)) - tokens_prompt - secours.reserve_sortie(max_tokens)
    if disponible < TEXTE_MIN_TOKENS:
        raise ValueError("pas de place pour le texte du cours")
    max_chars = int(disponible * CARACTERES_PAR_JETON)
    selection = selectionner_pages(pages, notions, max_chars)
    # Les intitulés « [Page N] » s'ajoutent au texte : on resserre jusqu'à ce que le total tienne.
    for _ in range(4):
        depasse = estimer_tokens(texte_du_cours(selection.pages)) - disponible
        if depasse <= 0:
            break
        max_chars -= int(depasse * CARACTERES_PAR_JETON) + 1
        selection = selectionner_pages(pages, notions, max_chars)
    return Selection(selection.pages, True, selection.pages_gardees, selection.pages_totales)
