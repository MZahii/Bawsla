"""Recommandation de cours par filtrage collaboratif : « les apprenants au profil proche du tien ont suivi tel cours »
(sujet, module User). Sans LLM : uniquement la matrice apprenants × cours suivis.

Méthode hybride, choisie après comparaison (voir `evaluer`) :
- collaboratif (poids 0,5) : similarité cosinus entre l'historique de l'apprenant et celui des autres, puis cours
  suivis par ses K_VOISINS plus proches voisins ;
- popularité (0,2) : stabilise les recommandations quand l'historique est court ;
- domaines déclarés au questionnaire d'accueil (0,3) : les intérêts de l'apprenant (user-service, parcours).
Entrée supplémentaire du sujet, les notions ratées (quiz-service, contrat 3, champ coursId) : un cours plus facile de
la même catégorie qu'un cours où l'apprenant a raté des notions reçoit un bonus, « pour revoir les bases ».
Démarrage à froid (aucun cours suivi) : popularité et domaines seulement, en commençant par les niveaux accessibles.

Évaluation : pour chaque apprenant qui suit au moins 2 cours, on cache un de ses cours et on regarde s'il figure
dans les n recommandations (taux de réussite @n), sur 3 tirages ; on mesure aussi la couverture du catalogue.
"""

from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.metrics.pairwise import cosine_similarity

K_VOISINS = 20
POIDS = {"collaboratif": 0.5, "popularite": 0.2, "domaines": 0.3}
BONUS_REVISION = 0.4
PENALITE_NIVEAU_DEBUT = 0.10  # par niveau au-dessus de DEBUTANT, sans historique (reste < poids des domaines)
NIVEAUX = ["DEBUTANT", "INTERMEDIAIRE", "AVANCE"]
GRAINES_EVALUATION = (42, 7, 123)


@dataclass
class Recommandation:
    coursId: int
    titre: str
    score: float
    raison: str


