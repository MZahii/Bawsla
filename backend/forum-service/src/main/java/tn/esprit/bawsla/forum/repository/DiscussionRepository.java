package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

public interface DiscussionRepository extends JpaRepository<Discussion, Long>, JpaSpecificationExecutor<Discussion> {

    /** Anti-flood : discussions d'un type publiées par un auteur depuis une date. */
    long countByAuteurIdAndTypeAndCreeLeAfter(Long auteurId, TypeDiscussion type, LocalDateTime depuis);

    long countByAuteurIdAndCreeLeAfter(Long auteurId, LocalDateTime depuis);

    long countByAuteurIdAndTypeAndSupprimeeLeIsNull(Long auteurId, TypeDiscussion type);

    long countByAuteurIdAndTypeAndAnonymeFalseAndSupprimeeLeIsNull(Long auteurId, TypeDiscussion type);

    /** Discussions d'un cours (ou de toute la plateforme si coursId est null) créées depuis une date. */
    @Query("select d from Discussion d where d.supprimeeLe is null and d.creeLe >= :depuis "
            + "and (:coursId is null or d.coursId = :coursId)")
    List<Discussion> creeesDepuis(@Param("coursId") Long coursId, @Param("depuis") LocalDateTime depuis);

    /** Questions visibles encore sans réponse (indicateur « sans réponse »). */
    @Query("select count(d) from Discussion d where d.supprimeeLe is null and d.masquee = false "
            + "and d.type = :type and d.nbReponses = 0 and d.verrouillee = false "
            + "and (:coursId is null or d.coursId = :coursId)")
    long sansReponse(@Param("coursId") Long coursId, @Param("type") TypeDiscussion type);

    @Query("select coalesce(max(d.score), 0) from Discussion d where d.auteurId = :auteurId and d.type = :type "
            + "and d.supprimeeLe is null")
    int meilleurScore(@Param("auteurId") Long auteurId, @Param("type") TypeDiscussion type);

    /** Étiquettes les plus utilisées par un membre dans ses discussions NON anonymes : [libellé, nombre]. */
    @Query("select e.libelle, count(d) from Discussion d join d.etiquettes e where d.auteurId = :auteurId "
            + "and d.anonyme = false and d.supprimeeLe is null group by e.libelle order by count(d) desc")
    List<Object[]> etiquettesFavorites(@Param("auteurId") Long auteurId, Pageable page);

    /** Discussions visibles parmi des ids, la plus active d'abord. */
    @Query("select d from Discussion d where d.id in :ids and d.supprimeeLe is null and d.masquee = false "
            + "order by d.derniereActiviteLe desc")
    List<Discussion> visibles(@Param("ids") Collection<Long> ids, Pageable page);

    @Query("select d from Discussion d where d.auteurId = :auteurId and d.supprimeeLe is null "
            + "order by d.creeLe desc")
    List<Discussion> deLAuteur(@Param("auteurId") Long auteurId, Pageable page);

    /** Discussions candidates d'une recherche : visibles, non fusionnées, dans un cours ou partout. */
    @Query("select d from Discussion d where d.supprimeeLe is null and d.masquee = false "
            + "and d.fusionneeDansId is null and (:coursId is null or d.coursId = :coursId)")
    List<Discussion> candidats(@Param("coursId") Long coursId);

    /** Discussions non lues d'un membre (« Tout marquer comme lu »), éventuellement dans un seul cours. */
    @Query("""
            select d.id from Discussion d where d.supprimeeLe is null and (:coursId is null or d.coursId = :coursId)
              and not exists (select l.id from Lecture l where l.discussionId = d.id and l.userId = :userId
                                and l.dernierLuLe >= d.derniereActiviteLe)""")
    List<Long> nonLues(@Param("userId") Long userId, @Param("coursId") Long coursId);
}
