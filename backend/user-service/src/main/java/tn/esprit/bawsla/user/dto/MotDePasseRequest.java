package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nouveau mot de passe défini par l'ADMIN pour un compte. */
public record MotDePasseRequest(
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = MotDePasse.MIN, max = MotDePasse.MAX, message = MotDePasse.TAILLE_MESSAGE)
        @Pattern(regexp = MotDePasse.REGEX, message = MotDePasse.REGEX_MESSAGE)
        String motDePasse) {
}
