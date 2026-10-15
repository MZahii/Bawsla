package tn.esprit.bawsla.forum.dto;

import java.util.List;

/** « Publier comme question » : brouillon pré-rempli par l'IA, à relire avant publication. */
public record BrouillonQuestionResponse(Long coursId, Long conversationId, String titre, String contenu,
                                        List<String> etiquettes, List<SimilaireResponse> similaires) {
}
