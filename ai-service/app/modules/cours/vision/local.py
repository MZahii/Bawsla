"""Lecture d'une page par Florence-2 (Microsoft, MIT) sur CPU : secours LOCAL quand Gemini est indisponible.

- OCR avec positions (`<OCR_WITH_REGION>`) : les lignes sont remises dans l'ordre de lecture (haut vers bas, puis gauche vers droite).
  Limite : une page à plusieurs colonnes est lue ligne par ligne, colonnes mélangées.
- `<MORE_DETAILED_CAPTION>` : si la description évoque un schéma, un graphique ou un diagramme, elle est ajoutée sous la forme
  « [Figure : … ] ». Florence-2 décrit en anglais.
- Le modèle exécute du code distant (`trust_remote_code`) : seuls les modèles de REVISIONS sont acceptés, à une révision ÉPINGLÉE.
- torch, transformers, Pillow, einops et timm sont importés DANS les fonctions : sans eux, la lecture locale est simplement indisponible.
- Mémoire et thread : gestionnaire commun avec le résumé local (un seul modèle en mémoire dans le processus).
"""

import contextlib
import io
import logging
import re
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from app.modules.cours.config import CoursAiSettings
from app.modules.cours.local.gestionnaire import GestionnaireModele, get_gestionnaire

log = logging.getLogger("bawsla.cours.vision")

#: nom Hugging Face -> révision (commit) du dépôt. Le code distant téléchargé ne change donc jamais sans qu'on change ce hash.
REVISIONS = {
    "microsoft/Florence-2-base": "5ca5edf5bd017b9919c05d08aebef5e4c7ac3bac",
    "microsoft/Florence-2-large": "21a599d414c4d928c9032694c424fb94458e3594",
}
FOURNISSEUR_LOCAL = "local · Florence-2"
TACHE_OCR = "<OCR_WITH_REGION>"
TACHE_DESCRIPTION = "<MORE_DETAILED_CAPTION>"
#: Mots (anglais) de la description qui indiquent un schéma plutôt qu'une page de texte.
MOTS_FIGURE = re.compile(r"\b(diagram|chart|graph|plot|flowchart|schematic|illustration|drawing|figure|map|curve|axis|axes)\b", re.I)
JETONS_OCR = 1024
JETONS_DESCRIPTION = 160
BALISES = re.compile(r"</?s>|<pad>|<unk>")
MODULES_REQUIS = ("torch", "transformers", "PIL", "einops", "timm")


class ModeleLocalInconnuError(ValueError):
    """Le nom de modèle demandé n'a pas de révision épinglée : on n'exécute pas un code distant non vérifié."""


@dataclass(frozen=True)
class LectureLocale:
    texte: str
    #: Description d'un schéma ou d'un diagramme (vide si la page n'en contient pas).
    figure: str


class MoteurVision(Protocol):
    """Ce que le service attend d'un moteur de lecture locale (le vrai utilise Florence-2, les tests un faux)."""

    def disponible(self) -> bool: ...

    async def lire(self, image: bytes) -> LectureLocale: ...


def revision_epinglee(nom: str) -> str:
    try:
        return REVISIONS[nom]
    except KeyError:
        raise ModeleLocalInconnuError(f"Modèle de vision locale non pris en charge : {nom}") from None


def bibliotheques_presentes() -> bool:
    import importlib.util

    return all(importlib.util.find_spec(m) is not None for m in MODULES_REQUIS)


@contextlib.contextmanager
def sans_flash_attn() -> Iterator[None]:
    """Florence-2 mentionne flash_attn dans son code (sous `if is_flash_attn_2_available()`), et transformers refuse de charger un code
    distant dont un import n'est pas installé, même non utilisé. On retire donc flash_attn de la liste des imports exigés, le temps du
    chargement seulement : la fonction d'origine est TOUJOURS restaurée, même si le chargement plante."""
    from transformers import dynamic_module_utils

    original = dynamic_module_utils.get_imports

    def corrige(fichier: str | Path) -> list[str]:
        imports = original(fichier)
        return [i for i in imports if i != "flash_attn"]

    dynamic_module_utils.get_imports = corrige
    try:
        yield
    finally:
        dynamic_module_utils.get_imports = original


def reparer_cache(modele: Any) -> None:
    """Avec transformers >= 4.50, `generate()` donne au code de Florence-2 un cache VIDE (et non None) au premier pas, et
    `past_key_values[0][0].shape` plante. Désactiver le cache règle l'erreur mais rend la lecture ~9 fois plus lente (mesuré : 54 s au lieu
    de 6 s pour 280 jetons, car chaque jeton recalcule toute la séquence). On rend donc None au code du modèle quand le cache est vide."""
    langage = modele.language_model
    original = langage.prepare_inputs_for_generation

    def preparer(decoder_input_ids: Any, past_key_values: Any = None, **kwargs: Any) -> Any:
        if past_key_values is not None and hasattr(past_key_values, "get_seq_length") and past_key_values.get_seq_length() == 0:
            past_key_values = None
        return original(decoder_input_ids, past_key_values=past_key_values, **kwargs)

    langage.prepare_inputs_for_generation = preparer


