-- Exécuté une seule fois, à la première création du volume MySQL.
-- Une base par microservice. Aucune clé étrangère entre bases.
CREATE DATABASE IF NOT EXISTS bawsla_user  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS bawsla_cours CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS bawsla_quiz  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS bawsla_forum CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- L'utilisateur applicatif (MYSQL_USER) est créé par l'image officielle ;
-- on lui donne les droits sur les 4 bases.
GRANT ALL PRIVILEGES ON bawsla_user.*  TO 'bawsla'@'%';
GRANT ALL PRIVILEGES ON bawsla_cours.* TO 'bawsla'@'%';
GRANT ALL PRIVILEGES ON bawsla_quiz.*  TO 'bawsla'@'%';
GRANT ALL PRIVILEGES ON bawsla_forum.* TO 'bawsla'@'%';
FLUSH PRIVILEGES;
