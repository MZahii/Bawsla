/**
 * Seule URL connue du front : l'api-gateway. Aucune clé API, aucun secret ici
 * (tout ce qui est dans le front est lisible par n'importe quel utilisateur).
 * URL relative : nginx (Docker, nginx.conf) ou le proxy de `ng serve` (proxy.conf.json) la transmettent à la gateway,
 * ce qui permet d'ouvrir la démo depuis une autre machine que celle qui héberge la stack.
 */
export const API_BASE_URL = '/api';
