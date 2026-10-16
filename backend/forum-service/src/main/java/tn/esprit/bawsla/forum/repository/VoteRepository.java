package tn.esprit.bawsla.forum.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Vote;
import tn.esprit.bawsla.forum.entity.enums.CibleType;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    Optional<Vote> findByUserIdAndCibleTypeAndCibleId(Long userId, CibleType cibleType, Long cibleId);

    /** Votes de l'utilisateur sur plusieurs cibles (« monVote » d'un fil en une requête). */
    List<Vote> findByUserIdAndCibleTypeAndCibleIdIn(Long userId, CibleType cibleType, Collection<Long> cibleIds);
}
