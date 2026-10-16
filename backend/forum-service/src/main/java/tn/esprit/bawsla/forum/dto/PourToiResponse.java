package tn.esprit.bawsla.forum.dto;

import java.util.List;

/**
 * Section « Pour toi » : fils résolus liés aux notions ratées dans les quiz (contrat 3).
 * disponible = false quand quiz-service ne répond pas (la section est alors masquée).
 */
public record PourToiResponse(boolean disponible, List<Notion> notions) {

    public record Notion(String notion, Long coursId, List<DiscussionResume> discussions) {
    }
}