@dataclass
class ModeleRecommandation:
    matrice: pd.DataFrame  # apprenant × coursId, 1 si suivi
    cours: pd.DataFrame  # index coursId ; colonnes titre, categorie, niveau
    popularite: pd.Series  # 0 à 1
    metriques: dict = field(default_factory=dict)

    def recommander(
        self,
        cours_suivis: list[int],
        domaines: list[str] | None = None,
        cours_en_difficulte: list[int] | None = None,
        n: int = 3,
        catalogue: list[int] | None = None,
        exclure: str | None = None,
        cours_disponibles: pd.DataFrame | None = None,
    ) -> list[Recommandation]:
        """n cours à recommander, jamais un cours déjà suivi.

        catalogue : identifiants des cours recommandables parmi ceux du modèle ; tous par défaut.
        exclure : identifiant de l'apprenant dans la matrice, pour ne pas se compter soi-même parmi ses voisins.
        cours_disponibles : vrai catalogue (index coursId ; titre, categorie, niveau), par exemple les cours publiés
            de cours-service. Un cours inconnu du modèle (aucune inscription vue à l'entraînement) n'a ni score
            collaboratif ni popularité : il est classé sur les domaines, le niveau et les notions ratées.
        """
        cours = self.cours if cours_disponibles is None else cours_disponibles
        domaines = set(domaines or [])
        suivis = set(cours_suivis)
        candidats = [c for c in (catalogue or cours.index) if c in cours.index and c not in suivis]
        if not candidats:
            return []

        # Un cours du vrai catalogue n'a d'historique que s'il est vraiment connu du modèle : même identifiant ET même
        # titre. Sinon (modèle entraîné sur le catalogue simulé, ids 1 à 15), deux cours différents partageraient
        # un numéro, et le vrai cours hériterait à tort de la popularité et des voisins du cours simulé.
        connus = [c for c in cours.index
                  if c in self.cours.index and str(self.cours.loc[c, "titre"]) == str(cours.loc[c, "titre"])]
        collaboratif, nb_voisins = self._collaboratif({c for c in suivis if c in connus}, exclure)
        collaboratif = collaboratif.reindex(connus).reindex(cours.index, fill_value=0.0).fillna(0.0)
        nb_voisins = nb_voisins.reindex(connus).reindex(cours.index, fill_value=0).fillna(0)
        popularite = self.popularite.reindex(connus).reindex(cours.index, fill_value=0.0).fillna(0.0)
        dans_domaines = cours["categorie"].isin(domaines).astype(float)
        revision = _revision(cours, cours_en_difficulte or [], suivis)
        score = (POIDS["collaboratif"] * collaboratif + POIDS["popularite"] * popularite
                 + POIDS["domaines"] * dans_domaines + BONUS_REVISION * revision)
        if not suivis:
            # Démarrage à froid : on commence par les niveaux accessibles plutôt que par un cours avancé.
            score = score - PENALITE_NIVEAU_DEBUT * cours["niveau"].map(_rang_niveau)

        resultats = []
        for c in score[candidats].sort_values(ascending=False, kind="stable").index[:n]:
            ligne = cours.loc[c]
            if revision[c] > 0:
                raison = f"Pour revoir les bases en {ligne['categorie']}"
            elif nb_voisins[c] >= 2 and POIDS["collaboratif"] * collaboratif[c] >= POIDS["domaines"] * dans_domaines[c]:
                raison = f"{int(nb_voisins[c])} apprenants au profil proche du tien ont suivi ce cours"
            elif dans_domaines[c]:
                raison = f"Dans un domaine qui t'intéresse : {ligne['categorie']}"
            elif popularite[c] > 0:
                raison = "Parmi les cours les plus suivis"
            else:
                raison = "Nouveau dans le catalogue"
            resultats.append(Recommandation(int(c), str(ligne["titre"]), round(float(score[c]), 3), raison))
        return resultats

    def _collaboratif(self, suivis: set[int], exclure: str | None) -> tuple[pd.Series, pd.Series]:
        """Score collaboratif (0 à 1) et nombre de voisins ayant suivi chaque cours."""
        vide = pd.Series(0.0, index=self.cours.index)
        if not suivis:
            return vide, vide
        matrice = self.matrice.drop(index=exclure) if exclure in self.matrice.index else self.matrice
        vecteur = pd.Series(0, index=matrice.columns)
        vecteur[[c for c in suivis if c in vecteur.index]] = 1
        sims = pd.Series(cosine_similarity(vecteur.to_numpy()[None, :], matrice.to_numpy())[0], index=matrice.index)
        voisins = sims.nlargest(K_VOISINS)
        voisins = voisins[voisins > 0]
        if voisins.empty:
            return vide, vide
        brut = (matrice.loc[voisins.index].T @ voisins) / voisins.sum()
        score = brut / brut.max() if brut.max() > 0 else brut
        return score.reindex(self.cours.index, fill_value=0.0), (
            matrice.loc[voisins.index].sum().reindex(self.cours.index, fill_value=0))


def _rang_niveau(niveau) -> int:
    """0 (débutant) à 2 (avancé) ; un niveau absent ou inconnu compte comme intermédiaire."""
    return NIVEAUX.index(niveau) if niveau in NIVEAUX else 1


def _revision(cours: pd.DataFrame, cours_en_difficulte: list[int], suivis: set[int]) -> pd.Series:
    """1 pour les cours plus faciles de la même catégorie qu'un cours où des notions ont été ratées."""
    bonus = pd.Series(0.0, index=cours.index)
    rangs = cours["niveau"].map(_rang_niveau)
    for c in cours_en_difficulte:
        if c not in cours.index:
            continue
        plus_faciles = cours[(cours["categorie"] == cours.loc[c, "categorie"]) & (rangs < rangs[c])].index
        bonus[[x for x in plus_faciles if x not in suivis]] = 1.0
    return bonus


def construire(inscriptions: pd.DataFrame, cours: pd.DataFrame) -> ModeleRecommandation:
    cours = cours.set_index("coursId") if "coursId" in cours.columns else cours
    matrice = (inscriptions.assign(suivi=1)
               .pivot_table(index="apprenant", columns="coursId", values="suivi", fill_value=0)
               .reindex(columns=cours.index, fill_value=0))
    nb = matrice.sum()
    return ModeleRecommandation(matrice, cours, nb / nb.max())


def _domaines(apprenants: pd.DataFrame) -> dict[str, list[str]]:
    if "domaines" not in apprenants.columns:
        return {}
    return {a: str(d).split("|") for a, d in zip(apprenants["apprenant"], apprenants["domaines"].fillna(""))}


