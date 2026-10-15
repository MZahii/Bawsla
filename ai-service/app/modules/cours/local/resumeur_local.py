"""Résumé reformulé par un modèle Hugging Face local (CPU) et orchestration auto / abstractif / extractif.

- torch et transformers sont importés DANS les fonctions : sans eux, seul le mode abstractif est indisponible (503).
- Un seul modèle en mémoire à la fois : charger le modèle d'une autre langue décharge le précédent ; un modèle inactif depuis
  RESUME_LOCAL_LIBERER_APRES_SECONDES est déchargé.
- Chargement et inférence passent par UN thread dédié (jamais deux chargements en parallèle, FastAPI n'est pas bloqué).
"""

import logging
import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any, Protocol

from app.core.sanitizer import Sanitizer
from app.modules.cours.config import CoursAiSettings
from app.modules.cours.explication.generation import CoursSansTexteError, nettoyer_pages
from app.modules.cours.local import langue as detecteur
from app.modules.cours.local.extractif import resumer_extractif
from app.modules.cours.local.garde_fou import Controle, controle_extractif, evaluer
from app.modules.cours.local.gestionnaire import GestionnaireModele, get_gestionnaire
from app.modules.cours.local.schemas import (
    LangueNonSupporteeError,
    Longueur,
    ModeResume,
    ResumeLocalIndisponibleError,
    ResumeLocalRequest,
    ResumeLocalResponse,
)
from app.modules.cours.local.texte import Unite, decouper_en_unites, phrases_du_texte

log = logging.getLogger("bawsla.cours.local")

#: Jetons de sortie (minimum, maximum) du résumé final selon la longueur demandée.
LONGUEURS = {Longueur.COURTE: (30, 70), Longueur.MOYENNE: (60, 130), Longueur.LONGUE: (100, 220)}
#: Jetons réservés aux jetons spéciaux du modèle dans chaque morceau.
MARGE_ENTREE = 16
#: Taille d'un morceau d'entrée (jetons) : on reste sous la capacité du modèle (1024) pour garder de la marge.
MORCEAUX_MAX_TOKENS = 900
NIVEAUX_MAX = 3

ALERTE_PEU_FIDELE = "Résumé reformulé peu fidèle : remplacé par les phrases clés du cours"
ALERTE_SANS_MODELE = "Modèle de reformulation indisponible sur ce serveur : résumé par phrases clés du cours"


@dataclass(frozen=True)
class ResultatAbstractif:
    texte: str
    modele: str
    morceaux_total: int
    morceaux_utilises: int


class MoteurAbstractif(Protocol):
    """Ce que le service attend d'un moteur de reformulation (le vrai utilise transformers, les tests un faux)."""

    def disponible(self) -> bool: ...

    async def resumer(self, unites: list[Unite], langue: str, longueur: Longueur) -> ResultatAbstractif: ...


def bibliotheques_presentes() -> bool:
    """torch et transformers sont-ils installés ? (sans les importer : l'import coûte plusieurs secondes)."""
    import importlib.util

    return all(importlib.util.find_spec(m) is not None for m in ("torch", "transformers", "sentencepiece"))


def decouper_en_morceaux(unites: list[Unite], mesurer: Callable[[str], int], taille_max: int) -> list[list[Unite]]:
    """Regroupe les unités dans l'ordre en morceaux d'au plus `taille_max` jetons, sans couper une phrase en deux."""
    morceaux: list[list[Unite]] = []
    courant: list[Unite] = []
    taille = 0
    for u in unites:
        t = mesurer(u.texte)
        if courant and taille + t > taille_max:
            morceaux.append(courant)
            courant, taille = [], 0
        courant.append(u)
        taille += t
    if courant:
        morceaux.append(courant)
    return morceaux


