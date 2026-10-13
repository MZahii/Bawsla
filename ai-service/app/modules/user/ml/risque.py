"""Supervisé : un Random Forest prédit le risque d'échec d'un apprenant à partir de ses résultats aux quiz et de son
engagement (sujet, module User). IA discriminative, sans LLM, sur des nombres uniquement.

Évaluation honnête :
- découpage 80 / 20 stratifié : le jeu de test n'est jamais vu pendant l'entraînement ni le réglage ;
- réglage des hyperparamètres par validation croisée à 5 plis sur la partie entraînement (F1 sur la classe « échec ») ;
- comparaison avec deux références : la classe majoritaire (DummyClassifier) et une régression logistique ;
- seuil de décision choisi sur des prédictions hors pli (jamais sur le test) en maximisant le F2, qui compte un
  apprenant à risque raté deux fois plus qu'une fausse alerte : c'est un outil d'alerte pour le formateur ;
- importance des variables : par permutation sur le test (AUC), gardée dans les métriques ; elle reste proche de 0
  car plusieurs variables vont ensemble (note et bonnes réponses, temps et cours suivis) : en mélanger une ne gêne
  pas le modèle, qui s'appuie sur sa « jumelle ». Les raisons affichées suivent donc l'importance propre à la forêt
  (réduction d'impureté), stable d'un entraînement à l'autre.

Biais et données insuffisantes (risque principal du module) : classes pondérées (`class_weight="balanced"`),
rappel de la classe « échec » suivi à part, et variable sans lien avec l'échec (ancienneté) écartée.
"""

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score, fbeta_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import GridSearchCV, StratifiedKFold, cross_val_predict, cross_validate, train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

GRAINE = 42
PART_TEST = 0.2
GRILLE = {"max_depth": [3, 5, 8, None], "min_samples_leaf": [1, 3, 5]}
SEUILS_TESTES = np.round(np.arange(0.30, 0.61, 0.05), 2)

# Niveaux affichés au formateur : « moyen » dès le seuil de décision (le modèle prédit l'échec), « élevé » à 0,6.
SEUIL_ELEVE = 0.60

VARIABLES = [
    "noteMoyenne", "tauxReussite", "nbQuiz", "nbNotionsRatees", "nbErreurs",  # quiz-service
    "progressionMoyenne", "tempsPasseMinutes", "nbCoursSuivis", "nbCoursTermines",  # cours-service
    "joursActifs30j", "joursDepuisDerniereConnexion",  # user-service
]

# Variables qui disent la même chose : une seule raison par groupe dans l'explication.
REDONDANTES = [{"noteMoyenne", "tauxReussite"}, {"nbNotionsRatees", "nbErreurs"}, {"nbCoursSuivis", "nbCoursTermines"}]

# Explication d'un risque : (sens du risque, message). +1 = plus c'est grand, plus c'est risqué ; -1 = l'inverse.
FACTEURS = {
    "noteMoyenne": (-1, lambda v: f"Note moyenne basse aux quiz ({v:.0%})"),
    "tauxReussite": (-1, lambda v: f"Peu de bonnes réponses aux quiz ({v:.0%})"),
    "nbQuiz": (-1, lambda v: f"Peu de quiz passés ({v:.0f})"),
    "nbNotionsRatees": (+1, lambda v: f"Beaucoup de notions ratées ({v:.0f})"),
    "nbErreurs": (+1, lambda v: f"Beaucoup d'erreurs aux quiz ({v:.0f})"),
    "progressionMoyenne": (-1, lambda v: f"Faible progression dans ses cours ({v:.0%})"),
    "tempsPasseMinutes": (-1, lambda v: f"Peu de temps passé sur les cours ({v / 60:.0f} h)"),
    "nbCoursSuivis": (-1, lambda v: f"Peu de cours suivis ({v:.0f})"),
    "nbCoursTermines": (-1, lambda v: f"Aucun ou peu de cours terminés ({v:.0f})"),
    "joursActifs30j": (-1, lambda v: f"Peu de jours d'activité ce mois-ci ({v:.0f} sur 30)"),
    "joursDepuisDerniereConnexion": (+1, lambda v: f"Pas connecté depuis {v:.0f} jours"),
}


