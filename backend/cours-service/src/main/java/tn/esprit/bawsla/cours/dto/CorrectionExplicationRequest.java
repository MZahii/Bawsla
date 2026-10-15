package tn.esprit.bawsla.cours.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de PUT /api/cours/{id}/explication : le formateur corrige le texte (et l'extrait source) des segments existants.
 * Il ne peut ni ajouter ni supprimer de segment : la structure imposée par l'IA et le minutage en dépendent.
 */
public record CorrectionExplicationRequest(
        @NotEmpty(message = "La liste des segments est obligatoire")
        @Size(max = 60, message = "60 segments maximum")
        List<@Valid SegmentCorrection> segments) {

    public record SegmentCorrection(
            @NotNull(message = "Le numéro du segment est obligatoire") Integer ordre,
            @NotBlank(message = "Le texte du segment est obligatoire") String texte,
            String extraitSource) {
    }
}
