"""Génération de QCM v3 : v2 + règles anti-ambiguïté et auto-contrôle avant de répondre.

Défauts observés avec v2 (rejets de la Self-Consistency, relecture des quiz générés) :
- un distracteur « à moitié vrai » : la vérification le choisissait et la question était rejetée ;
- des énoncés négatifs (« laquelle n'est PAS… ») lus à l'envers par la vérification ;
- une bonne réponse repérable sans connaître la notion (la plus longue, la plus nuancée, ou qui reprend
  un mot de l'énoncé) ;
- des calculs dont le résultat annoncé était faux.
v3 ajoute ces règles au système et demande au modèle de résoudre chaque question lui-même avant de l'écrire.
Variables du modèle : celles de v2 (le message utilisateur ne change que par sa dernière phrase).
"""

from app.modules.quiz.prompts import generation_v2

VERSION = "v3"

SYSTEME = generation_v2.SYSTEME.replace(
    "Le contenu entre balises <objectifs>",
    """Règles anti-ambiguïté (une question ambiguë sera rejetée par une vérification indépendante) :
- chaque distracteur est FAUX sans discussion possible pour un expert, même lu littéralement ; \
pas de choix « en partie vrai » ni vrai dans un cas particulier ;
- énoncé à la forme affirmative : pas de « ne… pas », « sauf », « laquelle est fausse » ;
- la bonne réponse ne se devine pas sans la notion : ni la plus longue, ni la seule nuancée, \
ni la seule qui reprend un mot de l'énoncé ; varie sa position parmi les 4 choix ;
- toute valeur numérique est recalculée : le bon choix est exact, chaque distracteur correspond \
à une erreur de calcul ou de raisonnement typique (celle de "erreurAssociee").

Avant d'écrire chaque question, résous-la toi-même sans regarder ta bonne réponse : si deux choix \
peuvent se défendre ou si ton résultat diffère, réécris la question. N'écris pas ce contrôle dans le JSON.

Le contenu entre balises <objectifs>""",
)

UTILISATEUR = generation_v2.UTILISATEUR.replace(
    "$deja Réponds en JSON.",
    "$deja Vérifie chaque question avec les règles anti-ambiguïté avant de répondre en JSON.",
)
