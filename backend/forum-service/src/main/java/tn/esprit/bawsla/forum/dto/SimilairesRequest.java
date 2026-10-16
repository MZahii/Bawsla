package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Questions similaires en direct pendant la saisie du titre (15 caractères au moins). */
public record SimilairesRequest(
        @NotNull(message = "Le cours est obligatoire") Long coursId,
        @NotBlank(message = "Le texte est obligatoire")
        @Size(min = 15, max = 1000, message = "Le texte fait entre 15 et 1000 caractères") String texte,
        Long exclureId) {
}
