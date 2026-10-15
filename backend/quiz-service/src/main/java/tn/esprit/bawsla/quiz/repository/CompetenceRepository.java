package tn.esprit.bawsla.quiz.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.quiz.entity.Competence;

public interface CompetenceRepository extends JpaRepository<Competence, Long> {

    Optional<Competence> findByCoursIdAndLibelle(Long coursId, String libelle);
}
