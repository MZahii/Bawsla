package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;

public interface JournalModerationRepository extends JpaRepository<JournalModeration, Long> {

    /** Journal filtré (admin), le plus récent d'abord ; chaque filtre null est ignoré. */
    @Query("""
            select j from JournalModeration j where (:acteurId is null or j.acteurId = :acteurId)
              and (:action is null or j.action = :action)
              and (:du is null or j.creeLe >= :du) and (:au is null or j.creeLe < :au)
            order by j.creeLe desc, j.id desc""")
    Page<JournalModeration> rechercher(@Param("acteurId") Long acteurId, @Param("action") ActionModeration action,
                                       @Param("du") LocalDateTime du, @Param("au") LocalDateTime au,
                                       Pageable page);
}
