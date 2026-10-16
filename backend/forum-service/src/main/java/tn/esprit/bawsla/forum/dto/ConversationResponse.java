package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(Long id, Long coursId, String coursTitre, String titre, LocalDateTime creeLe,
                                   LocalDateTime derniereActiviteLe, List<MessageVigieResponse> messages) {
}
