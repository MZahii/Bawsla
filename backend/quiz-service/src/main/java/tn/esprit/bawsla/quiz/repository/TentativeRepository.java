package tn.esprit.bawsla.quiz.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.quiz.entity.Tentative;

public interface TentativeRepository extends JpaRepository<Tentative, Long> {

    List<Tentative> findByEtudiantIdOrderByDateTentativeDesc(Long etudiantId);

    List<Tentative> findByQuizId(Long quizId);

    long countByQuizId(Long quizId);

    long countByQuizIdAndEtudiantId(Long quizId, Long etudiantId);

    /** Moyenne des AUTRES apprenants sur ce quiz (comparaison anonyme, pas de classe). */
    @Query("select avg(t.pourcentage) from Tentative t where t.quiz.id = :quizId and t.etudiantId <> :etudiantId")
    Double moyenneDesAutres(@Param("quizId") Long quizId, @Param("etudiantId") Long etudiantId);
}
