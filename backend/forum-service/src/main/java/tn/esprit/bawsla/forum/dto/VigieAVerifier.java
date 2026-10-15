package tn.esprit.bawsla.forum.dto;

/** Réponse de La Vigie dans un fil, vue par le formateur du cours dans son studio (onglet « Réponses de La Vigie »). */
public record VigieAVerifier(Long discussionId, String discussionTitre, VigieResponse reponse) {
}
