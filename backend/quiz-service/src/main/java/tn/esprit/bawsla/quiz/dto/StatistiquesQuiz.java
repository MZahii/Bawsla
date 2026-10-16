package tn.esprit.bawsla.quiz.dto;

import java.util.List;

/**
 * Analyse d'un quiz pour son formateur. distribution : nombre de tentatives par tranche de 20 points
 * (0-20, 20-40, 40-60, 60-80, 80-100). questions : triées de la plus ratée à la moins ratée.
 */
public record StatistiquesQuiz(Long quizId, long nbTentatives, Double moyenne, Double tauxReussite,
                               List<Integer> distribution, List<QuestionStat> questions) {

    public record QuestionStat(Long questionId, int numero, String enonce, String competence, long nbReponses,
                               int tauxErreur) {
    }
}
