package tn.esprit.bawsla.quiz.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.quiz.entity.Quiz;
import tn.esprit.bawsla.quiz.entity.StatutQuiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByCoursIdOrderByDateCreationDesc(Long coursId);

    List<Quiz> findAllByOrderByDateCreationDesc();

    List<Quiz> findByStatutOrderByDateCreationDesc(StatutQuiz statut);

    List<Quiz> findByCoursIdAndStatutOrderByDateCreationDesc(Long coursId, StatutQuiz statut);

    List<Quiz> findByEnseignantIdOrderByDateCreationDesc(Long enseignantId);
}
