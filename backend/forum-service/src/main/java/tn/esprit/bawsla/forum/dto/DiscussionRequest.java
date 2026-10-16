package tn.esprit.bawsla.forum.dto;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

/**
 * Nouvelle discussion. confirmer = publier malgré l'avertissement « doublon probable » ou « données personnelles ».
 * conversationId : publication depuis l'assistant privé (la réponse de La Vigie est jointe au fil).
 */
public record DiscussionRequest(
        @NotNull(message = "Le cours est obligatoire") Long coursId,
        Long chapitreId,
        @Min(value = 1, message = "La page commence à 1") Integer page,
        @NotNull(message = "Le type est obligatoire") TypeDiscussion type,
        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 10, max = 200, message = "Le titre fait entre 10 et 200 caractères") String titre,
        @NotBlank(message = "Le contenu est obligatoire")
        @Size(max = 20000, message = "Le contenu fait au plus 20 000 caractères") String contenu,
        @Size(max = 5, message = "5 étiquettes au plus")
        List<@NotBlank @Size(max = 40, message = "Une étiquette fait au plus 40 caractères") String> etiquettes,
        boolean anonyme,
        boolean confirmer,
        Long conversationId) {
}
