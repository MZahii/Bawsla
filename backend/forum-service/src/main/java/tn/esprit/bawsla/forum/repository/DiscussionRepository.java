package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import tn.esprit.bawsla.forum.entity.Discussion;
import tn.esprit.bawsla.forum.entity.enums.TypeDiscussion;

public interface DiscussionRepository extends JpaRepository<Discussion, Long>, JpaSpecificationExecutor<Discussion> {

    /** Anti-flood : discussions d'un type publiées par un auteur depuis une date. */
    long countByAuteurIdAndTypeAndCreeLeAfter(Long auteurId, TypeDiscussion type, LocalDateTime depuis);

    long countByAuteurIdAndCreeLeAfter(Long auteurId, LocalDateTime depuis);
}
