package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.enums.RoleMessage;

public record MessageVigieResponse(Long id, RoleMessage role, String texte, VigieResponse reponse,
                                   LocalDateTime creeLe) {
}
