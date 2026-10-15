package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.NotNull;

public record AvisVigieRequest(@NotNull(message = "L'avis est obligatoire") Boolean utile) {
}
