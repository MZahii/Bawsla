package tn.esprit.bawsla.forum.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Favori;

public interface FavoriRepository extends JpaRepository<Favori, Long> {

    boolean existsByUserIdAndDiscussionId(Long userId, Long discussionId);

    Optional<Favori> findByUserIdAndDiscussionId(Long userId, Long discussionId);
}
