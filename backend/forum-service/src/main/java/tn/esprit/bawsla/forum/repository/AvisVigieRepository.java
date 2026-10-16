package tn.esprit.bawsla.forum.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.AvisVigie;

public interface AvisVigieRepository extends JpaRepository<AvisVigie, Long> {

    Optional<AvisVigie> findByUserIdAndReponseIaId(Long userId, Long reponseIaId);
}
