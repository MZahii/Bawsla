"""Jeu de données SIMULÉ pour entraîner les modèles du module User (à signaler comme tel dans le rapport).

Pourquoi simulé : quiz-service (notes, notions ratées) et cours-service (inscriptions, temps, progression) ne
fournissent pas encore ces données. Les colonnes reprennent les noms des contrats (CONTRATS_API.md) pour que les
vraies données les remplacent sans changer les modèles :
- quiz-service, contrat 3 `notions-ratees` : nbErreurs, tauxReussite (par notion), agrégés ici par apprenant ;
- quiz-service, contrat à proposer `resultats` : note des quiz (0 à 1) ;
- cours-service, contrat à proposer `inscriptions` : cours suivis, progression, temps passé, cours terminés ;
- user-service (déjà disponible) : profil et domaines du questionnaire d'accueil, dernière connexion, ancienneté.

Méthode : chaque apprenant est tiré d'un des 4 profils cachés ci-dessous (« débutant en difficulté », « régulier »,
« avancé », « décrocheur »), avec du bruit. Le K-Means devra retrouver ces groupes sans les connaître.
L'échec à l'examen final suit une loi logistique de ses chiffres, plus du hasard : le Random Forest ne peut donc
pas être parfait, ce qui est réaliste. Graine fixe : mêmes données à chaque exécution.

Aucune donnée personnelle : les apprenants sont des identifiants « A001 »… sans nom ni email.

Utilisation : `cd ai-service && .venv/Scripts/python -m app.modules.user.ml.donnees_simulees`
"""

from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

GRAINE = 42
NB_APPRENANTS = 200
DOSSIER = Path(__file__).parent / "donnees"

# Catalogue simulé : catégories et niveaux identiques à cours-service (Cours.categorie, Niveau).
CATALOGUE = [
    (1, "Introduction à Python", "Data", "DEBUTANT"),
    (2, "Pandas et visualisation", "Data", "INTERMEDIAIRE"),
    (3, "Machine Learning appliqué", "Data", "AVANCE"),
    (4, "HTML et CSS", "Web", "DEBUTANT"),
    (5, "Angular moderne", "Web", "INTERMEDIAIRE"),
    (6, "Architecture front-end", "Web", "AVANCE"),
    (7, "Bases de données SQL", "Back-end", "DEBUTANT"),
    (8, "Spring Boot", "Back-end", "INTERMEDIAIRE"),
    (9, "Microservices", "Back-end", "AVANCE"),
    (10, "Sécurité web (OWASP)", "Sécurité", "DEBUTANT"),
    (11, "Tests d'intrusion", "Sécurité", "AVANCE"),
    (12, "Git et GitHub", "Outils", "DEBUTANT"),
    (13, "Docker", "Outils", "INTERMEDIAIRE"),
    (14, "Algorithmique", "Fondamentaux", "DEBUTANT"),
    (15, "Structures de données", "Fondamentaux", "INTERMEDIAIRE"),
]
CATEGORIES = sorted({c[2] for c in CATALOGUE})
NIVEAUX = ["DEBUTANT", "INTERMEDIAIRE", "AVANCE"]
PROFILS_ACCUEIL = ["ETUDIANT", "PRO", "RECONVERSION", "AUTRE"]  # questionnaire d'accueil (user-service)


@dataclass(frozen=True)
class ProfilCache:
    """Paramètres d'un groupe d'apprenants : moyenne et écart-type de chaque grandeur."""

    nom: str
    part: float  # proportion des apprenants
    note: tuple[float, float]  # note moyenne aux quiz, 0 à 1
    engagement: tuple[float, float]  # 0 à 1 : temps, régularité, progression
    niveau_max: int  # niveau de cours le plus élevé qu'il suit volontiers (0, 1, 2)
    nb_cours: tuple[int, int]  # bornes du nombre de cours suivis


PROFILS = [
    ProfilCache("debutant_en_difficulte", 0.30, (0.42, 0.10), (0.55, 0.15), 0, (1, 3)),
    ProfilCache("regulier", 0.35, (0.66, 0.08), (0.75, 0.10), 1, (2, 5)),
    ProfilCache("avance", 0.20, (0.85, 0.06), (0.80, 0.10), 2, (3, 6)),
    ProfilCache("decrocheur", 0.15, (0.50, 0.15), (0.18, 0.08), 1, (1, 3)),
]


def _borner(x: float, bas: float = 0.0, haut: float = 1.0) -> float:
    return float(min(haut, max(bas, x)))


