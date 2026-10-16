package tn.esprit.bawsla.forum.dto;

import java.time.LocalDateTime;

import tn.esprit.bawsla.forum.entity.EvenementReputation;
import tn.esprit.bawsla.forum.entity.enums.CibleType;
import tn.esprit.bawsla.forum.entity.enums.RaisonReputation;

public record EvenementReputationResponse(int delta, RaisonReputation raison, CibleType cibleType, Long cibleId,
                                          LocalDateTime creeLe) {

    public static EvenementReputationResponse from(EvenementReputation e) {
        return new EvenementReputationResponse(e.getDelta(), e.getRaison(), e.getCibleType(), e.getCibleId(),
                e.getCreeLe());
    }
}
