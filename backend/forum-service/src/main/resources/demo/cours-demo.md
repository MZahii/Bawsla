# Chapitre 1 : Le modèle relationnel

Une base de données relationnelle range les données dans des tables. Chaque table a des colonnes typées et des lignes. La clé primaire identifie chaque ligne de manière unique : deux lignes d'une même table ne peuvent pas avoir la même valeur de clé primaire.

Une clé étrangère est une colonne qui contient la clé primaire d'une autre table. Elle relie les deux tables : par exemple, la colonne client_id de la table commande référence la colonne id de la table client. Le SGBD vérifie que chaque valeur de clé étrangère existe dans la table référencée (contrainte d'intégrité référentielle).

# Chapitre 2 : Interroger une table avec SELECT

La requête SELECT choisit les colonnes à afficher, FROM indique la table et WHERE filtre les lignes. La clause WHERE est évaluée ligne par ligne : seules les lignes pour lesquelles la condition est vraie sont gardées.

ORDER BY trie le résultat, et LIMIT restreint le nombre de lignes renvoyées. GROUP BY regroupe les lignes qui ont la même valeur, et les fonctions d'agrégation (COUNT, SUM, AVG) calculent une valeur par groupe. HAVING filtre les groupes après l'agrégation, alors que WHERE filtre les lignes avant.

# Chapitre 3 : Les jointures

Une jointure combine les lignes de deux tables selon une condition écrite après le mot-clé ON.

## INNER JOIN

La jointure interne (INNER JOIN) ne garde que les lignes qui ont une correspondance dans les deux tables. Un client sans commande n'apparaît donc pas dans le résultat d'un INNER JOIN entre client et commande.

## LEFT JOIN

La jointure externe gauche (LEFT JOIN) garde toutes les lignes de la table de gauche, même celles qui n'ont aucune correspondance dans la table de droite. Pour ces lignes, les colonnes de la table de droite valent NULL.

Attention : une condition sur la table de droite placée dans la clause WHERE est appliquée après la jointure. Elle élimine les lignes où la colonne de droite vaut NULL, c'est-à-dire précisément les lignes sans correspondance. Le LEFT JOIN se comporte alors comme un INNER JOIN. Pour filtrer la table de droite sans perdre les lignes de gauche, il faut placer la condition dans la clause ON de la jointure.

Exemple : SELECT c.nom, co.total FROM client c LEFT JOIN commande co ON co.client_id = c.id AND co.total > 100 garde tous les clients, et n'associe que les commandes de plus de 100.

## RIGHT JOIN et FULL JOIN

La jointure externe droite (RIGHT JOIN) est le symétrique du LEFT JOIN : elle garde toutes les lignes de la table de droite. On peut toujours la réécrire en LEFT JOIN en inversant l'ordre des tables. La jointure externe complète (FULL OUTER JOIN) garde les lignes des deux tables ; MySQL ne la propose pas directement, on l'obtient avec l'union d'un LEFT JOIN et d'un RIGHT JOIN.

## Doublons après une jointure

Si une ligne de gauche correspond à plusieurs lignes de droite, elle apparaît plusieurs fois dans le résultat. Un client qui a trois commandes produit trois lignes. Pour compter les clients, on utilise COUNT(DISTINCT c.id) plutôt que COUNT(*).

# Chapitre 4 : Les index

Un index accélère la recherche des lignes selon une ou plusieurs colonnes, comme l'index d'un livre. Il ralentit en contrepartie les insertions et les mises à jour, car il doit être maintenu.

Un index composite porte sur plusieurs colonnes, dans un ordre précis : CREATE INDEX idx_commande ON commande (client_id, date_commande). Il sert aux requêtes qui filtrent sur la première colonne, ou sur la première puis la deuxième, mais pas sur la deuxième seule. La commande EXPLAIN montre si une requête utilise un index.

# Chapitre 5 : Les transactions

Une transaction regroupe plusieurs opérations qui réussissent ou échouent ensemble. Elle respecte les propriétés ACID : atomicité, cohérence, isolation et durabilité. START TRANSACTION la commence, COMMIT la valide et ROLLBACK annule toutes ses modifications.
