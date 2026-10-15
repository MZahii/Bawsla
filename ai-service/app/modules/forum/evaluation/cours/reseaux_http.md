# Chapitre 1 : Le modèle client-serveur

Sur le web, un client, le plus souvent un navigateur, envoie une requête à un serveur, qui renvoie une réponse. HTTP est un protocole sans état : chaque requête est traitée indépendamment des précédentes, et le serveur ne se souvient pas du client d'une requête à l'autre.

Avant d'envoyer la requête, le client traduit le nom de domaine en adresse IP grâce au DNS. Le serveur DNS renvoie par exemple l'adresse 93.184.216.34 pour un nom de domaine ; la réponse est gardée en cache pendant une durée appelée TTL.

# Chapitre 2 : Les méthodes HTTP

GET demande une ressource et ne doit pas modifier l'état du serveur. POST envoie des données pour créer une ressource ou déclencher un traitement. PUT remplace une ressource entière, PATCH la modifie partiellement et DELETE la supprime.

Une méthode est idempotente si l'appeler plusieurs fois a le même effet que l'appeler une fois : GET, PUT et DELETE sont idempotentes, POST ne l'est pas. C'est pourquoi un navigateur demande une confirmation avant de renvoyer un formulaire POST quand on actualise la page.

# Chapitre 3 : Les codes de statut

Le code de statut indique le résultat de la requête. Les codes 2xx signalent un succès : 200 OK, 201 Created après une création. Les codes 3xx signalent une redirection, comme 301 Moved Permanently. Les codes 4xx signalent une erreur du client : 400 Bad Request pour une requête mal formée, 404 Not Found pour une ressource absente.

La différence entre 401 et 403 est souvent mal comprise : 401 Unauthorized signifie que le client n'est pas authentifié, alors que 403 Forbidden signifie qu'il est authentifié mais n'a pas le droit d'accéder à la ressource. Les codes 5xx signalent une erreur du serveur, comme 500 Internal Server Error ou 503 Service Unavailable quand le serveur est surchargé.

# Chapitre 4 : En-têtes, cookies et sessions

Les en-têtes HTTP transportent des métadonnées. Content-Type indique le format du corps, par exemple application/json. Authorization transporte les informations d'authentification, comme un jeton Bearer.

Comme HTTP est sans état, le serveur utilise des cookies pour reconnaître un client. Il envoie l'en-tête Set-Cookie, et le navigateur renvoie le cookie à chaque requête vers ce domaine. L'attribut HttpOnly empêche le JavaScript de lire le cookie, ce qui limite le vol de session par une faille XSS ; l'attribut Secure n'envoie le cookie que sur une connexion HTTPS.

# Chapitre 5 : HTTPS et TLS

HTTPS est HTTP chiffré avec TLS. Au début de la connexion, le serveur présente un certificat signé par une autorité de certification, qui prouve son identité. Le client et le serveur négocient ensuite une clé de session, et les échanges sont chiffrés.

TLS protège contre l'écoute et la modification des données en transit, mais pas contre un site malveillant qui possède un certificat valide. Le cadenas du navigateur prouve que la connexion est chiffrée, pas que le site est honnête.

# Chapitre 6 : Les API REST

Une API REST expose des ressources identifiées par des URL, comme /api/cours/5, et utilise les méthodes HTTP pour agir dessus. Les réponses sont le plus souvent en JSON.

Une bonne API REST utilise des noms au pluriel pour les collections, renvoie le bon code de statut et ne met pas de verbe dans l'URL : on écrit POST /api/commandes plutôt que /api/creerCommande. La pagination limite la taille des réponses, par exemple avec les paramètres page et taille.

Le navigateur bloque par défaut les requêtes JavaScript vers un autre domaine : c'est la politique de même origine. Le serveur peut les autoriser avec les en-têtes CORS, comme Access-Control-Allow-Origin.
