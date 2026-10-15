# Chapitre 1 : Variables et types

En Python, une variable est un nom qui désigne un objet ; on ne déclare pas son type. L'instruction age = 20 associe le nom age à un entier. Les types de base sont int pour les entiers, float pour les nombres à virgule, str pour les chaînes de caractères et bool pour les booléens True et False. La fonction type(x) renvoie le type d'un objet.

Les chaînes sont immuables : on ne peut pas modifier un caractère en place, on crée une nouvelle chaîne. Une f-string comme f"Bonjour {nom}" insère la valeur d'une expression dans le texte. La conversion entre types est explicite : int("42") donne 42, str(3.5) donne "3.5", et int("abc") lève une exception ValueError.

# Chapitre 2 : Listes et dictionnaires

Une liste est une séquence ordonnée et modifiable, écrite entre crochets : notes = [12, 15, 9]. On accède à un élément par son indice, qui commence à 0 ; notes[-1] désigne le dernier élément. La méthode append ajoute un élément à la fin, et len(notes) donne la longueur.

Le découpage (slicing) notes[1:3] renvoie une nouvelle liste avec les éléments d'indice 1 et 2 : la borne de fin est exclue. Copier une liste avec b = a ne crée pas de copie : les deux noms désignent la même liste, et une modification de b se voit dans a. Pour une vraie copie, on écrit b = a.copy() ou b = list(a).

Un dictionnaire associe des clés à des valeurs : ages = {"Ali": 20, "Sara": 22}. Les clés doivent être immuables, par exemple des chaînes ou des nombres. ages["Ali"] lève une exception KeyError si la clé n'existe pas, alors que ages.get("Ali", 0) renvoie la valeur par défaut 0.

# Chapitre 3 : Conditions et boucles

L'instruction if teste une condition ; elif et else traitent les autres cas. L'indentation, en général quatre espaces, délimite les blocs : une erreur d'indentation provoque une IndentationError.

La boucle for parcourt les éléments d'une séquence : for note in notes. La fonction range(5) produit les entiers de 0 à 4 ; range(2, 10, 2) produit 2, 4, 6 et 8. La boucle while répète tant qu'une condition est vraie ; il faut que la condition finisse par devenir fausse, sinon la boucle est infinie. break sort de la boucle, continue passe directement à l'itération suivante.

La fonction enumerate(notes) donne à la fois l'indice et l'élément, ce qui évite de gérer un compteur à la main.

# Chapitre 4 : Fonctions

Une fonction se définit avec def et renvoie une valeur avec return ; sans return, elle renvoie None. Les paramètres peuvent avoir une valeur par défaut : def saluer(nom, poli=True).

Piège classique : une valeur par défaut mutable, comme une liste vide, est créée une seule fois, à la définition de la fonction. def ajouter(x, l=[]) réutilise la même liste à chaque appel. On écrit plutôt l=None, puis on crée la liste dans le corps de la fonction.

Une variable affectée dans une fonction est locale à cette fonction. Pour modifier une variable globale, il faut la déclarer avec le mot-clé global, ce qui est généralement à éviter : mieux vaut renvoyer une valeur.

# Chapitre 5 : Exceptions

Une exception interrompt l'exécution si elle n'est pas traitée. Le bloc try contient le code qui peut échouer, except traite une exception précise, else s'exécute s'il n'y a pas eu d'exception et finally s'exécute dans tous les cas, par exemple pour fermer une ressource.

On attrape les exceptions précises, comme ValueError ou KeyError, plutôt que toutes les exceptions avec un except nu, qui masquerait aussi les erreurs de programmation. L'instruction raise lève une exception, par exemple raise ValueError("note négative").

# Chapitre 6 : Compréhensions et fichiers

Une compréhension de liste construit une liste en une ligne : carres = [x * x for x in range(10) if x % 2 == 0] garde les carrés des nombres pairs. Il existe aussi des compréhensions de dictionnaire, comme {nom: len(nom) for nom in noms}.

Pour lire un fichier, on utilise with open("notes.txt", encoding="utf-8") as f : le bloc with ferme le fichier automatiquement, même en cas d'exception. Le mode "w" écrase le fichier, le mode "a" ajoute à la fin. Lire un fichier sans préciser l'encodage peut provoquer une UnicodeDecodeError avec des caractères accentués.
