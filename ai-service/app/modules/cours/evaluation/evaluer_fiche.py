"""Évaluation des prompts de la fiche de révision (v1, v2, v3) pour le rapport. À LANCER À LA MAIN.

Pour chaque cours (fichier .txt) et chaque version du prompt, la fiche est générée avec le MÊME modèle, la MÊME température et le même
pipeline que la production (`generer_fiche`), puis mesurée :

- score de fidélité (fidelite.py), notions non justifiées, nombre de notions ;
- mots-clés absents du texte du cours, nombre de mots-clés ;
- longueur du résumé (mots), durée de génération, fournisseur réel (gemini, groq, openai) et modèle.

Résultats : un tableau CSV et un Markdown dans prompts_journal/cours/evaluation/resultats/.

    cd ai-service
    .venv/Scripts/python -m app.modules.cours.evaluation.evaluer_fiche --dry-run        # faux LLM, aucun quota, aucune clé
    .venv/Scripts/python -m app.modules.cours.evaluation.evaluer_fiche                  # VRAI LLM : demande confirmation

Avant tout appel réel, le script annonce le nombre d'appels et demande « o » pour continuer. Il ne lit jamais la clé : le client est
construit par la fabrique du socle à partir du .env, comme pour l'application.
"""

import argparse
import asyncio
import csv
import logging
import re
import statistics
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path

from app.core.config import get_settings
from app.core.errors import LLMError
from app.core.llm_client import LLMClient, LLMMessage, LLMResult
from app.core.sanitizer import BasicSanitizer
from app.modules.cours import fiche as module_fiche
from app.modules.cours.clients_llm import construire_client
from app.modules.cours.config import CoursAiSettings, get_cours_ai_settings
from app.modules.cours.decoupage import decouper
from app.modules.cours.fidelite import SEUIL_MOT_CLE, _retrouve, normaliser
from app.modules.cours.fiche import generer_fiche
from app.modules.cours.prompts import prompt_fiche, versions_disponibles
from app.modules.cours.schemas import FicheLLM, Niveau, Notion, ResumeSeul

RACINE = Path(__file__).resolve().parents[5]
COURS_PAR_DEFAUT = RACINE / "prompts_journal" / "cours" / "evaluation" / "cours"
SORTIE_PAR_DEFAUT = RACINE / "prompts_journal" / "cours" / "evaluation" / "resultats"
VERSIONS_PAR_DEFAUT = ["v1", "v2", "v3"]

COLONNES = [
    "cours", "version", "score_fidelite", "notions", "notions_non_justifiees", "mots_cles", "mots_cles_absents",
    "longueur_resume_mots", "duree_s", "fournisseur", "modele", "erreur",
]


@dataclass
class Mesure:
    cours: str
    version: str
    score_fidelite: int | None = None
    notions: int | None = None
    notions_non_justifiees: int | None = None
    mots_cles: int | None = None
    mots_cles_absents: int | None = None
    longueur_resume_mots: int | None = None
    duree_s: float | None = None
    fournisseur: str = ""
    modele: str = ""
    erreur: str = ""


# ---------------------------------------------------------------- faux LLM (--dry-run)


