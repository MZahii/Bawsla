package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Demande pour devenir formateur, envoyée depuis « Mon compte ». */
public record DemandeFormateurRequest(
        @NotBlank(message = "Indique ton domaine d'expertise") @Size(max = 200, message = "200 caractères maximum")
        String expertise,
        @Size(max = 1000, message = "1000 caractères maximum") String presentation,
        @Size(max = 300, message = "300 caractères maximum") String lien) {
}
