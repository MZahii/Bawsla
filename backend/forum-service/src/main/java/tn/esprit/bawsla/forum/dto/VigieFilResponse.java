package tn.esprit.bawsla.forum.dto;

/**
 * La Vigie dans un fil : réponse principale (cours ou forum, absente si La Vigie est désactivée ou s'il ne s'agit
 * pas d'une question) et, à part, la réponse générale hors cours si l'apprenant l'a demandée. Jamais mélangées.
 * generaleDisponible : le bouton « Voir une réponse générale » peut être proposé.
 */
public record VigieFilResponse(VigieResponse principale, VigieResponse generale, boolean generaleDisponible) {
}