@dataclass
class Risque:
    probabilite: float
    niveau: str  # "faible", "moyen", "eleve"
    facteurs: list[str]


@dataclass
class ModeleRisque:
    modele: RandomForestClassifier
    seuil: float
    importances: dict[str, float]  # importance propre à la forêt, triée : ordre des raisons affichées
    # Par variable, la limite du quart le moins bon des apprenants qui réussissent (1er ou 3e quartile selon le sens).
    reperes: dict[str, float]
    metriques: dict = field(default_factory=dict)

    def evaluer(self, vecteurs: pd.DataFrame, nb_facteurs: int = 3) -> list[Risque]:
        probas = self.modele.predict_proba(vecteurs[VARIABLES])[:, 1]
        resultats = []
        for p, (_, ligne) in zip(probas, vecteurs[VARIABLES].iterrows()):
            n = niveau(float(p), self.seuil)
            # Risque faible : aucune raison listée (afficher des « problèmes » à un apprenant qui va bien tromperait).
            resultats.append(Risque(round(float(p), 2), n, self.facteurs(ligne, nb_facteurs) if n != "faible" else []))
        return resultats

    def facteurs(self, ligne: pd.Series, nb: int = 3) -> list[str]:
        """Les variables les plus importantes pour lesquelles l'apprenant fait moins bien que 75 % des apprenants qui
        réussissent : des raisons concrètes, lisibles par le formateur, sans doublon."""
        raisons, deja = [], set()
        for var in self.importances:
            sens, message = FACTEURS[var]
            if var in deja or (ligne[var] - self.reperes[var]) * sens <= 0:
                continue
            raisons.append(message(float(ligne[var])))
            deja |= next((g for g in REDONDANTES if var in g), {var})
            if len(raisons) == nb:
                break
        return raisons


def niveau(probabilite: float, seuil: float) -> str:
    if probabilite >= max(SEUIL_ELEVE, seuil):
        return "eleve"
    if probabilite >= seuil:
        return "moyen"
    return "faible"


def _trier(valeurs) -> dict[str, float]:
    return dict(sorted(((v, round(float(i), 3)) for v, i in zip(VARIABLES, valeurs)), key=lambda t: t[1], reverse=True))


def _scores(y_vrai, y_predit, probas) -> dict:
    return {
        "accuracy": round(float(accuracy_score(y_vrai, y_predit)), 3),
        "precision": round(float(precision_score(y_vrai, y_predit, zero_division=0)), 3),
        "rappel": round(float(recall_score(y_vrai, y_predit, zero_division=0)), 3),
        "f1": round(float(f1_score(y_vrai, y_predit, zero_division=0)), 3),
        "auc": round(float(roc_auc_score(y_vrai, probas)), 3),
        # [[réussites bien prédites, fausses alertes], [échecs ratés, échecs repérés]]
        "matriceConfusion": confusion_matrix(y_vrai, y_predit, labels=[0, 1]).tolist(),
    }


