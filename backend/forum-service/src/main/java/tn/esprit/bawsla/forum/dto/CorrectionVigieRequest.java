package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Correction du formateur : le texte d'origine est conservé. */
public record CorrectionVigieRequest(
        @NotBlank(message = "Le texte corrigé est obligatoire")
        @Size(max = 10000, message = "Le texte fait au plus 10 000 caractères") String texte) {
}
