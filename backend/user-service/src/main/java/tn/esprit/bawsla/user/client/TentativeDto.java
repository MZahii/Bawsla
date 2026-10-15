package tn.esprit.bawsla.user.client;

import java.time.LocalDateTime;

/** Contrat 6 : une tentative corrigée (pourcentage de 0 à 100). */
public record TentativeDto(Long quizId, Long coursId, int nbCorrectes, int nbQuestions, int pourcentage,
                           boolean reussie, LocalDateTime dateTentative) {
}
