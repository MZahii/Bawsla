package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DiscussionRequest(
        @NotBlank(message = "Le titre est obligatoire") @Size(max = 200) String titre,
        @NotBlank(message = "Le contenu est obligatoire") String contenu,
        @NotNull(message = "Le cours est obligatoire") Long coursId) {
}
