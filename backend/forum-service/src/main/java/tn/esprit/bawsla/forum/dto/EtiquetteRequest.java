package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Renommage d'une étiquette (admin). */
public record EtiquetteRequest(@NotBlank(message = "Le libellé est obligatoire")
                               @Size(max = 40, message = "Le libellé fait au plus 40 caractères") String libelle) {
}
