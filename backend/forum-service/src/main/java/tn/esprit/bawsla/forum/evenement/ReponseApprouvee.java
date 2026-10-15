package tn.esprit.bawsla.forum.evenement;

/** Réponse approuvée (approuvee = true) ou approbation retirée par le formateur du cours. */
public record ReponseApprouvee(Long reponseId,
                               Long discussionId,
                               Long coursId,
                               Long auteurReponseId,
                               Long formateurId,
                               boolean approuvee) {
}
