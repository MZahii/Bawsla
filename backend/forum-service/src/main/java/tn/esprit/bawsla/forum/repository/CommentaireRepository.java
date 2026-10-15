package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Commentaire;

public interface CommentaireRepository extends JpaRepository<Commentaire, Long> {

    List<Commentaire> findByDiscussionIdAndSupprimeLeIsNullOrderByCreeLeAsc(Long discussionId);

    long countByAuteurIdAndCreeLeAfter(Long auteurId, LocalDateTime depuis);
}
