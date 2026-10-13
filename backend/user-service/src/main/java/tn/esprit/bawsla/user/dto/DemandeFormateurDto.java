package tn.esprit.bawsla.user.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.user.entity.DemandeFormateur;
import tn.esprit.bawsla.user.entity.Role;
import tn.esprit.bawsla.user.entity.StatutDemande;
import tn.esprit.bawsla.user.entity.User;

/** Une demande formateur, vue par le demandeur lui-même ou par l'ADMIN (d'où l'email). */
public record DemandeFormateurDto(Long id, StatutDemande statut, String expertise, String presentation, String lien,
                                  String motifRefus, LocalDateTime dateCreation, LocalDateTime dateTraitement,
                                  Demandeur demandeur) {

    public record Demandeur(Long id, String nom, String prenom, String email, Role role, Long avatarVersion) {
    }

    public static DemandeFormateurDto from(DemandeFormateur d) {
        User u = d.getUtilisateur();
        return new DemandeFormateurDto(d.getId(), d.getStatut(), d.getExpertise(), d.getPresentation(), d.getLien(),
                d.getMotifRefus(), d.getDateCreation(), d.getDateTraitement(),
                new Demandeur(u.getId(), u.getNom(), u.getPrenom(), u.getEmail(), u.getRole(), u.getAvatarVersion()));
    }
}
