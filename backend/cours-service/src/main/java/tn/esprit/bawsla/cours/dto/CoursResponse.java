package tn.esprit.bawsla.cours.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import tn.esprit.bawsla.cours.entity.Cours;
import tn.esprit.bawsla.cours.entity.Niveau;
import tn.esprit.bawsla.cours.entity.StatutCours;

/**
 * Représentation publique d'un cours (contrat GET /api/cours/{id}, voir CONTRATS_API.md).
 * Champs garantis stables : id, titre, categorie, niveau, enseignantId. Les autres sont des ajouts.
 * {@code categorie} est une matière de la liste commune avec le module Quiz ; {@code categoriePrecision} précise la matière « Autre ».
 * {@code fichierPdf} est le nom d'origine du PDF (affichage), jamais le chemin sur le disque.
 */
public record CoursResponse(Long id, String titre, String description, String categorie, Niveau niveau,
                            String fichierPdf, Long enseignantId, LocalDateTime dateCreation,
                            LocalDateTime dateModification, BigDecimal prix, StatutCours statut, String imageUrl,
                            int nbChapitres, int dureeTotaleMinutes, boolean aPdf, Long pdfTaille,
                            String categoriePrecision) {

    public static CoursResponse from(Cours c, int nbChapitres, int dureeTotaleMinutes) {
        String imageUrl = c.getImageFichier() == null ? null : "/api/cours/" + c.getId() + "/image?v=" + c.getImageFichier();
        return new CoursResponse(c.getId(), c.getTitre(), c.getDescription(), c.getCategorie(), c.getNiveau(),
                c.getFichierPdf(), c.getEnseignantId(), c.getDateCreation(), c.getDateModification(), c.getPrix(),
                c.getStatut(), imageUrl, nbChapitres, dureeTotaleMinutes, c.getPdfStockage() != null, c.getPdfTaille(),
                c.getCategoriePrecision());
    }
}
