package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

/** Décision sur un signalement ; le motif est obligatoire et tracé dans le journal. */
public record DecisionRequest(@NotNull(message = "La décision est obligatoire") StatutSignalement statut,
                              boolean masquer,
                              boolean escalader,
                              @NotBlank(message = "Le motif est obligatoire")
                              @Size(max = 500, message = "Le motif fait au plus 500 caractères") String motif) {
}
