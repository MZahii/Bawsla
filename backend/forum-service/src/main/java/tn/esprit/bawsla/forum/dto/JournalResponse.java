package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;

/** Ligne du journal de modération. */
public record JournalResponse(Long id, Long acteurId, String role, ActionModeration action, String cibleType,
                              Long cibleId, Long coursId, String motif, LocalDateTime creeLe) {

    public static JournalResponse from(JournalModeration j) {
        return new JournalResponse(j.getId(), j.getActeurId(), j.getRole(), j.getAction(), j.getCibleType(),
                j.getCibleId(), j.getCoursId(), j.getMotif(), j.getCreeLe());
    }
}
