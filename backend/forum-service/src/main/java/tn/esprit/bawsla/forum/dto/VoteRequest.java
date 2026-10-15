package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tn.esprit.bawsla.forum.entity.enums.CibleType;

/** Vote +1 / −1 ; valeur 0 = annuler son vote. */
public record VoteRequest(@NotNull(message = "La cible est obligatoire") CibleType cibleType,
                          @NotNull(message = "La cible est obligatoire") Long cibleId,
                          @Min(value = -1, message = "Valeur : -1, 0 ou 1")
                          @Max(value = 1, message = "Valeur : -1, 0 ou 1") int valeur) {
}
