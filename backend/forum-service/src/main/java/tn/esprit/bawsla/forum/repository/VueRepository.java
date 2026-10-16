package tn.esprit.bawsla.forum.repository;

import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Vue;

public interface VueRepository extends JpaRepository<Vue, Long> {

    boolean existsByUserIdAndDiscussionIdAndJour(Long userId, Long discussionId, LocalDate jour);
}
