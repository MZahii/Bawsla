package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Aide à la rédaction : « Améliorer ma question ». */
public record AmeliorerRequest(@NotNull(message = "Le cours est obligatoire") Long coursId,
                               @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String titre,
                               @Size(max = 20000) String contenu) {
}
