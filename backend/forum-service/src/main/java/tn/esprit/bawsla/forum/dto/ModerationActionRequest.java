package tn.esprit.bawsla.forum.dto;

import jakarta.validation.constraints.Size;

/** Motif facultatif d'une action de modération (verrouillage), tracé dans le journal. */
public record ModerationActionRequest(@Size(max = 500, message = "Le motif fait au plus 500 caractères") String motif) {
}
