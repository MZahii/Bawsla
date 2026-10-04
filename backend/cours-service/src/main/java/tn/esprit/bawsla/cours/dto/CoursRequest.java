package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.cours.entity.Niveau;

public record CoursRequest(
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "200 caractères maximum")
        String titre,
        String description,
        @Size(max = 100, message = "100 caractères maximum")
        String categorie,
        Niveau niveau,
        String fichierPdf) {
}
