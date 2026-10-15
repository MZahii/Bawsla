package tn.esprit.bawsla.forum.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Abonnement;

public interface AbonnementRepository extends JpaRepository<Abonnement, Long> {

    boolean existsByUserIdAndDiscussionId(Long userId, Long discussionId);

    Optional<Abonnement> findByUserIdAndDiscussionId(Long userId, Long discussionId);

    List<Abonnement> findByDiscussionId(Long discussionId);
}
