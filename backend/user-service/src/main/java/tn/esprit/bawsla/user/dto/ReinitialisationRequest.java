package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nouveau mot de passe choisi depuis le lien reçu par email. */
public record ReinitialisationRequest(
        @NotBlank(message = "Le lien de réinitialisation est incomplet")
        @Size(max = 100, message = "Le lien de réinitialisation est incomplet")
        String token,
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = MotDePasse.MIN, max = MotDePasse.MAX, message = MotDePasse.TAILLE_MESSAGE)
        @Pattern(regexp = MotDePasse.REGEX, message = MotDePasse.REGEX_MESSAGE)
        String motDePasse) {
}
