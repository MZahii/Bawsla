package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

public record CommentaireResponse(Long id, Long discussionId, Long reponseId, String contenu, AuteurResponse auteur,
                                  boolean masque, LocalDateTime creeLe, LocalDateTime modifieLe,
                                  boolean peutModifier) {
}
