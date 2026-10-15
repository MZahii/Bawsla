"""Exemples Few-Shot : 2 questions par niveau de Bloom, au format JSON attendu du LLM.

Seuls les exemples des niveaux DEMANDÉS sont insérés dans le prompt (prompt plus court, moins de dérive).
Les sujets sont volontairement variés et sans rapport avec le cours : l'exemple montre la FORME et le niveau
cognitif, pas le contenu. Aucune donnée personnelle.
"""

from app.modules.quiz.schemas import NiveauBloom


def _q(niveau: str, competence: str, enonce: str, bonne: str, mauvais: list[tuple[str, str]], explication: str) -> dict:
    choix = [{"texte": bonne, "estCorrecte": True}]
    choix += [{"texte": t, "estCorrecte": False, "erreurAssociee": e} for t, e in mauvais]
    return {"enonce": enonce, "niveauBloom": niveau, "competence": competence, "choix": choix, "explication": explication}


EXEMPLES: dict[NiveauBloom, list[dict]] = {
    NiveauBloom.MEMORISER: [
        _q(
            "MEMORISER", "Connaître les commandes Git de base",
            "Quelle commande Git enregistre les modifications indexées dans l'historique local ?",
            "git commit",
            [
                ("git push", "Confond l'enregistrement local avec l'envoi vers le dépôt distant."),
                ("git add", "Confond l'indexation des fichiers avec leur enregistrement."),
                ("git fetch", "Confond l'enregistrement avec la récupération depuis le distant."),
            ],
            "git commit crée un instantané des modifications indexées dans l'historique local ; push l'envoie ensuite.",
        ),
        _q(
            "MEMORISER", "Connaître les unités du Système international",
            "Quelle est l'unité SI de la force ?",
            "Le newton",
            [
                ("Le joule", "Confond la force avec l'énergie."),
                ("Le watt", "Confond la force avec la puissance."),
                ("Le pascal", "Confond la force avec la pression (force par surface)."),
            ],
            "Le newton (N) est l'unité de force : 1 N = 1 kg·m/s².",
        ),
    ],
    NiveauBloom.COMPRENDRE: [
        _q(
            "COMPRENDRE", "Expliquer le rôle d'un index en base de données",
            "Pourquoi un index accélère-t-il une recherche sur une colonne ?",
            "Il évite de parcourir toutes les lignes en gardant les valeurs triées dans une structure dédiée.",
            [
                ("Il compresse la table pour qu'elle soit lue plus vite.", "Confond indexation et compression."),
                ("Il copie la table en mémoire vive.", "Confond index et mise en cache."),
                ("Il supprime les doublons de la colonne.", "Confond index et contrainte d'unicité."),
            ],
            "Un index (souvent un arbre B) range les valeurs triées avec un pointeur vers chaque ligne : la recherche devient logarithmique.",
        ),
        _q(
            "COMPRENDRE", "Interpréter un taux de variation",
            "Un prix passe de 50 à 60. Que signifie une hausse de 20 % ?",
            "L'augmentation (10) représente 20 % du prix de départ (50).",
            [
                ("L'augmentation représente 20 % du prix final.", "Prend le prix final comme base du pourcentage."),
                ("Le prix a augmenté de 20 unités.", "Confond pourcentage et écart absolu."),
                ("Le prix final vaut 20 % du prix de départ.", "Confond taux de variation et rapport des valeurs."),
            ],
            "Taux de variation = (60 − 50) / 50 = 0,20 : la base est toujours la valeur de départ.",
        ),
    ],
    NiveauBloom.APPLIQUER: [
        _q(
            "APPLIQUER", "Calculer une moyenne pondérée",
            "Notes 12 (coefficient 1) et 15 (coefficient 2). Quelle est la moyenne pondérée ?",
            "14",
            [
                ("13,5", "Calcule la moyenne simple sans tenir compte des coefficients."),
                ("27", "Additionne les notes sans diviser."),
                ("9", "Divise la somme des notes non pondérées (27) par la somme des coefficients (3)."),
            ],
            "(12 × 1 + 15 × 2) / (1 + 2) = 42 / 3 = 14.",
        ),
        _q(
            "APPLIQUER", "Écrire une requête SQL de filtrage",
            "Quelle requête renvoie les produits dont le prix dépasse 100 ?",
            "SELECT * FROM produit WHERE prix > 100;",
            [
                ("SELECT * FROM produit HAVING prix > 100;", "Utilise HAVING, réservé aux groupes, à la place de WHERE."),
                ("SELECT * FROM produit WHERE prix >= 100;", "Inclut la valeur 100 alors que « dépasse » l'exclut."),
                ("SELECT prix > 100 FROM produit;", "Place la condition dans la liste des colonnes au lieu de filtrer."),
            ],
            "WHERE filtre les lignes avant tout regroupement ; « dépasse » se traduit par l'opérateur strict >.",
        ),
    ],
    NiveauBloom.ANALYSER: [
        _q(
            "ANALYSER", "Identifier la cause d'un bogue",
            "Une boucle for (i = 0; i <= n; i++) lit un tableau de n éléments et plante à la fin. Quelle est la cause ?",
            "La condition i <= n fait lire une case de trop (indice n).",
            [
                ("Le tableau n'est pas trié.", "Attribue l'erreur à l'ordre des données plutôt qu'aux bornes."),
                ("i devrait commencer à 1.", "Croit que les indices commencent à 1."),
                ("La boucle ne s'arrête jamais.", "Confond un dépassement d'indice et une boucle infinie."),
            ],
            "Les indices valides vont de 0 à n − 1 ; avec <=, la dernière itération lit l'indice n, hors du tableau.",
        ),
        _q(
            "ANALYSER", "Distinguer corrélation et causalité",
            "Les villes qui ont plus de pompiers ont plus d'incendies. Quelle analyse est la plus juste ?",
            "La taille de la ville influence les deux : la corrélation ne prouve pas que les pompiers causent les incendies.",
            [
                ("Les pompiers provoquent des incendies.", "Conclut à une causalité à partir d'une simple corrélation."),
                ("Il faut réduire le nombre de pompiers.", "Tire une décision d'une causalité non démontrée."),
                ("Les deux chiffres n'ont aucun lien.", "Nie une corrélation pourtant observée."),
            ],
            "Une variable cachée (la taille de la ville) explique les deux séries : corrélation n'est pas causalité.",
        ),
    ],
    NiveauBloom.EVALUER: [
        _q(
            "EVALUER", "Choisir une structure de données adaptée",
            "On doit vérifier très souvent si un identifiant existe parmi un million. Quel choix est le plus pertinent ?",
            "Un ensemble à hachage, car la recherche y est en temps constant en moyenne.",
            [
                ("Une liste chaînée, car l'ajout est rapide.", "Juge sur l'ajout alors que le besoin porte sur la recherche."),
                ("Un tableau non trié, car il prend peu de place.", "Privilégie la mémoire au détriment d'une recherche linéaire."),
                ("Une pile, car elle est simple.", "Choisit sur la simplicité sans rapport avec le besoin."),
            ],
            "Le critère décisif est la recherche fréquente : le hachage offre O(1) en moyenne, contre O(n) pour une liste.",
        ),
        _q(
            "EVALUER", "Juger la fiabilité d'une source",
            "Pour un exposé sur un médicament, quelle source est la plus fiable ?",
            "Une revue scientifique à comité de lecture.",
            [
                ("Un forum de patients.", "Accorde la même valeur à des témoignages qu'à des études vérifiées."),
                ("La publicité du laboratoire.", "Ignore le conflit d'intérêts de la source."),
                ("Une vidéo très populaire.", "Confond popularité et fiabilité."),
            ],
            "La relecture par des pairs et la méthode publiée rendent la revue scientifique plus fiable que les autres sources.",
        ),
    ],
    NiveauBloom.CREER: [
        _q(
            "CREER", "Concevoir un schéma de base de données",
            "On conçoit une base où un auteur écrit plusieurs livres et un livre peut avoir plusieurs auteurs. Quelle conception convient ?",
            "Une table auteur, une table livre et une table d'association auteur_livre.",
            [
                ("Une colonne auteur_id dans la table livre.", "Ne permet qu'un seul auteur par livre (relation 1-N)."),
                ("Une seule table avec tous les champs.", "Duplique les données et ignore la normalisation."),
                ("Une colonne livres contenant une liste d'identifiants.", "Stocke plusieurs valeurs dans une colonne (non atomique)."),
            ],
            "Une relation plusieurs-à-plusieurs se modélise par une table d'association portant les deux clés.",
        ),
        _q(
            "CREER", "Élaborer un plan d'expérience",
            "On veut tester si un engrais fait pousser les plantes plus vite. Quel protocole est le mieux conçu ?",
            "Deux groupes de plantes identiques, un seul reçoit l'engrais, toutes les autres conditions égales.",
            [
                ("Donner l'engrais à toutes les plantes et mesurer.", "Oublie le groupe témoin nécessaire à la comparaison."),
                ("Comparer des espèces différentes avec et sans engrais.", "Introduit une seconde variable (l'espèce)."),
                ("Mesurer une seule plante avant et après.", "Échantillon trop petit pour conclure."),
            ],
            "Un protocole valide ne fait varier qu'un facteur et compare à un groupe témoin.",
        ),
    ],
}


def exemples_pour(niveaux: list[NiveauBloom]) -> list[dict]:
    """Les 2 exemples de chaque niveau demandé, dans l'ordre de la taxonomie."""
    return [ex for niveau in NiveauBloom if niveau in niveaux for ex in EXEMPLES[niveau]]
