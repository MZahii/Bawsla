package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;

public record ConversationRequest(@NotNull(message = "Le cours est obligatoire") Long coursId) {
}
