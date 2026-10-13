"""Non supervisé : K-Means regroupe les apprenants selon leurs notes, leur temps passé, les cours suivis et leur
engagement (sujet, module User). Aucun LLM, aucune donnée personnelle : seulement des nombres.

Choix du nombre de groupes k : on calcule l'inertie (méthode du coude) et le score de silhouette pour k de 2 à 8,
puis on retient la meilleure silhouette entre K_MIN et K_MAX. k = 2 a souvent la meilleure silhouette mais ne
sépare que « ça va / ça ne va pas » : trop grossier pour le produit, qui veut au moins 3 profils.

Les groupes n'ont pas de nom pour K-Means (0, 1, 2…) : on les nomme d'après leur centre, avec des règles lisibles
(activité faible → décrocheur, puis selon la note moyenne).
"""

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.metrics import adjusted_rand_score, silhouette_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

GRAINE = 42
K_TESTES = range(2, 9)
K_MIN, K_MAX = 3, 6

# Entrées du K-Means (mêmes noms que les colonnes du jeu de données et que les contrats).
VARIABLES = [
    "noteMoyenne",  # quiz-service : note moyenne aux quiz (0 à 1)
    "progressionMoyenne",  # cours-service : progression moyenne dans les cours suivis (0 à 1)
    "tempsPasseMinutes",  # cours-service : temps total passé
    "nbCoursSuivis",  # cours-service : nombre de cours suivis
    "nbNotionsRatees",  # quiz-service, contrat 3 : notions ratées
    "joursActifs30j",  # user-service : jours avec au moins une connexion sur 30 jours
    "joursDepuisDerniereConnexion",  # user-service
]

# Seuils de nommage, appliqués au centre de chaque groupe.
ACTIVITE_FAIBLE_JOURS = 8  # moins de 8 jours actifs sur 30…
INACTIF_DEPUIS_JOURS = 20  # …ou plus de 20 jours sans connexion
NOTE_AVANCE, NOTE_REGULIER = 0.80, 0.60

DESCRIPTIONS = {
    "Avancé": "Très bonnes notes et régulier : prêt pour des cours de niveau supérieur.",
    "Régulier": "Avance à bon rythme avec des résultats corrects.",
    "Débutant en difficulté": "Actif, mais beaucoup d'erreurs aux quiz : a besoin de revoir les bases.",
    "Décrocheur": "Peu ou plus d'activité depuis longtemps : risque d'abandon.",
}


@dataclass
class Groupe:
    id: int
    nom: str
    description: str
    taille: int
    centre: dict[str, float]  # moyennes dans les unités d'origine (note 0-1, minutes, jours…)


@dataclass
class ModeleProfils:
    pipeline: Pipeline
    k: int
    groupes: list[Groupe]
    metriques: dict = field(default_factory=dict)

    def predire(self, vecteurs: pd.DataFrame) -> list[Groupe]:
        """Groupe de chaque apprenant (une ligne par apprenant, colonnes VARIABLES)."""
        ids = self.pipeline.predict(vecteurs[VARIABLES])
        par_id = {g.id: g for g in self.groupes}
        return [par_id[int(i)] for i in ids]


def nommer(centre: dict[str, float]) -> str:
    if centre["joursActifs30j"] < ACTIVITE_FAIBLE_JOURS or centre["joursDepuisDerniereConnexion"] > INACTIF_DEPUIS_JOURS:
        return "Décrocheur"
    if centre["noteMoyenne"] >= NOTE_AVANCE:
        return "Avancé"
    if centre["noteMoyenne"] >= NOTE_REGULIER:
        return "Régulier"
    return "Débutant en difficulté"


def _pipeline(k: int) -> Pipeline:
    # Variables d'échelles très différentes (minutes vs 0-1) : on standardise avant K-Means.
    return Pipeline([("echelle", StandardScaler()), ("kmeans", KMeans(n_clusters=k, n_init=10, random_state=GRAINE))])