def generer(nb: int = NB_APPRENANTS, graine: int = GRAINE) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Renvoie (apprenants, inscriptions, cours). Mêmes résultats pour une même graine."""
    rng = np.random.default_rng(graine)
    cours = pd.DataFrame(CATALOGUE, columns=["coursId", "titre", "categorie", "niveau"])
    niveau_idx = {n: i for i, n in enumerate(NIVEAUX)}

    profils = rng.choice(len(PROFILS), size=nb, p=[p.part for p in PROFILS])
    lignes, inscriptions = [], []
    for i, k in enumerate(profils):
        p = PROFILS[k]
        aid = f"A{i + 1:03d}"
        note = _borner(rng.normal(*p.note), 0.05, 1.0)
        engagement = _borner(rng.normal(*p.engagement), 0.02, 1.0)

        # Domaines d'intérêt : 1 ou 2 catégories, qui orientent les cours choisis (base du filtrage collaboratif).
        domaines = list(rng.choice(CATEGORIES, size=rng.integers(1, 3), replace=False))
        nb_cours = int(rng.integers(p.nb_cours[0], p.nb_cours[1] + 1))
        poids = np.array([
            (3.0 if c[2] in domaines else 0.4) * (1.0 if niveau_idx[c[3]] <= p.niveau_max else 0.15)
            for c in CATALOGUE
        ])
        choisis = rng.choice(cours["coursId"], size=nb_cours, replace=False, p=poids / poids.sum())

        temps_total, progressions, termines = 0, [], 0
        for cid in choisis:
            niveau = niveau_idx[cours.loc[cours.coursId == cid, "niveau"].item()]
            progression = _borner(rng.normal(engagement * (1.05 - 0.15 * niveau), 0.15))
            temps = int(max(5, rng.normal(progression * 300 * (1 + 0.4 * niveau), 40)))
            termine = progression >= 0.95
            note_cours = _borner(rng.normal(note - 0.06 * niveau, 0.08))
            inscriptions.append({
                "apprenant": aid, "coursId": int(cid), "progression": round(progression, 2),
                "tempsPasseMinutes": temps, "termine": termine, "note": round(note_cours, 2),
            })
            temps_total += temps
            progressions.append(progression)
            termines += int(termine)

        nb_quiz = int(max(0, round(rng.normal(2 + 8 * engagement, 1.5))))
        # Notions ratées (contrat 3) : plus nombreuses quand la note est basse.
        nb_notions = int(max(0, round(rng.normal((1 - note) * 10, 1.5)))) if nb_quiz else 0
        nb_erreurs = int(nb_notions * max(1.0, rng.normal(2.5 - note, 0.5)))
        jours_actifs = int(_borner(round(rng.normal(engagement * 22, 3)), 0, 30))
        depuis = int(max(0, round(rng.exponential(1 + (1 - engagement) * 22))))

        # Échec à l'examen final : loi logistique des chiffres + hasard (jamais parfaitement prévisible).
        # -1.3 : constante calée pour environ un tiers d'échecs (peu chez les avancés, beaucoup chez les décrocheurs).
        score = (-1.3 - 5.0 * (note - 0.6) - 3.0 * (engagement - 0.5) + 0.12 * nb_notions + 0.04 * depuis
                 + rng.normal(0, 0.8))
        echec = bool(rng.random() < 1 / (1 + np.exp(-score)))

        lignes.append({
            "apprenant": aid,
            "profilAccueil": str(rng.choice(PROFILS_ACCUEIL)),
            # Domaines déclarés au questionnaire d'accueil (user-service, parcours.domaines), séparés par « | ».
            "domaines": "|".join(sorted(domaines)),
            "ancienneteJours": int(rng.integers(7, 365)),
            "nbCoursSuivis": nb_cours,
            "nbCoursTermines": termines,
            "progressionMoyenne": round(float(np.mean(progressions)), 2),
            "tempsPasseMinutes": temps_total,
            "nbQuiz": nb_quiz,
            "noteMoyenne": round(note, 2) if nb_quiz else 0.0,
            "tauxReussite": round(_borner(rng.normal(note, 0.05)), 2) if nb_quiz else 0.0,
            "nbNotionsRatees": nb_notions,
            "nbErreurs": nb_erreurs,
            "joursActifs30j": jours_actifs,
            "joursDepuisDerniereConnexion": depuis,
            "echec": echec,
            # Vérité terrain, utilisée seulement pour évaluer le K-Means (jamais comme entrée d'un modèle).
            "profilCache": p.nom,
        })
    return pd.DataFrame(lignes), pd.DataFrame(inscriptions), cours


def enregistrer(dossier: Path = DOSSIER) -> dict[str, Path]:
    apprenants, inscriptions, cours = generer()
    dossier.mkdir(parents=True, exist_ok=True)
    chemins = {
        "apprenants": dossier / "apprenants.csv",
        "inscriptions": dossier / "inscriptions.csv",
        "cours": dossier / "cours.csv",
    }
    apprenants.to_csv(chemins["apprenants"], index=False)
    inscriptions.to_csv(chemins["inscriptions"], index=False)
    cours.to_csv(chemins["cours"], index=False)
    return chemins


if __name__ == "__main__":
    for nom, chemin in enregistrer().items():
        print(f"{nom:13s} -> {chemin}")
