package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corps de PUT /api/cours/{id}/pages/{numero} : le texte de la page corrigé par le formateur (vide accepté : page sans texte). */
public record CorrectionPageRequest(
        @NotNull(message = "Le texte est obligatoire")
        @Size(max = 100000, message = "100 000 caractères maximum")
        String texte) {
}
