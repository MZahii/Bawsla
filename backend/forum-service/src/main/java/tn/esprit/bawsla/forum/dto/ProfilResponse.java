package tn.esprit.bawsla.forum.dto;

import java.util.List;

import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;

/** Carte de survol d'un auteur. */
public record ProfilResponse(Long userId, String nom, String role, NiveauReputation niveau, String niveauLibelle,
                             int points, List<BadgeResponse> badges, List<String> etiquettesFavorites,
                             long nbQuestions, long nbReponses, long nbReponsesAcceptees) {
}
