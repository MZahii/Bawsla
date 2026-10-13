package tn.esprit.bawsla.user.dto;

import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.User;

/**
 * Contrat GET /api/users/{id} : infos PUBLIQUES uniquement (pas d'email).
 * formateurValide : true si l'admin a validé ce formateur (cours-service s'en sert avant une publication).
 */
public record UserPublicDto(Long id, String nom, String prenom, Role role, boolean formateurValide) {

    public static UserPublicDto from(User u) {
        return new UserPublicDto(u.getId(), u.getNom(), u.getPrenom(), u.getRole(), u.isFormateurValide());
    }
}
