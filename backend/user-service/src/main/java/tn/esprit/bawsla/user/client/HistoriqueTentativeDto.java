package tn.esprit.bawsla.user.client;

import java.time.LocalDateTime;

/** Une ligne de GET /api/quiz/tentatives/mes (quiz-service) : ses propres tentatives, pourcentage de 0 à 100. */
public record HistoriqueTentativeDto(Long quizId, Long coursId, String type, int pourcentage, boolean reussie,
                                     LocalDateTime dateTentative) {
}
