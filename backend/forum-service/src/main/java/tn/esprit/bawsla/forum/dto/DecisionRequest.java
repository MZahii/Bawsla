package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;
import tn.esprit.bawsla.forum.dto.validation.Motif;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

/** Décision sur un signalement ; le motif est obligatoire et tracé dans le journal. */
public record DecisionRequest(@NotNull(message = "La décision est obligatoire") StatutSignalement statut,
                              boolean masquer,
                              boolean escalader,
                              @Motif String motif) {
}
