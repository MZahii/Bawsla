package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.enums.TypeNotification;

public record NotificationResponse(Long id, TypeNotification type, Long discussionId, Long reponseId,
                                   String discussionTitre, AuteurResponse acteur, String extrait, boolean lue,
                                   LocalDateTime creeLe) {
}
