package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;

/** Sanction motivée ; dureeHeures null = sans fin (24 h et 7 jours proposés à l'écran). */
public record SanctionRequest(@NotNull(message = "Le membre est obligatoire") Long userId,
                              @NotNull(message = "Le type est obligatoire") TypeSanction type,
                              @Min(value = 1, message = "Durée d'au moins 1 heure") Integer dureeHeures,
                              @NotBlank(message = "Le motif est obligatoire")
                              @Size(max = 500, message = "Le motif fait au plus 500 caractères") String motif,
                              Long signalementId) {
}
