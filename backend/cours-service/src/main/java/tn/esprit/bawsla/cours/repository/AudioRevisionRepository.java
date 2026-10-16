package tn.esprit.bawsla.cours.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.cours.entity.AudioRevision;

public interface AudioRevisionRepository extends JpaRepository<AudioRevision, Long> {

    Optional<AudioRevision> findFirstByFicheIdOrderByIdDesc(Long ficheId);

    List<AudioRevision> findByFicheId(Long ficheId);

    List<AudioRevision> findByCoursId(Long coursId);
}
