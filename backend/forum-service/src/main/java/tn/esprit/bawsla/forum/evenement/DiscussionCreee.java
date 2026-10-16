package tn.esprit.bawsla.forum.evenement;

import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/** Nouvelle discussion publiée (abonnement de l'auteur, badges, embedding, La Vigie, modération). */
public record DiscussionCreee(Long discussionId,
                              Long coursId,
                              Long auteurId,
                              TypeDiscussion type,
                              boolean anonyme) {
}
