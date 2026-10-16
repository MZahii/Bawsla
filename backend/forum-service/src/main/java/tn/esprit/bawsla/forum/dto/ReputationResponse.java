package tn.esprit.bawsla.forum.dto;

import java.util.List;

import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;

public record ReputationResponse(int points, NiveauReputation niveau, String niveauLibelle,
                                 NiveauReputation prochainNiveau, Integer seuilProchainNiveau,
                                 List<EvenementReputationResponse> historique, List<BadgeResponse> badges) {
}
