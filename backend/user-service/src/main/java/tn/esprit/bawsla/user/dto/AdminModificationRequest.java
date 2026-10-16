package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.user.entity.Role;

/** Modification d'un compte par l'ADMIN. Le mot de passe se change à part (PUT /{id}/mot-de-passe). */
public record AdminModificationRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String prenom,
        @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") @Size(max = 150) String email,
        @NotNull(message = "Le rôle est obligatoire") Role role,
        @NotNull(message = "Le statut est obligatoire") Boolean actif) {
}
