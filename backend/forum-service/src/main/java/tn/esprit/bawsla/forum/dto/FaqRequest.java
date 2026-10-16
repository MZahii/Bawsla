package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;

/** Transforme un fil résolu en entrée de FAQ épinglée du cours. */
public record FaqRequest(@NotNull(message = "La discussion est obligatoire") Long discussionId) {
}
