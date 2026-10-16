package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.ReponseIa;
import tn.esprit.bawsla.forum.entity.enums.NiveauReponseIa;
import tn.esprit.bawsla.forum.entity.enums.StatutReponseIa;

public interface ReponseIaRepository extends JpaRepository<ReponseIa, Long> {

    @Query("select distinct r.discussionId from ReponseIa r where r.discussionId in :ids and r.statut in :statuts")
    List<Long> discussionsAvecStatut(@Param("ids") Collection<Long> ids,
                                     @Param("statuts") Collection<StatutReponseIa> statuts);

    /** Réponses de La Vigie d'un cours (ou de la plateforme si coursId est null) depuis une date. */
    @Query("select r from ReponseIa r where r.creeLe >= :depuis and (:coursId is null or r.coursId = :coursId)")
    List<ReponseIa> creeesDepuis(@Param("coursId") Long coursId, @Param("depuis") LocalDateTime depuis);

    /** Réponse principale la plus récente d'un fil (niveau COURS ou FORUM, hors réponse générale). */
    @Query("select r from ReponseIa r where r.discussionId = :discussionId and r.niveau <> :niveauExclu "
            + "order by r.creeLe desc, r.id desc")
    List<ReponseIa> principales(@Param("discussionId") Long discussionId,
                                @Param("niveauExclu") NiveauReponseIa niveauExclu);

    List<ReponseIa> findByDiscussionIdAndNiveauOrderByCreeLeDescIdDesc(Long discussionId, NiveauReponseIa niveau);

    boolean existsByDiscussionId(Long discussionId);

    List<ReponseIa> findByCoursIdAndDiscussionIdIsNotNullOrderByCreeLeDescIdDesc(Long coursId);
}
