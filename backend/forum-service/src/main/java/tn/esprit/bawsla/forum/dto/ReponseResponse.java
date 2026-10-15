package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tn.esprit.bawsla.forum.entity.enums.TypeReaction;

/** Réponse humaine, avec ses votes, réactions, commentaires et droits. */
public record ReponseResponse(Long id, Long discussionId, String contenu, AuteurResponse auteur, int score,
                              int monVote, boolean acceptee, boolean approuvee, AuteurResponse approuveePar,
                              LocalDateTime approuveeLe, boolean masquee, Map<TypeReaction, Long> reactions,
                              Set<TypeReaction> mesReactions, List<CommentaireResponse> commentaires,
                              LocalDateTime creeLe, LocalDateTime modifieLe, DroitsResponse droits) {
}
