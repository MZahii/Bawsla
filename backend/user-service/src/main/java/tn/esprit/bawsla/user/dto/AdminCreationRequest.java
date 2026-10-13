package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.user.entity.Role;

/**
 * Création d'un compte par l'ADMIN (tous les rôles possibles). L'admin choisit le mot de passe initial
 * et le transmet lui-même : aucun mot de passe n'est généré ni renvoyé par l'API. actif : true par défaut.
 */
public record AdminCreationRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String prenom,
        @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") @Size(max = 150) String email,
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = MotDePasse.MIN, max = MotDePasse.MAX, message = MotDePasse.TAILLE_MESSAGE)
        @Pattern(regexp = MotDePasse.REGEX, message = MotDePasse.REGEX_MESSAGE)
        String motDePasse,
        @NotNull(message = "Le rôle est obligatoire") Role role,
        Boolean actif) {
}
