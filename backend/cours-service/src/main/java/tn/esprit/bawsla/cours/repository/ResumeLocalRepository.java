package tn.esprit.bawsla.cours.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.ResumeLocal;
import tn.esprit.bawsla.cours.entity.StatutResumeLocal;

public interface ResumeLocalRepository extends JpaRepository<ResumeLocal, Long> {

    /** La plus récente, quel que soit son statut (ce que voit le formateur). */
    Optional<ResumeLocal> findFirstByCoursIdOrderByIdDesc(Long coursId);

    /** La plus récente validée (ce que voit l'apprenant). */
    Optional<ResumeLocal> findFirstByCoursIdAndStatutOrderByIdDesc(Long coursId, StatutResumeLocal statut);

    List<ResumeLocal> findByCoursId(Long coursId);
}
