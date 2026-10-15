package tn.esprit.bawsla.forum.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.JournalModeration;

public interface JournalModerationRepository extends JpaRepository<JournalModeration, Long> {
}
