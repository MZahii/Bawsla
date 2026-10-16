package tn.esprit.bawsla.forum.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import tn.esprit.bawsla.forum.entity.ConversationVigie;

public interface ConversationVigieRepository extends JpaRepository<ConversationVigie, Long> {

    List<ConversationVigie> findByUserIdOrderByDerniereActiviteLeDesc(Long userId);
}
