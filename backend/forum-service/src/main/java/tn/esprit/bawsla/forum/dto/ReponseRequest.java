package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReponseRequest(
        @NotBlank(message = "Le contenu est obligatoire")
        @Size(max = 20000, message = "La réponse fait au plus 20 000 caractères") String contenu,
        boolean anonyme) {
}