def courbes(X: pd.DataFrame) -> list[dict]:
    """Inertie et silhouette pour chaque k testé : données des graphiques du coude et de la silhouette."""
    resultats = []
    for k in K_TESTES:
        p = _pipeline(k)
        etiquettes = p.fit_predict(X)
        resultats.append({
            "k": k,
            "inertie": round(float(p.named_steps["kmeans"].inertia_), 1),
            "silhouette": round(float(silhouette_score(p.named_steps["echelle"].transform(X), etiquettes)), 3),
        })
    return resultats


def entrainer(apprenants: pd.DataFrame) -> ModeleProfils:
    X = apprenants[VARIABLES]
    points = courbes(X)
    candidats = [p for p in points if K_MIN <= p["k"] <= K_MAX]
    k = max(candidats, key=lambda p: p["silhouette"])["k"]

    pipeline = _pipeline(k)
    etiquettes = pipeline.fit_predict(X)
    centres = pipeline.named_steps["echelle"].inverse_transform(pipeline.named_steps["kmeans"].cluster_centers_)

    groupes, vus = [], {}
    for i, c in enumerate(centres):
        centre = {v: round(float(x), 2) for v, x in zip(VARIABLES, c)}
        nom = nommer(centre)
        vus[nom] = vus.get(nom, 0) + 1
        groupes.append(Groupe(i, nom, DESCRIPTIONS[nom], int((etiquettes == i).sum()), centre))
    # Deux groupes de même nom (possible si k > 4) : on les numérote pour les distinguer à l'écran.
    for nom, n in vus.items():
        if n > 1:
            for j, g in enumerate([g for g in groupes if g.nom == nom], start=1):
                g.nom = f"{nom} {j}"

    metriques = {
        "k": k,
        "regleChoixK": f"meilleure silhouette pour k entre {K_MIN} et {K_MAX}",
        "silhouette": next(p["silhouette"] for p in points if p["k"] == k),
        "courbes": points,
        "nbApprenants": int(len(X)),
        "variables": VARIABLES,
    }
    # Validation possible seulement sur données simulées : accord avec les profils cachés (0 = hasard, 1 = parfait).
    if "profilCache" in apprenants.columns:
        metriques["accordProfilsCaches"] = round(float(adjusted_rand_score(apprenants["profilCache"], etiquettes)), 3)
        tableau = pd.crosstab(pd.Series(etiquettes, name="groupe"), apprenants["profilCache"])
        metriques["repartitionProfilsCaches"] = {
            next(g.nom for g in groupes if g.id == int(gid)): {str(p): int(n) for p, n in ligne.items()}
            for gid, ligne in tableau.iterrows()
        }
    if "echec" in apprenants.columns:
        metriques["tauxEchecParGroupe"] = {
            g.nom: round(float(apprenants.loc[etiquettes == g.id, "echec"].mean()), 2) for g in groupes
        }
    return ModeleProfils(pipeline, k, groupes, metriques)


if __name__ == "__main__":
    from app.modules.user.ml.donnees_simulees import generer

    modele = entrainer(generer()[0])
    print(f"k retenu : {modele.k} (silhouette {modele.metriques['silhouette']}, "
          f"accord avec les profils cachés ARI = {modele.metriques.get('accordProfilsCaches')})")
    print("\n k  inertie  silhouette")
    for p in modele.metriques["courbes"]:
        print(f"{p['k']:2d}  {p['inertie']:7.1f}  {p['silhouette']:.3f}{'  <- retenu' if p['k'] == modele.k else ''}")
    print()
    for g in modele.groupes:
        print(f"[{g.nom}] {g.taille} apprenants, échec {modele.metriques['tauxEchecParGroupe'][g.nom]:.0%} — {g.description}")
        print("   centre :", g.centre)
        print("   profils cachés :", modele.metriques["repartitionProfilsCaches"][g.nom])
    np.set_printoptions(precision=2)
