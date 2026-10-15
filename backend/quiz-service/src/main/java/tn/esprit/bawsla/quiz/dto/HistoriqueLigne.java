package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.TypeQuiz;

/** Une tentative de l'historique de l'apprenant, comparée à la moyenne des autres apprenants du même quiz. */
public record HistoriqueLigne(Long tentativeId, Long quizId, String titre, Long coursId, TypeQuiz type,
                              int pourcentage, boolean reussie, LocalDateTime dateTentative, Double moyenneAutres) {
}
