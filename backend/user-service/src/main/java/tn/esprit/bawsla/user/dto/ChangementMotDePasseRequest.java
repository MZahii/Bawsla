package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Changement de son propre mot de passe : l'ancien est exigé. */
public record ChangementMotDePasseRequest(
        @NotBlank(message = "Le mot de passe actuel est obligatoire") String ancienMotDePasse,
        @NotBlank(message = "Le nouveau mot de passe est obligatoire")
        @Size(min = MotDePasse.MIN, max = MotDePasse.MAX, message = MotDePasse.TAILLE_MESSAGE)
        @Pattern(regexp = MotDePasse.REGEX, message = MotDePasse.REGEX_MESSAGE)
        String nouveauMotDePasse) {
}
