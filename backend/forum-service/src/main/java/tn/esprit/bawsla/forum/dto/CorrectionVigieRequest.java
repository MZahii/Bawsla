package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.dto.validation.Motif;

/** Correction motivée du formateur : le texte d'origine est conservé, le motif est tracé dans le journal. */
public record CorrectionVigieRequest(
        @NotBlank(message = "Le texte corrigé est obligatoire")
        @Size(max = 10000, message = "Le texte fait au plus 10 000 caractères") String texte,
        @Motif String motif) {
}
