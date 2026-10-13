"""Entraîne les trois modèles du module User, les enregistre et les recharge pour les endpoints.

- `entrainer_tout()` : K-Means (profils), Random Forest (risque) et recommandation, puis `metriques.json`
  (scores, date, source des données, versions des bibliothèques) : c'est ce qu'affiche le tableau de bord admin.
- `obtenir()` : modèles en mémoire ; à défaut, ceux du disque ; à défaut, un entraînement (premier démarrage).
- `reentrainer()` : nouvel entraînement à la demande de l'admin, qui remplace les modèles en mémoire.

Les fichiers .joblib ne sont pas versionnés (binaires, régénérés en une commande). joblib s'appuie sur pickle : on
ne charge que des fichiers produits ici, jamais un fichier venu de l'extérieur.

Utilisation : `cd ai-service && .venv/Scripts/python -m app.modules.user.ml.entrainement`
"""

import json
import platform
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy
import pandas
import sklearn

from app.modules.user.ml import clustering, recommandation, risque
from app.modules.user.ml.clustering import ModeleProfils
from app.modules.user.ml.donnees_simulees import NB_APPRENANTS, generer
from app.modules.user.ml.recommandation import ModeleRecommandation
from app.modules.user.ml.risque import ModeleRisque

DOSSIER = Path(__file__).parent / "modeles"
FICHIERS = {"profils": "profils.joblib", "risque": "risque.joblib", "recommandation": "recommandation.joblib"}
METRIQUES = "metriques.json"
SOURCE_SIMULEE = f"Données simulées ({NB_APPRENANTS} apprenants fictifs, graine fixe) : quiz-service et cours-service " \
                 "ne fournissent pas encore les résultats ni les inscriptions."


def versions() -> dict[str, str]:
    return {"python": platform.python_version(), "scikit-learn": sklearn.__version__, "pandas": pandas.__version__,
            "numpy": numpy.__version__}


@dataclass
class Modeles:
    profils: ModeleProfils
    risque: ModeleRisque
    recommandation: ModeleRecommandation
    metriques: dict


def entrainer_tout(dossier: Path = DOSSIER) -> Modeles:
    debut = time.perf_counter()
    apprenants, inscriptions, cours = generer()
    profils = clustering.entrainer(apprenants)
    modele_risque = risque.entrainer(apprenants)
    modele_reco = recommandation.entrainer(apprenants, inscriptions, cours)
    metriques = {
        "dateEntrainement": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "dureeSecondes": round(time.perf_counter() - debut, 1),
        "source": SOURCE_SIMULEE,
        "donneesSimulees": True,
        "versions": versions(),
        "profils": profils.metriques | {"groupes": [
            {"id": g.id, "nom": g.nom, "description": g.description, "taille": g.taille, "centre": g.centre}
            for g in profils.groupes
        ]},
        "risque": modele_risque.metriques,
        "recommandation": modele_reco.metriques,
    }
    modeles = Modeles(profils, modele_risque, modele_reco, metriques)
    enregistrer(modeles, dossier)
    return modeles


def enregistrer(modeles: Modeles, dossier: Path = DOSSIER) -> None:
    dossier.mkdir(parents=True, exist_ok=True)
    for nom, fichier in FICHIERS.items():
        joblib.dump(getattr(modeles, nom), dossier / fichier, compress=3)
    (dossier / METRIQUES).write_text(json.dumps(modeles.metriques, ensure_ascii=False, indent=2), encoding="utf-8")


def charger(dossier: Path = DOSSIER) -> Modeles | None:
    """Modèles enregistrés, ou None s'il en manque ou s'ils viennent d'autres versions (Python, bibliothèques)."""
    chemins = {nom: dossier / f for nom, f in FICHIERS.items()}
    if not (dossier / METRIQUES).exists() or not all(c.exists() for c in chemins.values()):
        return None
    metriques = json.loads((dossier / METRIQUES).read_text(encoding="utf-8"))
    enregistrees, actuelles = metriques.get("versions", {}), versions()
    # Un modèle scikit-learn ne se recharge de façon fiable qu'avec les mêmes versions (ex. : PC en Python 3.13,
    # image Docker en 3.12) : sinon on réentraîne.
    if enregistrees.get("python", "").rsplit(".", 1)[0] != actuelles["python"].rsplit(".", 1)[0] or any(
            enregistrees.get(b) != actuelles[b] for b in ("scikit-learn", "numpy")):
        return None
    return Modeles(**{nom: joblib.load(c) for nom, c in chemins.items()}, metriques=metriques)


_cache: Modeles | None = None
_verrou = threading.Lock()


def obtenir(dossier: Path = DOSSIER) -> Modeles:
    global _cache
    with _verrou:
        if _cache is None:
            _cache = charger(dossier) or entrainer_tout(dossier)
        return _cache


def reentrainer(dossier: Path = DOSSIER) -> Modeles:
    global _cache
    with _verrou:
        _cache = entrainer_tout(dossier)
        return _cache


def vider_cache() -> None:
    global _cache
    with _verrou:
        _cache = None


if __name__ == "__main__":
    m = entrainer_tout()
    met = m.metriques
    print(f"Entraînement terminé en {met['dureeSecondes']} s ({met['dateEntrainement']}), modèles dans {DOSSIER}")
    print(f"- Profils (K-Means)       : k = {m.profils.k}, silhouette {met['profils']['silhouette']}, "
          f"groupes {[g.nom for g in m.profils.groupes]}")
    t = met["risque"]["test"]
    print(f"- Risque (Random Forest)  : test accuracy {t['accuracy']}, rappel {t['rappel']}, F1 {t['f1']}, AUC {t['auc']}")
    h = met["recommandation"]["evaluation"]["hybride"]
    print(f"- Recommandation (hybride): réussite@3 {h['reussiteA3']}, réussite@5 {h['reussiteA5']}")
    for f in [*FICHIERS.values(), METRIQUES]:
        print(f"  {f:24s} {(DOSSIER / f).stat().st_size / 1024:8.1f} Ko")
