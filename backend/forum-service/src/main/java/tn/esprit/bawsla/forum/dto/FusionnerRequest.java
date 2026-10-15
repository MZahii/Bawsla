package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Fusion d'un doublon : la discussion devient un lien vers l'originale. */
public record FusionnerRequest(@NotNull(message = "La discussion d'origine est obligatoire") Long versDiscussionId,
                               @Size(max = 500) String motif) {
}
