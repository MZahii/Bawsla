package tn.esprit.bawsla.cours.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;

/** Représentation publique d'un cours (contrat GET /api/cours/{id}, voir CONTRATS_API.md). */
public record CoursResponse(Long id, String titre, String description, String categorie, Niveau niveau,
                            String fichierPdf, Long enseignantId, LocalDateTime dateCreation) {

    public static CoursResponse from(Cours c) {
        return new CoursResponse(c.getId(), c.getTitre(), c.getDescription(), c.getCategorie(),
                c.getNiveau(), c.getFichierPdf(), c.getEnseignantId(), c.getDateCreation());
    }
}
