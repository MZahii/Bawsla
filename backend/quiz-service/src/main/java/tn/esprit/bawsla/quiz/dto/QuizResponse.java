package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.entity.StatutQuiz;
import tn.esprit.bawsla.quiz.entity.TypeQuiz;

public record QuizResponse(Long id, String titre, String description, Long coursId, Long enseignantId,
                           TypeQuiz type, StatutQuiz statut, String theme, String matiere, String difficulte,
                           int seuilReussite, int tentativesMax, int dureeMinutes, int nbQuestions,
                           LocalDateTime dateCreation, LocalDateTime dateModification, LocalDateTime datePublication) {

    public static QuizResponse from(Quiz q) {
        return new QuizResponse(q.getId(), q.getTitre(), q.getDescription(), q.getCoursId(), q.getEnseignantId(),
                q.getType(), q.getStatut(), q.getTheme(), q.getMatiere(), q.getDifficulte(), q.getSeuilReussite(),
                q.getTentativesMax(), q.getDureeMinutes(), q.getQuestions().size(), q.getDateCreation(),
                q.getDateModification(), q.getDatePublication());
    }
}
