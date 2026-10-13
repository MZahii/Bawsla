package tn.esprit.bawsla.user.dto;

import java.util.List;

import jakarta.validation.constraints.Size;
import tn.esprit.bawsla.user.entity.Parcours;

/** Questionnaire d'accueil de l'apprenant (lecture et écriture). Tous les champs sont facultatifs. */
public record ParcoursDto(
        @Size(max = 50) String profil,
        @Size(max = 150) String objectif,
        @Size(max = 20, message = "20 domaines maximum") List<@Size(max = 60) String> domaines,
        @Size(max = 50) String temps) {

    public static ParcoursDto from(Parcours p) {
        return p == null
                ? new ParcoursDto(null, null, List.of(), null)
                : new ParcoursDto(p.getProfil(), p.getObjectif(), p.getDomaines(), p.getTemps());
    }
}
