package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.entity.enums.NiveauReputation;

/**
 * Auteur affiché à côté d'un message. Pour un message anonyme vu par un autre apprenant, id et nom sont absents
 * (champs null omis) et anonyme vaut true : l'auteurId n'est jamais renvoyé.
 * Si user-service ne répond pas, nom vaut « Membre n° X ».
 */
public record AuteurResponse(Long id, String nom, String role, NiveauReputation niveau, String niveauLibelle,
                             Integer points, boolean anonyme, boolean formateurDuCours) {

    public static AuteurResponse masque() {
        return new AuteurResponse(null, null, null, null, null, null, true, false);
    }
}
