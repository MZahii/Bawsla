package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;

public interface ReponseIaRepository extends JpaRepository<ReponseIa, Long> {

    @Query("select distinct r.discussionId from ReponseIa r where r.discussionId in :ids and r.statut in :statuts")
    List<Long> discussionsAvecStatut(@Param("ids") Collection<Long> ids,
                                     @Param("statuts") Collection<StatutReponseIa> statuts);

    /** Réponses de La Vigie d'un cours (ou de la plateforme si coursId est null) depuis une date. */
    @Query("select r from ReponseIa r where r.creeLe >= :depuis and (:coursId is null or r.coursId = :coursId)")
    List<ReponseIa> creeesDepuis(@Param("coursId") Long coursId, @Param("depuis") LocalDateTime depuis);
}
