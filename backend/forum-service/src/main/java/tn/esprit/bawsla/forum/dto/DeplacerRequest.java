package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Min;
import tn.esprit.bawsla.forum.dto.validation.Motif;

/** Déplacement d'un fil vers un autre chapitre ou une autre page du même cours, motivé. */
public record DeplacerRequest(Long chapitreId, @Min(value = 1, message = "La page commence à 1") Integer page,
                              @Motif String motif) {
}
