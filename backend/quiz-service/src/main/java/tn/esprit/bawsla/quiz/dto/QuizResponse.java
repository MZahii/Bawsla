package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.Quiz;

public record QuizResponse(Long id, String titre, String description, Long coursId, Long enseignantId,
                           LocalDateTime dateCreation) {

    public static QuizResponse from(Quiz q) {
        return new QuizResponse(q.getId(), q.getTitre(), q.getDescription(), q.getCoursId(),
                q.getEnseignantId(), q.getDateCreation());
    }
}
