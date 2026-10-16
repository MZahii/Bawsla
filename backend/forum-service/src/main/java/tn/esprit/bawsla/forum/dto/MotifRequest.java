package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.dto.validation.Motif;

/** Motif obligatoire d'une décision (masquer, rétablir, rejeter une réponse de La Vigie), tracé dans le journal. */
public record MotifRequest(@Motif String motif) {
}
