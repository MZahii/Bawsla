package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.entity.enums.StatutDiscussion;

/** Discussion proche : proximite vaut TRES_PROCHE ou PROCHE (affichée en clair). */
public record SimilaireResponse(Long discussionId, String titre, String extrait, StatutDiscussion statut,
                                int nbReponses, double score, String proximite) {
}
