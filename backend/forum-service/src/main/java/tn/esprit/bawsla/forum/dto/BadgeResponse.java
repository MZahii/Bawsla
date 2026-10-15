package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.enums.CodeBadge;

/** Badge obtenu ou à venir, avec la progression vers l'objectif. */
public record BadgeResponse(CodeBadge code, String libelle, String description, boolean obtenu,
                            LocalDateTime obtenuLe, int progression, int objectif) {
}
