package tn.esprit.bawsla.forum.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.Favori;

public interface FavoriRepository extends JpaRepository<Favori, Long> {

    boolean existsByUserIdAndDiscussionId(Long userId, Long discussionId);

    Optional<Favori> findByUserIdAndDiscussionId(Long userId, Long discussionId);

    @Query("select f.discussionId from Favori f where f.userId = :userId order by f.creeLe desc")
    List<Long> discussionsFavorites(@Param("userId") Long userId);
}
