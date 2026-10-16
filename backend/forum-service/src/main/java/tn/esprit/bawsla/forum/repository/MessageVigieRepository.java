package tn.esprit.bawsla.forum.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tn.esprit.bawsla.forum.entity.MessageVigie;
import tn.esprit.bawsla.forum.entity.enums.RoleMessage;

public interface MessageVigieRepository extends JpaRepository<MessageVigie, Long> {

    List<MessageVigie> findByConversationIdOrderByCreeLeAscIdAsc(Long conversationId);

    /** Questions posées à l'assistant par un membre depuis une date (limite d'usage). */
    @Query("select count(m) from MessageVigie m, ConversationVigie c where m.conversationId = c.id "
            + "and c.userId = :userId and m.role = :role and m.creeLe >= :depuis")
    long compter(@Param("userId") Long userId, @Param("role") RoleMessage role,
                 @Param("depuis") LocalDateTime depuis);
}