class LLMFactice(LLMClient):
    """Faux LLM déterministe pour --dry-run : une fiche différente selon la version du prompt, bâtie sur le texte du cours. Aucun réseau."""

    def __init__(self, texte: str) -> None:
        self.systemes = {v: prompt_fiche(v).systeme for v in versions_disponibles()}
        self.phrases = [p.strip() for p in re.split(r"(?<=[.!?])\s+", texte) if len(p.split()) >= 8]
        self.appels = 0

    def _version(self, messages: list[LLMMessage]) -> str:
        systeme = messages[0].content if messages else ""
        return next((v for v, s in self.systemes.items() if s == systeme), "v3")

    async def complete_json(self, messages, schema, *, module, temperature=0.0, max_tokens=2048):
        self.appels += 1
        if schema is ResumeSeul:
            return ResumeSeul(resume="Résumé global factice du cours, produit sans aucun appel réseau.")
        version = self._version(messages)
        # v1 : beaucoup de notions dont des extraits inventés ; v2 : équilibrée ; v3 : sobre et fidèle.
        nombre = {"v1": 6, "v2": 5, "v3": 4}.get(version, 4)
        inventees = {"v1": 2, "v2": 1, "v3": 0}.get(version, 0)
        notions = []
        for i in range(nombre):
            if i < inventees:
                extrait = f"Un passage inventé numéro {i + 1} qui ne figure pas dans le cours."
            else:
                extrait = self.phrases[i % len(self.phrases)] if self.phrases else ""
            notions.append(Notion(titre=f"Notion {i + 1}", definition=f"Définition factice de la notion {i + 1}.", extrait_source=extrait))
        mots = [p.split()[2].strip(",.;:()") for p in self.phrases[:4] if len(p.split()) > 3]
        mots += ["motinexistant"] * {"v1": 2, "v2": 1, "v3": 0}.get(version, 0)
        return FicheLLM(resume=" ".join(self.phrases[: {"v1": 5, "v2": 4, "v3": 3}.get(version, 3)]) or "Résumé factice.", mots_cles=mots, notions=notions)

    async def complete(self, messages, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="factice", model="faux", fournisseur="dry-run")

    async def complete_with_image(self, messages, image, mime_type, *, module, temperature=0.2, max_tokens=1024):
        return LLMResult(text="factice", model="faux", fournisseur="dry-run")

    async def synthetiser(self, texte, voix, langue):
        raise NotImplementedError

    async def synthetiser_dialogue(self, repliques, voix_par_locuteur, langue):
        raise NotImplementedError


# ---------------------------------------------------------------- calcul de la charge et de la confirmation


def nombre_appels(texte: str, reglages: CoursAiSettings) -> int:
    """Appels au LLM pour UNE fiche : un par morceau (6 au plus), plus un appel de synthèse s'il y a plusieurs morceaux."""
    morceaux = min(len(decouper(texte, reglages.fiche_morceau_chars)), reglages.fiche_morceaux_max)
    return morceaux + (1 if morceaux > 1 else 0)


def lire_cours(dossier: Path) -> dict[str, str]:
    fichiers = sorted(dossier.glob("*.txt"))
    return {f.stem: f.read_text(encoding="utf-8").strip() for f in fichiers if f.read_text(encoding="utf-8").strip()}


def valider_versions(versions: list[str]) -> list[str]:
    inconnues = [v for v in versions if v not in versions_disponibles()]
    if inconnues:
        raise SystemExit(f"Version(s) de prompt inconnue(s) : {', '.join(inconnues)} (disponibles : {', '.join(versions_disponibles())}).")
    return versions


# ---------------------------------------------------------------- mesure


async def mesurer(nom: str, version: str, texte: str, llm: LLMClient, reglages: CoursAiSettings, modele: str, dry_run: bool) -> Mesure:
    """Génère la fiche avec cette version du prompt (même température : celle du module) et la mesure."""
    mesure = Mesure(cours=nom, version=version)
    reglages_version = reglages.model_copy(update={"prompt_fiche_version": version})
    debut = time.monotonic()
    try:
        fiche = await generer_fiche(
            llm, BasicSanitizer(get_settings().sanitizer_max_chars), texte, Niveau.INTERMEDIAIRE, reglages=reglages_version, modele=modele
        )
    except LLMError as exc:
        mesure.duree_s = round(time.monotonic() - debut, 1)
        mesure.erreur = exc.message
        return mesure
    mesure.duree_s = round(time.monotonic() - debut, 1)
    source = normaliser(texte)
    mesure.score_fidelite = fiche.score_fidelite
    mesure.notions = len(fiche.notions)
    mesure.notions_non_justifiees = sum(1 for n in fiche.notions if not n.justifiee)
    mesure.mots_cles = len(fiche.mots_cles)
    mesure.mots_cles_absents = sum(1 for m in fiche.mots_cles if not _retrouve(normaliser(m), source, SEUIL_MOT_CLE))
    mesure.longueur_resume_mots = len(fiche.resume.split())
    mesure.fournisseur = "dry-run" if dry_run else (fiche.fournisseur or "")
    mesure.modele = "faux-llm" if dry_run else fiche.modele
    return mesure


