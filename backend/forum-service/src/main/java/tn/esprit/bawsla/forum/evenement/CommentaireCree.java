package tn.esprit.bawsla.forum.evenement;

/** Nouveau commentaire (notification de l'auteur du message commenté, mentions). */
public record CommentaireCree(Long commentaireId,
                              Long discussionId,
                              Long reponseId,
                              Long coursId,
                              Long auteurId,
                              Long auteurCibleId,
                              String contenu) {
}
