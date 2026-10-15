package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotionRequest(
        @NotBlank(message = "Le titre de la notion est obligatoire")
        @Size(max = 200, message = "200 caractères maximum")
        String titre,
        @NotBlank(message = "La définition est obligatoire")
        String definition,
        String extraitSource) {
}
