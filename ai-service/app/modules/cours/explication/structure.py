"""Le script respecte-t-il la structure imposée par le prompt ? (ordre des types, rôles, longueur). Vérifié par du code, pas par le LLM."""

from app.modules.cours.explication.schemas import Locuteur, Mode, SegmentLLM, TypeSegment
from app.modules.cours.fidelite import compter_mots

#: Fenêtre acceptée autour de la cible (450 à 750 mots pour 3 à 5 minutes).
MOTS_MIN, MOTS_MAX = 380, 850

#: Ordre imposé à l'intérieur d'une notion : explication, analogie, exemple, puis démonstration.
RANG = {TypeSegment.EXPLICATION: 0, TypeSegment.ANALOGIE: 1, TypeSegment.EXEMPLE: 2, TypeSegment.DEMONSTRATION: 3}


def verifier_structure(segments: list[SegmentLLM], mode: Mode) -> list[str]:
    """Liste des écarts à la structure (vide si tout est conforme). Sert aussi de consignes de correction au LLM."""
    problemes: list[str] = []
    if segments[0].type != TypeSegment.INTRO:
        problemes.append("le premier segment doit être de type « intro »")
    if segments[-1].type != TypeSegment.RECAP:
        problemes.append("le dernier segment doit être de type « recap »")
    for type_ in (TypeSegment.ANALOGIE, TypeSegment.EXEMPLE, TypeSegment.DEMONSTRATION):
        if not any(s.type == type_ for s in segments):
            problemes.append(f"il manque au moins un segment de type « {type_.value} »")

    # Chaque question est suivie immédiatement de sa réponse (un segment « explication »).
    for i, s in enumerate(segments):
        if s.type == TypeSegment.QUESTION and (i + 1 >= len(segments) or segments[i + 1].type != TypeSegment.EXPLICATION):
            problemes.append(f"la question du segment {i + 1} doit être suivie de sa réponse (segment « explication »)")
            break

    # À l'intérieur d'une notion : explication d'abord, puis analogie, exemple, démonstration, sans retour en arrière.
    notion_courante, dernier_rang = None, -1
    for i, s in enumerate(segments):
        if s.type not in RANG:
            continue
        if s.type == TypeSegment.EXPLICATION and i > 0 and segments[i - 1].type == TypeSegment.QUESTION:
            continue  # réponse à une question : elle peut venir à n'importe quel moment de la notion
        if s.notion != notion_courante:
            notion_courante, dernier_rang = s.notion, -1
            if s.type != TypeSegment.EXPLICATION:
                problemes.append(f"la notion « {s.notion} » doit commencer par une « explication » (segment {i + 1})")
                break
        if RANG[s.type] < dernier_rang:
            problemes.append(f"ordre non respecté pour la notion « {s.notion} » (segment {i + 1}) : explication, analogie, exemple, démonstration")
            break
        dernier_rang = max(dernier_rang, RANG[s.type])

    etudiant = [s for s in segments if s.locuteur == Locuteur.ETUDIANT]
    if mode == Mode.MONOLOGUE and etudiant:
        problemes.append("en mode monologue, tous les segments doivent avoir le locuteur « prof »")
    if mode == Mode.DIALOGUE:
        if sum(1 for s in etudiant if s.type == TypeSegment.QUESTION) < 2:
            problemes.append("en mode dialogue, l'étudiant doit poser au moins deux questions (segments « question » avec le locuteur « etudiant »)")
        if any(s.type != TypeSegment.QUESTION for s in etudiant):
            problemes.append("l'étudiant ne dit que des segments de type « question »")

    mots = compter_mots(segments)
    if mots < MOTS_MIN:
        problemes.append(f"le script est trop court ({mots} mots) : vise entre 450 et 750 mots")
    elif mots > MOTS_MAX:
        problemes.append(f"le script est trop long ({mots} mots) : vise entre 450 et 750 mots")
    return problemes
