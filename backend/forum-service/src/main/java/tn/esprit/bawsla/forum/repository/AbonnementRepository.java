package tn.esprit.bawsla.forum.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Abonnement;

public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    boolean existsByUserIdAndDiscussionId(Long userId, Long discussionId);

    Optional<Abonnement> findByUserIdAndDiscussionId(Long userId, Long discussionId);

    List<Abonnement> findByDiscussionId(Long discussionId);

    long countByDiscussionId(Long discussionId);

    @Query("select a.discussionId from Abonnement a where a.userId = :userId order by a.creeLe desc")
    List<Long> discussionsSuivies(@Param("userId") Long userId);
}
