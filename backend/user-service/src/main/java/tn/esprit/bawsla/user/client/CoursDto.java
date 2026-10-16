package tn.esprit.bawsla.user.client;

/** Champs garantis du contrat 1 (cours-service) ; les autres champs de la réponse sont ignorés. */
public record CoursDto(Long id, String titre, String categorie, String niveau, String statut, Long enseignantId) {
}
