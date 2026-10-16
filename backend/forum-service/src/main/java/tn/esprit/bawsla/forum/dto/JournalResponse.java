package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.JournalModeration;
import tn.esprit.bawsla.forum.entity.enums.ActionModeration;

/** Ligne du journal de modération ; acteurNom absent pour la modération automatique (role IA). */
public record JournalResponse(Long id, Long acteurId, String acteurNom, String role, ActionModeration action, String cibleType,
                              Long cibleId, Long coursId, String motif, LocalDateTime creeLe) {

    public static JournalResponse from(JournalModeration j, String acteurNom) {
        return new JournalResponse(j.getId(), j.getActeurId(), acteurNom, j.getRole(), j.getAction(), j.getCibleType(),
                j.getCibleId(), j.getCoursId(), j.getMotif(), j.getCreeLe());
    }
}
