package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.user.entity.Role;

/** role : ETUDIANT (défaut) ou ENSEIGNANT. ADMIN est refusé à l'inscription. */
public record RegisterRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String prenom,
        @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") @Size(max = 150) String email,
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Entre 8 et 72 caractères") String motDePasse,
        Role role) {
}
