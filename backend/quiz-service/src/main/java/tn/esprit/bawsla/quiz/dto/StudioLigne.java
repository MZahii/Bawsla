package tn.esprit.bawsla.quiz.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.quiz.entity.StatutQuiz;
import tn.esprit.bawsla.quiz.entity.TypeQuiz;

/** Un quiz du formateur avec ses chiffres : tentatives, moyenne et taux de réussite (null sans tentative). */
public record StudioLigne(Long id, String titre, Long coursId, TypeQuiz type, StatutQuiz statut, int nbQuestions,
                          long nbTentatives, Double moyenne, Double tauxReussite, LocalDateTime dateCreation) {
}
