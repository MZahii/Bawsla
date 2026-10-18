"""Remédiation v3 (IA 2) : v1 + rattrapage qui piège exactement la confusion diagnostiquée.

Défauts observés avec v1 :
- la question de rattrapage visait parfois la notion en général, pas la confusion de l'apprenant :
  il pouvait la réussir sans avoir corrigé son erreur ;
- l'explication donnait déjà la réponse du rattrapage ;
- le conseil restait vague (« relis le cours »).
v3 impose qu'un distracteur du rattrapage reproduise la confusion diagnostiquée, interdit de révéler
la réponse du rattrapage et demande un conseil actionnable en moins de 10 minutes.
Variables et format JSON : ceux de v1.
"""

from app.modules.quiz.prompts import remediation_v1

VERSION = "v3"

SYSTEME = remediation_v1.SYSTEME.replace(
    "Le contenu entre balises <reponse_apprenant>",
    """Exigences supplémentaires :
- "raisonnement" se termine par une phrase « Confusion retenue : … » qui nomme la confusion en une ligne ;
- "explication" ne donne ni la réponse ni un indice direct de la question de rattrapage ;
- "conseil" est une action précise faisable en moins de 10 minutes (un exercice, une règle à réciter, \
un contre-exemple à construire), jamais « relis le cours » ;
- dans "questionRattrapage", UN des mauvais choix reproduit exactement la confusion retenue, et son \
"erreurAssociee" la nomme ; les autres distracteurs sont faux sans discussion possible ;
- résous toi-même la question de rattrapage avant de l'écrire : une seule réponse doit se défendre.

Le contenu entre balises <reponse_apprenant>""",
)

UTILISATEUR = remediation_v1.UTILISATEUR