def ordonner_lignes(boites: list[list[float]], etiquettes: list[str]) -> str:
    """Remet les fragments de l'OCR dans l'ordre de lecture : on regroupe les fragments d'une même ligne (centres verticaux proches),
    lignes de haut en bas, fragments de gauche à droite."""
    fragments = []
    for coins, etiquette in zip(boites, etiquettes, strict=False):
        texte = BALISES.sub("", etiquette).strip()
        if not texte or len(coins) < 8:
            continue
        xs, ys = coins[0::2], coins[1::2]
        fragments.append((sum(ys) / len(ys), max(ys) - min(ys), min(xs), texte))
    if not fragments:
        return ""
    hauteurs = sorted(f[1] for f in fragments)
    tolerance = max(4.0, 0.6 * hauteurs[len(hauteurs) // 2])
    fragments.sort(key=lambda f: f[0])
    lignes: list[list[tuple[float, float, float, str]]] = []
    for f in fragments:
        if lignes and abs(f[0] - lignes[-1][-1][0]) <= tolerance:
            lignes[-1].append(f)
        else:
            lignes.append([f])
    return "\n".join(" ".join(f[3] for f in sorted(ligne, key=lambda f: f[2])) for ligne in lignes)


def figure_decrite(description: str) -> str:
    """La description de Florence-2 si elle évoque un schéma, sinon rien (une page de texte n'a pas de « figure »)."""
    description = BALISES.sub("", description).strip()
    if not description or not MOTS_FIGURE.search(description):
        return ""
    if not description.endswith((".", "!", "?")) and "." in description:  # la génération est plafonnée : on coupe à la dernière phrase complète
        description = description[: description.rindex(".") + 1]
    return " ".join(description.split())


class MoteurFlorence:
    """Moteur réel : Florence-2 chargé à la demande par le gestionnaire commun (un seul modèle en mémoire, thread dédié)."""

    def __init__(self, reglages: CoursAiSettings, gestionnaire: GestionnaireModele | None = None) -> None:
        self._reglages = reglages
        self._gestionnaire = gestionnaire or GestionnaireModele()

    def disponible(self) -> bool:
        return bibliotheques_presentes()

    @property
    def nom_modele(self) -> str:
        return self._reglages.vision_locale_modele

    async def lire(self, image: bytes) -> LectureLocale:
        return await self._gestionnaire.executer(self._lire_bloquant, image)

    # ------------------------------------------------------------------ thread dédié

    def _lire_bloquant(self, octets: bytes) -> LectureLocale:
        import torch
        from PIL import Image

        processeur, modele = self._obtenir(self.nom_modele)
        try:
            with Image.open(io.BytesIO(octets)) as ouverte:
                image = ouverte.convert("RGB")
            with torch.inference_mode():
                ocr = self._tache(processeur, modele, image, TACHE_OCR, JETONS_OCR, faisceaux=3)
                description = self._tache(processeur, modele, image, TACHE_DESCRIPTION, JETONS_DESCRIPTION, faisceaux=1)
            texte = ordonner_lignes(ocr.get("quad_boxes", []), ocr.get("labels", []))
            return LectureLocale(texte=texte, figure=figure_decrite(description if isinstance(description, str) else ""))
        finally:
            self._gestionnaire.utilise(self._reglages.resume_local_liberer_apres_secondes)

    @staticmethod
    def _tache(processeur: Any, modele: Any, image: Any, tache: str, jetons: int, *, faisceaux: int) -> Any:
        entrees = processeur(text=tache, images=image, return_tensors="pt")
        sortie = modele.generate(
            input_ids=entrees["input_ids"],
            pixel_values=entrees["pixel_values"],
            max_new_tokens=jetons,
            num_beams=faisceaux,
            do_sample=False,
            use_cache=True,
        )
        brut = processeur.batch_decode(sortie, skip_special_tokens=False)[0]
        return processeur.post_process_generation(brut, task=tache, image_size=(image.width, image.height))[tache]

    def _obtenir(self, nom: str) -> tuple[Any, Any]:
        """Processeur et modèle de `nom` (révision épinglée) ; décharge d'abord un autre modèle (résumé local compris)."""
        revision = revision_epinglee(nom)

        def charger() -> tuple[Any, Any]:
            import torch
            from transformers import AutoModelForCausalLM, AutoProcessor

            with sans_flash_attn():
                modele = AutoModelForCausalLM.from_pretrained(
                    nom, revision=revision, trust_remote_code=True, attn_implementation="eager", torch_dtype=torch.float32
                )
                processeur = AutoProcessor.from_pretrained(nom, revision=revision, trust_remote_code=True)
            reparer_cache(modele)
            modele.eval()
            return processeur, modele

        return self._gestionnaire.obtenir(nom, charger, "Vision locale")


_moteur: MoteurFlorence | None = None


def get_moteur_vision(reglages: CoursAiSettings) -> MoteurFlorence:
    """Moteur unique du processus, sur le gestionnaire de modèle partagé avec le résumé local."""
    global _moteur
    if _moteur is None:
        _moteur = MoteurFlorence(reglages, get_gestionnaire())
    return _moteur
