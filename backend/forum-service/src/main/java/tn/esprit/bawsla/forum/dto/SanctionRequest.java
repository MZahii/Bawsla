package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tn.esprit.bawsla.forum.dto.validation.Motif;
import tn.esprit.bawsla.forum.entity.enums.TypeSanction;

/** Sanction motivée ; dureeHeures null = sans fin (24 h et 7 jours proposés à l'écran). */
public record SanctionRequest(@NotNull(message = "Le membre est obligatoire") Long userId,
                              @NotNull(message = "Le type est obligatoire") TypeSanction type,
                              @Min(value = 1, message = "Durée d'au moins 1 heure") Integer dureeHeures,
                              @Motif String motif,
                              Long signalementId) {
}
