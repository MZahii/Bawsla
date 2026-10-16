package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.ParametresForumCours;

public record ParametresCoursResponse(Long coursId, boolean ouvert, boolean anonymatAutorise, boolean vigieActive,
                                      boolean reponseGeneraleAutorisee, boolean modeExamen,
                                      LocalDateTime modeExamenFin, String questionModele,
                                      LocalDateTime dateModification) {

    public static ParametresCoursResponse from(ParametresForumCours p) {
        return new ParametresCoursResponse(p.getCoursId(), p.isOuvert(), p.isAnonymatAutorise(), p.isVigieActive(),
                p.isReponseGeneraleAutorisee(), p.isModeExamen(), p.getModeExamenFin(), p.getQuestionModele(),
                p.getDateModification());
    }
}
