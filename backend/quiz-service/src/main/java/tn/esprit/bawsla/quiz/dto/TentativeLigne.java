package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.Tentative;

/**
 * Contrat 6 (module User / ML) : une tentative corrigée d'un apprenant. {@code pourcentage} de 0 à 100 ; {@code reussie} suit la règle de
 * réussite du quiz. Aucune donnée personnelle : seulement les identifiants du quiz et du cours.
 */
public record TentativeLigne(Long quizId, Long coursId, int nbCorrectes, int nbQuestions, int pourcentage, boolean reussie,
                             LocalDateTime dateTentative) {

    public static TentativeLigne from(Tentative t) {
        return new TentativeLigne(t.getQuiz().getId(), t.getQuiz().getCoursId(), t.getNbCorrectes(), t.getNbQuestions(),
                t.getPourcentage(), t.isReussie(), t.getDateTentative());
    }
}
