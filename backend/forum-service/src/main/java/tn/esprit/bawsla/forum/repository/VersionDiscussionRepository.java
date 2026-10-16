package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.VersionDiscussion;

public interface VersionDiscussionRepository extends JpaRepository<VersionDiscussion, Long> {

    List<VersionDiscussion> findByDiscussionIdOrderByModifieLeDesc(Long discussionId);
}
