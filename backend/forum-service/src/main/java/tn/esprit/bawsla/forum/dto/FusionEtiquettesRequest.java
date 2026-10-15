package tn.esprit.bawsla.forum.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** Fusion d'étiquettes : les sources sont remplacées par la cible puis supprimées. */
public record FusionEtiquettesRequest(@NotEmpty(message = "Aucune étiquette à fusionner") List<Long> sourceIds,
                                      @NotNull(message = "L'étiquette cible est obligatoire") Long cibleId) {
}
