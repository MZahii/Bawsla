package tn.esprit.bawsla.forum.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Édition par l'auteur : le cours et le type ne changent pas (déplacement = modération). */
public record DiscussionModificationRequest(
        @NotBlank(message = "Le titre est obligatoire")
        @Size(min = 10, max = 200, message = "Le titre fait entre 10 et 200 caractères") String titre,
        @NotBlank(message = "Le contenu est obligatoire")
        @Size(max = 20000, message = "Le contenu fait au plus 20 000 caractères") String contenu,
        @Size(max = 5, message = "5 étiquettes au plus")
        List<@NotBlank @Size(max = 40) String> etiquettes) {
}