def entrainer(apprenants: pd.DataFrame) -> ModeleRisque:
    X, y = apprenants[VARIABLES], apprenants["echec"].astype(int)
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=PART_TEST, stratify=y, random_state=GRAINE)
    plis = StratifiedKFold(n_splits=5, shuffle=True, random_state=GRAINE)

    recherche = GridSearchCV(
        RandomForestClassifier(n_estimators=300, class_weight="balanced", random_state=GRAINE),
        GRILLE, cv=plis, scoring="f1", n_jobs=-1,
    ).fit(X_train, y_train)
    foret = recherche.best_estimator_

    # Seuil : meilleur F2 sur des probabilités hors pli de l'entraînement (le test reste intact).
    probas_hors_pli = cross_val_predict(foret, X_train, y_train, cv=plis, method="predict_proba")[:, 1]
    seuil = float(max(SEUILS_TESTES, key=lambda s: fbeta_score(y_train, probas_hors_pli >= s, beta=2)))

    # Comparaison avec les références, par validation croisée puis sur le test.
    references = {
        "classeMajoritaire": DummyClassifier(strategy="most_frequent"),
        "regressionLogistique": make_pipeline(StandardScaler(), LogisticRegression(class_weight="balanced", max_iter=1000)),
        "randomForest": foret,
    }
    comparaison = {}
    for nom, m in references.items():
        cv = cross_validate(m, X_train, y_train, cv=plis, scoring=["accuracy", "f1", "roc_auc"])
        m.fit(X_train, y_train)
        probas = m.predict_proba(X_test)[:, 1]
        comparaison[nom] = {
            "validationCroisee": {
                "accuracy": round(float(cv["test_accuracy"].mean()), 3),
                "f1": round(float(cv["test_f1"].mean()), 3),
                "auc": round(float(cv["test_roc_auc"].mean()), 3),
            },
            "test": _scores(y_test, (probas >= 0.5).astype(int), probas),
        }

    probas_test = foret.predict_proba(X_test)[:, 1]
    permutation = permutation_importance(foret, X_test, y_test, n_repeats=30, random_state=GRAINE, scoring="roc_auc")
    importances = _trier(foret.feature_importances_)
    reussites = X_train[y_train == 0]
    reperes = {v: float(reussites[v].quantile(0.25 if FACTEURS[v][0] < 0 else 0.75)) for v in VARIABLES}

    metriques = {
        "nbApprenants": int(len(X)),
        "nbEntrainement": int(len(X_train)),
        "nbTest": int(len(X_test)),
        "tauxEchec": round(float(y.mean()), 2),
        "hyperparametres": recherche.best_params_,
        "seuilDecision": seuil,
        "test": _scores(y_test, (probas_test >= seuil).astype(int), probas_test),
        "testSeuil05": comparaison["randomForest"]["test"],
        "comparaison": comparaison,
        "importances": importances,
        "importancesPermutation": _trier(permutation.importances_mean),
        "variables": VARIABLES,
        "niveaux": {"moyen": seuil, "eleve": max(SEUIL_ELEVE, seuil)},
    }
    return ModeleRisque(foret, seuil, importances, reperes, metriques)


if __name__ == "__main__":
    from app.modules.user.ml.donnees_simulees import generer

    apprenants = generer()[0]
    m = entrainer(apprenants)
    met = m.metriques
    print(f"Entraînement {met['nbEntrainement']} / test {met['nbTest']} ; hyperparamètres {met['hyperparametres']}")
    print(f"Seuil de décision retenu : {m.seuil}\n")
    print(f"{'modèle':22s} {'CV acc':>7s} {'CV F1':>6s} {'CV AUC':>7s} | {'test acc':>8s} {'rappel':>6s} {'F1':>5s}")
    for nom, r in met["comparaison"].items():
        cv, t = r["validationCroisee"], r["test"]
        print(f"{nom:22s} {cv['accuracy']:7.3f} {cv['f1']:6.3f} {cv['auc']:7.3f} | {t['accuracy']:8.3f} {t['rappel']:6.3f} {t['f1']:5.3f}")
    t = met["test"]
    print(f"\nRandom Forest au seuil {m.seuil} (test) : accuracy {t['accuracy']}, précision {t['precision']}, "
          f"rappel {t['rappel']}, F1 {t['f1']}, AUC {t['auc']}")
    print("Matrice de confusion [[réussite OK, fausse alerte], [échec raté, échec repéré]] :", t["matriceConfusion"])
    print("\nImportance des variables (forêt) :", met["importances"])
    print("Importance par permutation (test, AUC) :", met["importancesPermutation"])
    print("\nExemples :")
    for _, a in apprenants.sample(6, random_state=3).iterrows():
        r = m.evaluer(a.to_frame().T)[0]
        print(f"  {a['apprenant']} ({a['profilCache']}, échec réel {a['echec']}) -> {r.niveau} {r.probabilite:.0%} : {r.facteurs}")
