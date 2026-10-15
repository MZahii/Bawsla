package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.EvenementReputation;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.RaisonReputation;

public interface EvenementReputationRepository extends JpaRepository<EvenementReputation, Long> {

    List<EvenementReputation> findByUserIdOrderByCreeLeDescIdDesc(Long userId, Pageable page);

    /** Solde des points déjà attribués à un membre pour une raison et une cible (sert à les annuler exactement). */
    @Query("select coalesce(sum(e.delta), 0) from EvenementReputation e where e.userId = :userId "
            + "and e.raison = :raison and e.cibleType = :cibleType and e.cibleId = :cibleId")
    int solde(@Param("userId") Long userId, @Param("raison") RaisonReputation raison,
              @Param("cibleType") CibleType cibleType, @Param("cibleId") Long cibleId);

    /** Points gagnés par membre dans un cours depuis une date : [userId, somme]. */
    @Query("select e.userId, sum(e.delta) from EvenementReputation e where e.coursId = :coursId "
            + "and e.creeLe >= :depuis group by e.userId having sum(e.delta) > 0 order by sum(e.delta) desc")
    List<Object[]> classement(@Param("coursId") Long coursId, @Param("depuis") LocalDateTime depuis,
                              Pageable page);
}
