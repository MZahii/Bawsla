package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Size;

/** Réglages du forum d'un cours (formateur du cours ou admin). */
public record ParametresCoursRequest(boolean ouvert, boolean anonymatAutorise, boolean vigieActive,
                                     boolean reponseGeneraleAutorisee, boolean modeExamen,
                                     LocalDateTime modeExamenFin,
                                     @Size(max = 4000, message = "Le modèle fait au plus 4000 caractères")
                                     String questionModele) {
}
