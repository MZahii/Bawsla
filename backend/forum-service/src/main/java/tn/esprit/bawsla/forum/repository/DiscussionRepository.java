package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Discussion;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {

    List<Discussion> findByCoursIdOrderByDateCreationDesc(Long coursId);

    List<Discussion> findAllByOrderByDateCreationDesc();
}
