package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ce que l'utilisateur peut modifier lui-même. L'email et le rôle ne se changent pas ici. */
public record ProfilRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String prenom,
        @Size(max = 500, message = "500 caractères maximum") String bio) {
}
