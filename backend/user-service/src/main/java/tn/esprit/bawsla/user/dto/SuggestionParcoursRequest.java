package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corps de POST /api/users/me/parcours/suggestion : ce que l'apprenant veut apprendre, en une phrase. */
public record SuggestionParcoursRequest(
        @NotBlank(message = "Décris en quelques mots ce que tu veux apprendre")
        @Size(min = 3, max = 500, message = "Entre 3 et 500 caractères")
        String texte) {
}
