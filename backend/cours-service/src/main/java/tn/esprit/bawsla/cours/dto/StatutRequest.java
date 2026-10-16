package tn.esprit.bawsla.cours.dto;

import jakarta.validation.constraints.NotNull;
import tn.esprit.bawsla.cours.entity.StatutCours;

/** Corps de PATCH /api/cours/{id}/statut. */
public record StatutRequest(@NotNull(message = "Le statut est obligatoire") StatutCours statut) {
}
