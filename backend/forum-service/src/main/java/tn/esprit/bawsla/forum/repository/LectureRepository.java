package tn.esprit.bawsla.forum.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    Optional<Lecture> findByUserIdAndDiscussionId(Long userId, Long discussionId);

    List<Lecture> findByUserIdAndDiscussionIdIn(Long userId, Collection<Long> discussionIds);
}
