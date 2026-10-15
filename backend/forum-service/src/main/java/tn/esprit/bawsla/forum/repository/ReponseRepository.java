package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Reponse;

public interface ReponseRepository extends JpaRepository<Reponse, Long> {

    List<Reponse> findByDiscussionIdAndSupprimeeLeIsNull(Long discussionId);

    long countByAuteurIdAndCreeLeAfter(Long auteurId, LocalDateTime depuis);

    /** Discussions de la liste qui ont au moins une réponse approuvée par le formateur. */
    @Query("select distinct r.discussionId from Reponse r where r.discussionId in :ids "
            + "and r.approuveeParId is not null and r.supprimeeLe is null and r.masquee = false")
    List<Long> discussionsAvecApprobation(@Param("ids") Collection<Long> ids);

    @Query("select count(r) from Reponse r where r.discussionId = :discussionId and r.supprimeeLe is null "
            + "and r.masquee = false")
    int compterVisibles(@Param("discussionId") Long discussionId);
}
