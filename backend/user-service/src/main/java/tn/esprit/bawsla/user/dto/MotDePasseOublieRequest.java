package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Demande de lien de réinitialisation (« mot de passe oublié »). */
public record MotDePasseOublieRequest(
        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Cet email n'est pas valide")
        String email) {
}
