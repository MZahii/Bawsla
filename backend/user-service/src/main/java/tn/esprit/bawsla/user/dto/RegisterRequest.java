package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.user.entity.Role;

/**
 * role : ETUDIANT (défaut) ou ENSEIGNANT. ADMIN est refusé à l'inscription.
 * expertise, presentation, lien : facultatifs, lus seulement pour ENSEIGNANT ; si expertise est renseignée,
 * une demande formateur EN_ATTENTE est créée avec le compte.
 */
public record RegisterRequest(
        @NotBlank(message = "Le nom est obligatoire") @Size(max = 100) String nom,
        @NotBlank(message = "Le prénom est obligatoire") @Size(max = 100) String prenom,
        @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") @Size(max = 150) String email,
        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = MotDePasse.MIN, max = MotDePasse.MAX, message = MotDePasse.TAILLE_MESSAGE)
        @Pattern(regexp = MotDePasse.REGEX, message = MotDePasse.REGEX_MESSAGE)
        String motDePasse,
        Role role,
        @Size(max = 200, message = "200 caractères maximum") String expertise,
        @Size(max = 1000, message = "1000 caractères maximum") String presentation,
        @Size(max = 300, message = "300 caractères maximum") String lien) {

    /** Inscription sans profil de formateur (compatibilité des appels existants). */
    public RegisterRequest(String nom, String prenom, String email, String motDePasse, Role role) {
        this(nom, prenom, email, motDePasse, role, null, null, null);
    }
}
