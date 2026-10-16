package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentaireRequest(
        @NotBlank(message = "Le commentaire est obligatoire")
        @Size(max = 600, message = "Un commentaire fait au plus 600 caractères") String contenu) {
}
