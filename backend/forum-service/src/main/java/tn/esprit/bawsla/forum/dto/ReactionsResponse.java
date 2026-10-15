package tn.esprit.bawsla.forum.dto;

import java.util.Map;
import java.util.Set;

import tn.esprit.bawsla.forum.entity.enums.TypeReaction;

public record ReactionsResponse(Long reponseId, Map<TypeReaction, Long> compteurs, Set<TypeReaction> miennes) {
}
