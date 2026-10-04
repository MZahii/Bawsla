package tn.esprit.bawsla.user.dto;

import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;

/** Contrat GET /api/users/{id} : infos PUBLIQUES uniquement (pas d'email). */
public record UserPublicDto(Long id, String nom, String prenom, Role role) {

    public static UserPublicDto from(User u) {
        return new UserPublicDto(u.getId(), u.getNom(), u.getPrenom(), u.getRole());
    }
}
