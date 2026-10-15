package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

/** Apprenant dont les questions restent sans réponse (pseudonyme, jamais de donnée personnelle). */
public record ApprenantBloque(String pseudo, long questionsSansReponse, LocalDateTime derniereQuestionLe,
                              List<Long> discussionIds) {
}