def evaluer(apprenants: pd.DataFrame, inscriptions: pd.DataFrame, cours: pd.DataFrame) -> dict:
    """Taux de réussite @3 et @5 et couverture du catalogue, comparés à deux références."""
    domaines = _domaines(apprenants)
    resultats: dict[str, dict[str, list[float]]] = {}
    for graine in GRAINES_EVALUATION:
        caches = (inscriptions.groupby("apprenant").filter(lambda g: len(g) >= 2)
                  .groupby("apprenant").sample(1, random_state=graine))
        modele = construire(inscriptions.drop(caches.index), cours)
        populaires = list(modele.popularite.sort_values(ascending=False).index)
        methodes = {
            "hybride": lambda a, suivis, n: [r.coursId for r in modele.recommander(suivis, domaines.get(a), n=n, exclure=a)],
            "popularite": lambda a, suivis, n: [c for c in populaires if c not in suivis][:n],
            "collaboratifSeul": lambda a, suivis, n: [r.coursId for r in ModeleRecommandation(
                modele.matrice, modele.cours, modele.popularite * 0).recommander(suivis, None, n=n, exclure=a)],
        }
        for nom, methode in methodes.items():
            for n in (3, 5):
                recos, touches = set(), []
                for ligne in caches.itertuples():
                    suivis = list(modele.matrice.columns[modele.matrice.loc[ligne.apprenant] > 0])
                    liste = methode(ligne.apprenant, suivis, n)
                    recos.update(liste)
                    touches.append(ligne.coursId in liste)
                r = resultats.setdefault(nom, {})
                r.setdefault(f"reussiteA{n}", []).append(float(np.mean(touches)))
                r.setdefault(f"couvertureA{n}", []).append(len(recos) / len(modele.cours))
    return {nom: {k: round(float(np.mean(v)), 3) for k, v in m.items()} for nom, m in resultats.items()} | {
        "nbApprenantsEvalues": int(inscriptions.groupby("apprenant").size().ge(2).sum()),
        "tirages": len(GRAINES_EVALUATION),
    }


def entrainer(apprenants: pd.DataFrame, inscriptions: pd.DataFrame, cours: pd.DataFrame) -> ModeleRecommandation:
    modele = construire(inscriptions, cours)
    modele.metriques = {
        "poids": POIDS,
        "kVoisins": K_VOISINS,
        "nbApprenants": int(modele.matrice.shape[0]),
        "nbCours": int(modele.matrice.shape[1]),
        "nbInscriptions": int(len(inscriptions)),
        "densite": round(float(modele.matrice.to_numpy().mean()), 3),
        "evaluation": evaluer(apprenants, inscriptions, cours),
    }
    return modele


if __name__ == "__main__":
    from app.modules.user.ml.donnees_simulees import generer

    apprenants, inscriptions, cours = generer()
    m = entrainer(apprenants, inscriptions, cours)
    ev = m.metriques["evaluation"]
    print(f"Matrice {m.metriques['nbApprenants']} apprenants × {m.metriques['nbCours']} cours, densité {m.metriques['densite']:.0%}")
    print(f"Évaluation ({ev['nbApprenantsEvalues']} apprenants, {ev['tirages']} tirages) :")
    for nom in ("hybride", "collaboratifSeul", "popularite"):
        r = ev[nom]
        print(f"  {nom:17s} réussite@3 {r['reussiteA3']:.3f}  réussite@5 {r['reussiteA5']:.3f}  couverture@3 {r['couvertureA3']:.0%}")
    print("\nExemples :")
    for a in ("A001", "A003"):
        ligne = apprenants.set_index("apprenant").loc[a]
        suivis = list(inscriptions.loc[inscriptions.apprenant == a, "coursId"])
        print(f"  {a} suit {suivis}, domaines {ligne['domaines']} :")
        for r in m.recommander(suivis, ligne["domaines"].split("|"), exclure=a):
            print(f"     -> [{r.coursId}] {r.titre} ({r.score}) : {r.raison}")
    print("  Nouvel apprenant (aucun cours), intéressé par Data :")
    for r in m.recommander([], ["Data"]):
        print(f"     -> [{r.coursId}] {r.titre} : {r.raison}")
    print("  A raté des notions dans « Spring Boot » (8) :")
    for r in m.recommander([8, 12], ["Back-end"], cours_en_difficulte=[8]):
        print(f"     -> [{r.coursId}] {r.titre} : {r.raison}")
