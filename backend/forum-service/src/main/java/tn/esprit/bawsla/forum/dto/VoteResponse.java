package tn.esprit.bawsla.forum.dto;

import tn.esprit.bawsla.forum.entity.enums.CibleType;

/** Nouveau score de la cible après le vote (mise à jour optimiste confirmée). */
public record VoteResponse(CibleType cibleType, Long cibleId, int score, int monVote) {
}
