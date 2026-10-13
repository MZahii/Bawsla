package tn.esprit.bawsla.cours.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** Corps de PATCH /api/cours/{id}/chapitres/ordre : tous les identifiants de chapitres, dans le nouvel ordre. */
public record OrdreRequest(@NotNull(message = "La liste des chapitres est obligatoire") List<Long> ids) {
}