class MoteurTransformers:
    """Moteur réel : un seul modèle chargé à la fois, chargement paresseux, thread dédié, déchargement après inactivité.

    La mémoire et le thread sont ceux du gestionnaire commun (`local/gestionnaire.py`), partagé avec la lecture d'images locale.
    """

    def __init__(self, reglages: CoursAiSettings, gestionnaire: GestionnaireModele | None = None) -> None:
        self._reglages = reglages
        self._gestionnaire = gestionnaire or GestionnaireModele()

    @property
    def _charge(self) -> tuple[str, Any, Any] | None:
        return self._gestionnaire.charge

    @_charge.setter
    def _charge(self, valeur: tuple[str, Any, Any] | None) -> None:
        self._gestionnaire.charge = valeur

    @property
    def _derniere_utilisation(self) -> float:
        return self._gestionnaire.derniere_utilisation

    @_derniere_utilisation.setter
    def _derniere_utilisation(self, valeur: float) -> None:
        self._gestionnaire.derniere_utilisation = valeur

    def disponible(self) -> bool:
        return bibliotheques_presentes()

    def nom_modele(self, langue: str) -> str:
        return self._reglages.resume_local_modele_fr if langue == detecteur.FR else self._reglages.resume_local_modele_en

    @property
    def modele_charge(self) -> str | None:
        return self._charge[0] if self._charge else None

    async def resumer(self, unites: list[Unite], langue: str, longueur: Longueur) -> ResultatAbstractif:
        if not self.disponible():
            raise ResumeLocalIndisponibleError()
        return await self._gestionnaire.executer(self._resumer_bloquant, unites, langue, longueur)

    # ------------------------------------------------------------------ thread dédié

    def _resumer_bloquant(self, unites: list[Unite], langue: str, longueur: Longueur) -> ResultatAbstractif:
        import torch

        nom = self.nom_modele(langue)
        tokenizer, modele = self._obtenir(nom)
        try:
            capacite = _capacite(tokenizer, modele)
            taille = min(MORCEAUX_MAX_TOKENS, capacite - MARGE_ENTREE)

            def mesurer(texte: str) -> int:
                return len(tokenizer(texte, add_special_tokens=False)["input_ids"])

            morceaux = decouper_en_morceaux(unites, mesurer, taille)
            total = len(morceaux)
            morceaux = morceaux[: self._reglages.resume_local_morceaux_max]
            mini, maxi = LONGUEURS[longueur]
            textes = [" ".join(u.texte for u in m) for m in morceaux]
            with torch.inference_mode():
                if len(textes) == 1:
                    resume = self._generer(tokenizer, modele, textes[0], mini, maxi, taille)
                else:
                    # Résumé hiérarchique : un résumé par morceau, puis un résumé des résumés (jusqu'à tenir en un morceau).
                    partiels = [self._generer(tokenizer, modele, t, mini // 2, maxi // 2 + 10, taille) for t in textes]
                    resume = " ".join(partiels)
                    for _ in range(NIVEAUX_MAX):
                        if mesurer(resume) <= taille:
                            break
                        resume = " ".join(self._generer(tokenizer, modele, t, mini // 2, maxi // 2 + 10, taille)
                                          for t in _redecouper(resume, mesurer, taille))
                    resume = self._generer(tokenizer, modele, resume, mini, maxi, taille)
            return ResultatAbstractif(resume, nom, total, len(morceaux))
        finally:
            self._gestionnaire.utilise(self._reglages.resume_local_liberer_apres_secondes)

    @staticmethod
    def _generer(tokenizer: Any, modele: Any, texte: str, mini: int, maxi: int, taille: int) -> str:
        entree = tokenizer(texte, return_tensors="pt", truncation=True, max_length=taille + MARGE_ENTREE)
        entree.pop("token_type_ids", None)  # certains tokenizers (BARThez) en ajoutent, generate() n'en veut pas
        nb = int(entree["input_ids"].shape[1])
        sortie = modele.generate(
            **entree,
            num_beams=4,
            no_repeat_ngram_size=3,
            early_stopping=True,
            min_new_tokens=max(5, min(mini, nb // 3)),
            max_new_tokens=max(20, min(maxi, nb)),
        )
        return " ".join(tokenizer.decode(sortie[0], skip_special_tokens=True).split())

    def _obtenir(self, nom: str) -> tuple[Any, Any]:
        """Tokenizer et modèle de `nom` ; décharge un autre modèle d'abord (jamais deux en mémoire)."""

        def charger() -> tuple[Any, Any]:
            from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

            try:
                tokenizer = AutoTokenizer.from_pretrained(nom)
            except Exception:  # noqa: BLE001 - le tokenizer rapide plante pour certains modèles : repli sur la version Python
                log.warning("Résumé local : tokenizer rapide en échec pour %s, repli use_fast=False", nom)
                tokenizer = AutoTokenizer.from_pretrained(nom, use_fast=False)
            modele = AutoModelForSeq2SeqLM.from_pretrained(nom)
            modele.eval()
            return tokenizer, modele

        return self._gestionnaire.obtenir(nom, charger, "Résumé local")

    def liberer_si_inactif(self, maintenant: float | None = None) -> bool:
        """Décharge le modèle s'il est inactif depuis assez longtemps. Renvoie True si la mémoire a été libérée."""
        return self._gestionnaire.liberer_si_inactif(self._reglages.resume_local_liberer_apres_secondes, maintenant)


def _capacite(tokenizer: Any, modele: Any) -> int:
    """Nombre de jetons que le modèle accepte en entrée (lu dans sa configuration, pas codé en dur)."""
    candidats = [getattr(modele.config, "max_position_embeddings", None), getattr(tokenizer, "model_max_length", None)]
    valides = [int(c) for c in candidats if isinstance(c, int | float) and 0 < c < 100_000]
    return min(valides) if valides else 512


def _redecouper(texte: str, mesurer: Callable[[str], int], taille: int) -> list[str]:
    morceaux = decouper_en_morceaux([Unite(p, 0) for p in phrases_du_texte(texte)], mesurer, taille)
    return [" ".join(u.texte for u in m) for m in morceaux] or [texte]


_moteur: MoteurTransformers | None = None


def get_moteur(reglages: CoursAiSettings) -> MoteurTransformers:
    """Moteur unique du processus (un seul modèle en mémoire, un seul thread d'inférence)."""
    global _moteur
    if _moteur is None:
        _moteur = MoteurTransformers(reglages, get_gestionnaire())
    return _moteur


# ---------------------------------------------------------------------------------------------- orchestration


async def resumer_local(
    requete: ResumeLocalRequest, sanitizer: Sanitizer, reglages: CoursAiSettings, moteur: MoteurAbstractif
) -> ResumeLocalResponse:
    debut = time.monotonic()
    if not reglages.resume_local_actif:
        raise ResumeLocalIndisponibleError()
    pages = nettoyer_pages(requete.pages, sanitizer)
    if sum(len(p.texte) for p in pages) < reglages.explication_texte_min_chars:
        raise CoursSansTexteError(
            "Le cours ne contient pas assez de texte à résumer. Si le PDF est un scan, envoie un PDF avec du texte "
            "sélectionnable ou ajoute du contenu dans les chapitres."
        )
    langue = detecteur.detecter_langue(" ".join(p.texte for p in pages))
    if langue == detecteur.AUTRE:
        raise LangueNonSupporteeError(
            "Langue du cours non prise en charge : le résumé automatique local fonctionne en français et en anglais."
        )
    unites = decouper_en_unites(pages)
    if len(unites) < 3:
        raise CoursSansTexteError("Le cours ne contient pas assez de phrases complètes pour être résumé.")

    alertes: list[str] = []
    mode = requete.mode
    reformule: tuple[ResultatAbstractif, Controle] | None = None
    score_reformule: int | None = None

    if mode != ModeResume.EXTRACTIF:
        if not moteur.disponible():
            if mode == ModeResume.ABSTRACTIF:
                raise ResumeLocalIndisponibleError()
            alertes.append(ALERTE_SANS_MODELE)
        else:
            resultat = await moteur.resumer(unites, langue, requete.longueur)
            controle = evaluer(phrases_du_texte(resultat.texte), unites, langue, reglages.resume_local_seuil_phrase)
            score_reformule = controle.score
            log.info("Résumé local : modèle=%s langue=%s score reformulé=%d", resultat.modele, langue, controle.score)
            if resultat.morceaux_utilises < resultat.morceaux_total:
                alertes.append(
                    f"Cours tronqué : {resultat.morceaux_utilises} morceau(x) sur {resultat.morceaux_total} ont été résumés."
                )
            if mode == ModeResume.AUTO and controle.score < reglages.resume_local_seuil:
                alertes.append(ALERTE_PEU_FIDELE)
            else:
                reformule = (resultat, controle)
                alertes.extend(controle.alertes)
                if mode == ModeResume.ABSTRACTIF and controle.score < reglages.resume_local_seuil:
                    alertes.append("Résumé reformulé peu fidèle : relis-le avec attention avant de le valider.")

    if reformule is not None:
        resultat, controle = reformule
        modele, utilise = resultat.modele, ModeResume.ABSTRACTIF
    else:
        retenues = resumer_extractif(unites, requete.longueur)
        controle = controle_extractif(retenues)
        modele, utilise = "extractif-textrank", ModeResume.EXTRACTIF

    return ResumeLocalResponse(
        langue=langue,
        modele=modele,
        mode_utilise=utilise,
        resume=" ".join(p.texte for p in controle.phrases),
        phrases=controle.phrases,
        score_fidelite=controle.score,
        score_reformule=score_reformule,
        alertes=alertes,
        duree_ms=round((time.monotonic() - debut) * 1000),
    )
