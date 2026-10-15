package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Déplacement d'un fil vers un autre chapitre ou une autre page du même cours. */
public record DeplacerRequest(Long chapitreId, @Min(1) Integer page,
                              @Size(max = 500) String motif) {
}
