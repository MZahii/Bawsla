package tn.esprit.bawsla.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Signalement;
import tn.esprit.bawsla.forum.entity.enums.StatutSignalement;

public interface SignalementRepository extends JpaRepository<Signalement, Long> {

    /** Signalements faits par un membre et confirmés par la modération (badge Gardien). */
    long countByAuteurIdAndStatut(Long auteurId, StatutSignalement statut);
}
