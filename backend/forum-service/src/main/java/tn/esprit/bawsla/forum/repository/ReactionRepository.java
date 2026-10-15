package tn.esprit.bawsla.forum.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.Reaction;
import tn.esprit.bawsla.forum.entity.enums.TypeReaction;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByUserIdAndReponseIdAndType(Long userId, Long reponseId, TypeReaction type);

    List<Reaction> findByReponseIdIn(Collection<Long> reponseIds);

    List<Reaction> findByReponseId(Long reponseId);
}
