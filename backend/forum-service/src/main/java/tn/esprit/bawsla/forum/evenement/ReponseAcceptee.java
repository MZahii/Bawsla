package tn.esprit.bawsla.forum.evenement;

/** Meilleure réponse choisie (reponseId) ou retirée (reponseId null) ; ancienneReponseId = choix précédent. */
public record ReponseAcceptee(Long discussionId,
                              Long coursId,
                              Long reponseId,
                              Long auteurReponseId,
                              Long ancienneReponseId,
                              Long ancienAuteurId,
                              Long accepteurId,
                              Long auteurDiscussionId) {
}