# ---------------------------------------------------------------- écriture des résultats


def ecrire_csv(mesures: list[Mesure], chemin: Path) -> None:
    with chemin.open("w", encoding="utf-8-sig", newline="") as f:
        ecrivain = csv.DictWriter(f, fieldnames=COLONNES)
        ecrivain.writeheader()
        for m in mesures:
            ecrivain.writerow({k: ("" if v is None else v) for k, v in asdict(m).items()})


def _moyenne(valeurs: list[float | int | None]) -> str:
    utiles = [v for v in valeurs if v is not None]
    return f"{statistics.mean(utiles):.1f}" if utiles else "—"


def ecrire_markdown(mesures: list[Mesure], chemin: Path, *, dry_run: bool, versions: list[str], reglages: CoursAiSettings, modele: str) -> None:
    lignes = [
        "# Évaluation des prompts de la fiche de révision",
        "",
        f"- Date : {datetime.now():%Y-%m-%d %H:%M}",
        f"- Mode : {'**DRY-RUN (faux LLM, chiffres sans valeur)**' if dry_run else 'LLM réel'}",
        f"- Modèle configuré : {'faux-llm' if dry_run else modele} ; température : {module_fiche.TEMPERATURE} (identique pour toutes les versions)",
        f"- Versions comparées : {', '.join(versions)} ; cours : {len({m.cours for m in mesures})}",
        f"- Morceaux de {reglages.fiche_morceau_chars} caractères au plus, {reglages.fiche_morceaux_max} morceaux au maximum.",
        "",
        "## Moyennes par version",
        "",
        "| Version | Score de fidélité | Notions | Notions non justifiées | Mots-clés absents | Résumé (mots) | Durée (s) | Échecs |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for v in versions:
        ms = [m for m in mesures if m.version == v]
        lignes.append(
            f"| {v} | {_moyenne([m.score_fidelite for m in ms])} | {_moyenne([m.notions for m in ms])} | "
            f"{_moyenne([m.notions_non_justifiees for m in ms])} | {_moyenne([m.mots_cles_absents for m in ms])} | "
            f"{_moyenne([m.longueur_resume_mots for m in ms])} | {_moyenne([m.duree_s for m in ms])} | {sum(1 for m in ms if m.erreur)} |"
        )
    lignes += ["", "## Détail par cours", ""]
    for cours in sorted({m.cours for m in mesures}):
        lignes += [
            f"### {cours}",
            "",
            "| Version | Score | Notions | Non justifiées | Mots-clés | Absents | Résumé (mots) | Durée (s) | Fournisseur | Erreur |",
            "|---|---|---|---|---|---|---|---|---|---|",
        ]
        for m in (x for x in mesures if x.cours == cours):
            lignes.append(
                f"| {m.version} | {_v(m.score_fidelite)} | {_v(m.notions)} | {_v(m.notions_non_justifiees)} | {_v(m.mots_cles)} | "
                f"{_v(m.mots_cles_absents)} | {_v(m.longueur_resume_mots)} | {_v(m.duree_s)} | {m.fournisseur or '—'} | {m.erreur or ''} |"
            )
        lignes.append("")
    lignes += [
        "## Lecture",
        "",
        "- Score de fidélité : plus il est haut, plus les extraits, mots-clés et chiffres de la fiche figurent dans le cours (voir fidelite.py).",
        "- Notions non justifiées et mots-clés absents : moins il y en a, mieux c'est (ce sont les inventions détectées).",
        "- Un fournisseur différent d'une version à l'autre (gemini puis groq) fausse la comparaison : refaire la mesure quand le quota est revenu.",
        "",
    ]
    chemin.write_text("\n".join(lignes), encoding="utf-8")


def _v(valeur: object) -> str:
    return "—" if valeur is None else str(valeur)


# ---------------------------------------------------------------- programme


def analyser(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Compare les prompts de la fiche (v1, v2, v3) sur des cours de test.")
    parser.add_argument("--cours", type=Path, default=COURS_PAR_DEFAUT, help="dossier des cours de test (*.txt)")
    parser.add_argument("--versions", nargs="+", default=VERSIONS_PAR_DEFAUT, help="versions du prompt à comparer")
    parser.add_argument("--sortie", type=Path, default=SORTIE_PAR_DEFAUT, help="dossier des résultats (CSV et Markdown)")
    parser.add_argument("--dry-run", action="store_true", help="faux LLM : aucun appel réel, aucune confirmation, chiffres sans valeur")
    parser.add_argument("--oui", action="store_true", help="confirme à l'avance la consommation de quota (sinon : question posée)")
    return parser.parse_args(argv)


async def executer(args: argparse.Namespace) -> list[Mesure]:
    reglages = get_cours_ai_settings()
    settings = get_settings()
    modele = settings.llm_model or "stub"
    cours = lire_cours(args.cours)
    mesures: list[Mesure] = []
    client_reel: LLMClient | None = None
    for nom, texte in cours.items():
        for version in args.versions:
            if args.dry_run:
                llm: LLMClient = LLMFactice(texte)
            else:
                if client_reel is None:
                    # Même fabrique que l'application : clé lue par le socle, jamais affichée ; même délai par appel (FICHE_TIMEOUT_SECONDS).
                    client_reel = construire_client(settings, reglages.fiche_timeout_seconds)
                llm = client_reel
            print(f"  {nom} · {version} …", flush=True)
            mesures.append(await mesurer(nom, version, texte, llm, reglages, modele, args.dry_run))
    return mesures


def main(argv: list[str] | None = None) -> int:
    args = analyser(argv)
    logging.getLogger("bawsla").setLevel(logging.WARNING)
    versions = valider_versions(args.versions)
    cours = lire_cours(args.cours)
    if not cours:
        print(f"Aucun fichier .txt dans {args.cours}.", file=sys.stderr)
        return 2
    reglages = get_cours_ai_settings()
    appels = sum(nombre_appels(texte, reglages) for texte in cours.values()) * len(versions)
    print(f"{len(cours)} cours × {len(versions)} versions ({', '.join(versions)}) = {len(cours) * len(versions)} fiches, environ {appels} appel(s) au LLM"
          f" (jusqu'à {2 * appels} si une réponse JSON doit être redemandée).")

    if args.dry_run:
        print("Mode --dry-run : faux LLM, aucun quota consommé.")
    else:
        settings = get_settings()
        print(f"Fournisseur configuré : {settings.llm_provider}, modèle : {settings.llm_model or '(aucun)'}, secours : {settings.llm_secours or 'aucun'}.")
        if not args.oui:
            reponse = input("Cela consomme du quota. Continuer ? (o/N) ").strip().lower()
            if reponse not in ("o", "oui", "y", "yes"):
                print("Annulé : rien n'a été envoyé.")
                return 1

    mesures = asyncio.run(executer(args))
    args.sortie.mkdir(parents=True, exist_ok=True)
    horodatage = datetime.now().strftime("%Y-%m-%d_%H%M%S")
    suffixe = "_dry-run" if args.dry_run else ""
    csv_chemin = args.sortie / f"fiche_{horodatage}{suffixe}.csv"
    md_chemin = args.sortie / f"fiche_{horodatage}{suffixe}.md"
    modele = "faux-llm" if args.dry_run else (get_settings().llm_model or "stub")
    ecrire_csv(mesures, csv_chemin)
    ecrire_markdown(mesures, md_chemin, dry_run=args.dry_run, versions=versions, reglages=reglages, modele=modele)
    print(f"Résultats : {csv_chemin}\n            {md_chemin}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
