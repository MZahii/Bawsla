package tn.esprit.bawsla.user.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;

/** Profil complet : réservé à l'utilisateur lui-même (/me) et à l'ADMIN. */
public record UserDto(Long id, String nom, String prenom, String email, Role role, boolean actif,
                      LocalDateTime dateCreation) {

    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole(), u.isActif(),
                u.getDateCreation());
    }
}
