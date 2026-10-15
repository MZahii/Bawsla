package tn.esprit.bawsla.cours.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.FicheRevision;
import tn.esprit.bawsla.cours.entity.StatutFiche;

public interface FicheRevisionRepository extends JpaRepository<FicheRevision, Long> {

    /** La plus récente, quel que soit son statut (ce que voit le formateur). */
    Optional<FicheRevision> findFirstByCoursIdOrderByIdDesc(Long coursId);

    /** La plus récente validée (ce que voit l'apprenant). */
    Optional<FicheRevision> findFirstByCoursIdAndStatutOrderByIdDesc(Long coursId, StatutFiche statut);

    List<FicheRevision> findByCoursId(Long coursId);
}
