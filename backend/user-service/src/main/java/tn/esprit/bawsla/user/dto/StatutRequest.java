package tn.esprit.bawsla.user.dto;

import jakarta.validation.constraints.NotNull;

public record StatutRequest(@NotNull(message = "Le statut est obligatoire") Boolean actif) {
}
