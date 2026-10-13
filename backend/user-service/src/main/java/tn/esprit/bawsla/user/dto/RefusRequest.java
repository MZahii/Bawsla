package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Refus d'une demande formateur : le motif est montré au demandeur. */
public record RefusRequest(
        @NotBlank(message = "Le motif du refus est obligatoire") @Size(max = 500, message = "500 caractères maximum")
        String motif) {
}
