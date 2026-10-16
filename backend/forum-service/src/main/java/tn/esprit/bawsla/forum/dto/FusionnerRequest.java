package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;
import tn.esprit.bawsla.forum.dto.validation.Motif;

/** Fusion motivée d'un doublon : la discussion devient un lien vers l'originale. */
public record FusionnerRequest(@NotNull(message = "La discussion d'origine est obligatoire") Long versDiscussionId,
                               @Motif String motif) {
}
