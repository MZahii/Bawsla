package tn.esprit.bawsla.cours.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de PUT /api/cours/{id}/fiche : la fiche telle que le formateur l'a corrigée. */
public record FicheRequest(
        @NotBlank(message = "Le résumé est obligatoire")
        String resume,
        @NotNull(message = "La liste des mots-clés est obligatoire")
        @Size(max = 30, message = "30 mots-clés maximum")
        List<@NotBlank(message = "Un mot-clé ne peut pas être vide") @Size(max = 200, message = "200 caractères maximum") String> motsCles,
        @NotNull(message = "La liste des notions est obligatoire")
        @Size(max = 30, message = "30 notions maximum")
        List<@Valid NotionRequest> notions) {
}
