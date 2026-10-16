package tn.esprit.bawsla.forum.evenement;

/** Titre ou contenu modifié par l'auteur (nouvel embedding, nouvelle modération). */
public record DiscussionModifiee(Long discussionId,
                                 Long coursId) {
}
