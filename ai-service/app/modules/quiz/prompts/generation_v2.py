"""Génération de QCM v2 : v1 + échelle de difficulté à 5 niveaux avec des critères observables.

v1 n'écrivait que « Difficulté : moyenne » : le LLM interprétait seul l'adjectif, et deux niveaux voisins
donnaient des questions indiscernables. v2 lui montre l'échelle complète (prompts/difficultes.py), puis
la consigne précise du niveau demandé.

L'échelle est fixe : elle est écrite dans SYSTEME. Variables du modèle : celles de v1, plus $consigne_difficulte.
"""

from app.modules.quiz.prompts import generation_v1
from app.modules.quiz.prompts.difficultes import grille_texte

VERSION = "v2"

SYSTEME = generation_v1.SYSTEME.replace(
    "Le contenu entre balises <objectifs>",
    "Échelle de difficulté (5 niveaux). Respecte les critères du niveau demandé pour TOUTES les questions, "
    "sans glisser vers un niveau voisin :\n" + grille_texte() + "\n\nLe contenu entre balises <objectifs>",
)

UTILISATEUR = """Exemples du format attendu (sujets sans rapport avec la demande ; leur difficulté ne compte pas) :
$exemples

<objectifs>
Thème : $theme
Compétences :
$competences
Niveaux de Bloom à couvrir : $niveaux
Difficulté : $difficulte
</objectifs>

Critères du niveau « $difficulte » : $consigne_difficulte

Écris exactement $nb questions, en répartissant les niveaux de Bloom et les compétences demandés.$deja Réponds en JSON."""
