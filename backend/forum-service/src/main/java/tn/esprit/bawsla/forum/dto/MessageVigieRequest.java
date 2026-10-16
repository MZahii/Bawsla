package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.entity.enums.ModeVigie;

/** Question posée en privé à La Vigie (mode DIRECTE, GUIDEE ou GENERAL). */
public record MessageVigieRequest(
        @NotBlank(message = "La question est obligatoire")
        @Size(max = 2000, message = "La question fait au plus 2000 caractères") String texte,
        @NotNull(message = "Le mode est obligatoire") ModeVigie mode) {
}
