package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** Page « Mon activité ». */
public record ActiviteResponse(List<DiscussionResume> questions, List<DiscussionResume> reponses,
                               List<DiscussionResume> favoris, List<DiscussionResume> suivis,
                               ReputationResponse reputation) {
}
